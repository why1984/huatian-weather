package com.huatian.weather.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Location
import android.location.Geocoder
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.huatian.weather.data.model.WeatherLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationProvider(private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    suspend fun currentLocation(): WeatherLocation = withContext(Dispatchers.IO) {
        val location = getDeviceLocation() ?: error("暂时无法获取当前位置")

        val address = reverseGeocode(location.latitude, location.longitude)
        addressToLocation(
            address = address,
            fallbackName = "当前位置",
            longitude = location.longitude,
            latitude = location.latitude
        )
    }

    suspend fun search(query: String): WeatherLocation = withContext(Dispatchers.IO) {
        val geocoder = Geocoder(context, Locale.CHINA)
        val addresses = geocoder.getFromLocationName(query, 1).orEmpty()
        val address = addresses.firstOrNull() ?: error("没有找到“$query”")
        addressToLocation(
            address = address,
            fallbackName = query,
            longitude = address.longitude,
            latitude = address.latitude
        )
    }

    private fun reverseGeocode(latitude: Double, longitude: Double): Address? {
        val geocoder = Geocoder(context, Locale.CHINA)
        return geocoder.getFromLocation(latitude, longitude, 1).orEmpty().firstOrNull()
    }

    @SuppressLint("MissingPermission")
    private suspend fun getDeviceLocation(): Location? = suspendCancellableCoroutine { continuation ->
        val cancellation = CancellationTokenSource()
        client.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            cancellation.token
        )
            .addOnSuccessListener { location ->
                if (continuation.isActive) continuation.resume(location)
            }
            .addOnFailureListener { throwable ->
                if (continuation.isActive) continuation.resumeWithException(throwable)
            }
            .addOnCanceledListener {
                if (continuation.isActive) continuation.resume(null)
            }

        continuation.invokeOnCancellation {
            cancellation.cancel()
        }
    }

    private fun addressToLocation(
        address: Address?,
        fallbackName: String,
        longitude: Double,
        latitude: Double
    ): WeatherLocation {
        val name = address?.locality
            ?: address?.subAdminArea
            ?: address?.adminArea
            ?: fallbackName
        val detail = listOfNotNull(
            address?.subLocality,
            address?.thoroughfare,
            address?.featureName
        ).distinct().joinToString("")
            .ifBlank { address?.getAddressLine(0).orEmpty().ifBlank { name } }

        return WeatherLocation(
            name = name,
            detail = detail,
            longitude = longitude,
            latitude = latitude
        )
    }

    companion object {
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
