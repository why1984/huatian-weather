package com.huatian.weather.data.repository

import android.content.Context
import com.google.gson.Gson
import com.huatian.weather.BuildConfig
import com.huatian.weather.data.model.CaiyunResponse
import com.huatian.weather.data.model.CurrentWeather
import com.huatian.weather.data.model.DailyWeather
import com.huatian.weather.data.model.HourlyWeather
import com.huatian.weather.data.model.WeatherAlert
import com.huatian.weather.data.model.WeatherLocation
import com.huatian.weather.data.model.WeatherSnapshot
import com.huatian.weather.data.network.CaiyunApi
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class WeatherRepository(context: Context) {
    private val api: CaiyunApi = Retrofit.Builder()
        .baseUrl("https://api.caiyunapp.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(CaiyunApi::class.java)

    private val gson = Gson()
    private val preferences = context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)

    suspend fun fetch(location: WeatherLocation): WeatherSnapshot {
        val response = api.getWeather(
            token = BuildConfig.CAIYUN_TOKEN,
            longitude = location.longitude,
            latitude = location.latitude
        )
        val result = response.result ?: error("天气服务没有返回数据")
        val snapshot = mapResponse(location, response, result)
        preferences.edit()
            .putString(KEY_SNAPSHOT, gson.toJson(snapshot))
            .apply()
        return snapshot
    }

    fun cached(): WeatherSnapshot? {
        return preferences.getString(KEY_SNAPSHOT, null)
            ?.let { runCatching { gson.fromJson(it, WeatherSnapshot::class.java) }.getOrNull() }
    }

    private fun mapResponse(
        location: WeatherLocation,
        response: CaiyunResponse,
        result: com.huatian.weather.data.model.CaiyunResult
    ): WeatherSnapshot {
        if (response.status != null && response.status != "ok") {
            error("天气服务返回状态：${response.status}")
        }

        val realtime = result.realtime
        val dailyTemperatures = result.daily?.temperature.orEmpty()
        val today = dailyTemperatures.firstOrNull()
        val skycon = realtime?.skycon.orEmpty()

        val current = CurrentWeather(
            temperature = realtime?.temperature?.roundToInt() ?: 0,
            apparentTemperature = realtime?.apparentTemperature?.roundToInt()
                ?: realtime?.temperature?.roundToInt()
                ?: 0,
            high = today?.max?.roundToInt() ?: realtime?.temperature?.roundToInt() ?: 0,
            low = today?.min?.roundToInt() ?: realtime?.temperature?.roundToInt() ?: 0,
            condition = weatherCondition(skycon),
            skycon = skycon
        )

        val hourlyTemperatures = result.hourly?.temperature.orEmpty()
        val hourlySkycons = result.hourly?.skycon.orEmpty()
        val hourlyPrecipitation = result.hourly?.precipitation.orEmpty()
        val hourly = hourlyTemperatures.mapIndexed { index, item ->
            val sky = hourlySkycons.getOrNull(index)?.value.orEmpty()
            val probability = hourlyPrecipitation.getOrNull(index)?.probability ?: 0.0
            HourlyWeather(
                time = formatHour(item.datetime),
                temperature = item.value?.roundToInt() ?: 0,
                precipitationProbability = formatProbability(probability),
                condition = weatherCondition(sky),
                skycon = sky
            )
        }.take(24)

        val dailySkycons = result.daily?.skycon.orEmpty()
        val daily = dailyTemperatures.mapIndexed { index, item ->
            val date = item.date.orEmpty().take(10)
            val sky = dailySkycons.getOrNull(index)?.value.orEmpty()
            DailyWeather(
                date = date,
                dateLabel = formatDate(date),
                temperatureHigh = item.max?.roundToInt() ?: 0,
                temperatureLow = item.min?.roundToInt() ?: 0,
                condition = weatherCondition(sky),
                skycon = sky
            )
        }.take(7)

        val alerts = result.alert?.content.orEmpty()
            .mapNotNull { alert ->
                val title = alert.title?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                WeatherAlert(title, alert.description.orEmpty())
            }

        return WeatherSnapshot(
            location = location,
            current = current,
            hourly = hourly,
            daily = daily,
            alerts = alerts,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun formatHour(value: String?): String {
        val instant = parseInstant(value) ?: return "--:--"
        return instant.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("H:mm", Locale.CHINA))
    }

    private fun formatDate(value: String): String {
        val date = runCatching { LocalDate.parse(value.take(10)) }.getOrNull() ?: return value
        val today = LocalDate.now()
        if (date == today) return "今天"
        val weekday = date.dayOfWeek.getDisplayName(
            java.time.format.TextStyle.SHORT,
            Locale.CHINA
        )
        return weekday
    }

    private fun parseInstant(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(value) }.getOrNull()
    }

    private fun formatProbability(value: Double): Int {
        val percent = if (value <= 1.0) value * 100 else value
        return percent.roundToInt().coerceIn(0, 100)
    }

    private fun weatherCondition(skycon: String): String {
        return when (skycon) {
            "CLEAR_DAY", "CLEAR_NIGHT" -> "晴"
            "PARTLY_CLOUDY_DAY", "PARTLY_CLOUDY_NIGHT" -> "多云"
            "CLOUDY" -> "阴"
            "LIGHT_RAIN" -> "小雨"
            "MODERATE_RAIN" -> "中雨"
            "HEAVY_RAIN", "STORM_RAIN" -> "大雨"
            "LIGHT_SNOW" -> "小雪"
            "MODERATE_SNOW" -> "中雪"
            "HEAVY_SNOW", "STORM_SNOW" -> "大雪"
            "HAIL" -> "冰雹"
            "SLEET" -> "雨夹雪"
            "FOG", "HAZE" -> "雾霾"
            "DUST" -> "浮尘"
            "SAND" -> "沙尘"
            "WIND" -> "大风"
            else -> "天气"
        }
    }

    companion object {
        private const val KEY_SNAPSHOT = "snapshot"
        val defaultLocation = WeatherLocation(
            name = "北京市",
            detail = "北京市",
            longitude = 116.4074,
            latitude = 39.9042
        )
    }
}
