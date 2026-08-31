package com.huatian.weather.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.huatian.weather.data.model.WeatherLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

class LocationProvider(private val context: Context) {
    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    suspend fun currentLocation(): WeatherLocation = withContext(Dispatchers.IO) {
        check(hasPermission(appContext)) {
            "定位权限未开启，仍可使用城市搜索"
        }
        val location = getDeviceLocation()
            ?: error("暂时无法获取当前位置，请确认系统定位、网络或 GPS 已开启")
        val address = reverseGeocode(location.latitude, location.longitude)
        addressToWeatherLocation(
            address = address,
            fallbackName = "当前位置",
            fallbackDetail = "经纬度 ${location.latitude.formatCoordinate()}, ${location.longitude.formatCoordinate()}",
            longitude = location.longitude,
            latitude = location.latitude
        )
    }

    suspend fun search(query: String): WeatherLocation = withContext(Dispatchers.IO) {
        val normalized = query.normalizeLocationQuery()
        offlineCities[normalized]?.let { return@withContext it }

        val geocoded = geocodeBySystem(query)
        if (geocoded != null) return@withContext geocoded

        error("没有找到“$query”；当前离线搜索支持国内主要城市，也可点击定位按钮获取当前位置")
    }

    @SuppressLint("MissingPermission")
    private suspend fun getDeviceLocation(): Location? {
        val enabledProviders = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).filter { provider ->
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        }

        val lastKnown = enabledProviders
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }

        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < FRESH_LOCATION_MS) {
            return lastKnown
        }

        val activeProviders = enabledProviders.filter { it != LocationManager.PASSIVE_PROVIDER }
        if (activeProviders.isEmpty()) return lastKnown

        return withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (continuation.isActive) {
                            continuation.resume(location)
                            locationManager.removeUpdates(this)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }

                activeProviders.forEach { provider ->
                    runCatching {
                        locationManager.requestLocationUpdates(
                            provider,
                            0L,
                            0f,
                            listener,
                            Looper.getMainLooper()
                        )
                    }
                }

                continuation.invokeOnCancellation {
                    locationManager.removeUpdates(listener)
                }
            }
        } ?: lastKnown
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocode(latitude: Double, longitude: Double): Address? {
        if (!Geocoder.isPresent()) return null
        return runCatching {
            Geocoder(appContext, Locale.CHINA)
                .getFromLocation(latitude, longitude, 1)
                .orEmpty()
                .firstOrNull()
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun geocodeBySystem(query: String): WeatherLocation? {
        if (!Geocoder.isPresent()) return null
        return runCatching {
            Geocoder(appContext, Locale.CHINA)
                .getFromLocationName(query, 1)
                .orEmpty()
                .firstOrNull()
                ?.let { address ->
                    addressToWeatherLocation(
                        address = address,
                        fallbackName = query,
                        fallbackDetail = query,
                        longitude = address.longitude,
                        latitude = address.latitude
                    )
                }
        }.getOrNull()
    }

    private fun addressToWeatherLocation(
        address: Address?,
        fallbackName: String,
        fallbackDetail: String,
        longitude: Double,
        latitude: Double
    ): WeatherLocation {
        val name = address?.subLocality.orMeaningful()
            ?: address?.locality.orMeaningful()
            ?: address?.subAdminArea.orMeaningful()
            ?: address?.adminArea.orMeaningful()
            ?: fallbackName
        val detail = listOfNotNull(
            address?.locality.orMeaningful(),
            address?.subLocality.orMeaningful(),
            address?.thoroughfare.orMeaningful(),
            address?.featureName.orMeaningful()
        ).distinct().joinToString("")
            .ifBlank {
                address?.getAddressLine(0).orMeaningful() ?: fallbackDetail
            }

        return WeatherLocation(
            name = name,
            detail = detail,
            longitude = longitude,
            latitude = latitude
        )
    }

    private fun String?.orMeaningful(): String? {
        return this?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun Double.formatCoordinate(): String {
        return String.format(Locale.US, "%.5f", this)
    }

    companion object {
        private const val LOCATION_TIMEOUT_MS = 12_000L
        private const val FRESH_LOCATION_MS = 10 * 60 * 1000L

        private val offlineCities: Map<String, WeatherLocation> = listOf(
            city("北京", "北京市", 116.4074, 39.9042),
            city("上海", "上海市", 121.4737, 31.2304),
            city("天津", "天津市", 117.2000, 39.1333),
            city("重庆", "重庆市", 106.5516, 29.5630),
            city("广州", "广东省广州市", 113.2644, 23.1291),
            city("深圳", "广东省深圳市", 114.0579, 22.5431),
            city("杭州", "浙江省杭州市", 120.1551, 30.2741),
            city("南京", "江苏省南京市", 118.7969, 32.0603),
            city("苏州", "江苏省苏州市", 120.5853, 31.2989),
            city("成都", "四川省成都市", 104.0665, 30.5728),
            city("武汉", "湖北省武汉市", 114.3054, 30.5931),
            city("西安", "陕西省西安市", 108.9398, 34.3416),
            city("郑州", "河南省郑州市", 113.6254, 34.7466),
            city("长沙", "湖南省长沙市", 112.9388, 28.2282),
            city("合肥", "安徽省合肥市", 117.2272, 31.8206),
            city("济南", "山东省济南市", 117.1201, 36.6512),
            city("青岛", "山东省青岛市", 120.3826, 36.0671),
            city("福州", "福建省福州市", 119.2965, 26.0745),
            city("厦门", "福建省厦门市", 118.0894, 24.4798),
            city("南昌", "江西省南昌市", 115.8582, 28.6829),
            city("昆明", "云南省昆明市", 102.8329, 24.8801),
            city("贵阳", "贵州省贵阳市", 106.6302, 26.6470),
            city("南宁", "广西壮族自治区南宁市", 108.3669, 22.8170),
            city("海口", "海南省海口市", 110.1983, 20.0440),
            city("三亚", "海南省三亚市", 109.5119, 18.2528),
            city("太原", "山西省太原市", 112.5489, 37.8706),
            city("石家庄", "河北省石家庄市", 114.5149, 38.0428),
            city("呼和浩特", "内蒙古自治区呼和浩特市", 111.7492, 40.8426),
            city("沈阳", "辽宁省沈阳市", 123.4315, 41.8057),
            city("大连", "辽宁省大连市", 121.6147, 38.9140),
            city("长春", "吉林省长春市", 125.3235, 43.8171),
            city("哈尔滨", "黑龙江省哈尔滨市", 126.5349, 45.8038),
            city("兰州", "甘肃省兰州市", 103.8343, 36.0611),
            city("西宁", "青海省西宁市", 101.7782, 36.6171),
            city("银川", "宁夏回族自治区银川市", 106.2309, 38.4872),
            city("乌鲁木齐", "新疆维吾尔自治区乌鲁木齐市", 87.6168, 43.8256),
            city("拉萨", "西藏自治区拉萨市", 91.1409, 29.6456),
            city("宁波", "浙江省宁波市", 121.5503, 29.8739),
            city("温州", "浙江省温州市", 120.6994, 27.9943),
            city("无锡", "江苏省无锡市", 120.3124, 31.4900),
            city("常州", "江苏省常州市", 119.9741, 31.8112),
            city("佛山", "广东省佛山市", 113.1214, 23.0215),
            city("东莞", "广东省东莞市", 113.7518, 23.0207),
            city("珠海", "广东省珠海市", 113.5767, 22.2707),
            city("中山", "广东省中山市", 113.3928, 22.5176),
            city("泉州", "福建省泉州市", 118.6759, 24.8741),
            city("烟台", "山东省烟台市", 121.4479, 37.4638),
            city("潍坊", "山东省潍坊市", 119.1618, 36.7068),
            city("洛阳", "河南省洛阳市", 112.4540, 34.6197),
            city("南通", "江苏省南通市", 120.8943, 31.9802),
            city("徐州", "江苏省徐州市", 117.2841, 34.2058)
        ).flatMap { location ->
            listOf(
                location.name.normalizeLocationQuery() to location,
                location.detail.normalizeLocationQuery() to location
            )
        }.toMap()

        private fun city(name: String, detail: String, longitude: Double, latitude: Double): WeatherLocation {
            return WeatherLocation(
                name = name,
                detail = detail,
                longitude = longitude,
                latitude = latitude
            )
        }

        fun hasPermission(context: Context): Boolean {
            val fine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            return fine || coarse
        }
    }
}

private fun String.normalizeLocationQuery(): String {
    return trim()
        .replace(" ", "")
        .replace("　", "")
        .removeSuffix("市")
        .removeSuffix("地区")
        .removeSuffix("盟")
}
