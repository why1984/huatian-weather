package com.huatian.weather.data.model

import com.google.gson.annotations.SerializedName

data class WeatherLocation(
    val name: String,
    val detail: String,
    val longitude: Double,
    val latitude: Double
) {
    val displayName: String
        get() = if (detail.isBlank() || detail == name) name else "$name · $detail"
}

data class CurrentWeather(
    val temperature: Int,
    val apparentTemperature: Int,
    val high: Int,
    val low: Int,
    val condition: String,
    val skycon: String
)

data class HourlyWeather(
    val time: String,
    val temperature: Int,
    val precipitationProbability: Int,
    val condition: String,
    val skycon: String
)

data class DailyWeather(
    val date: String,
    val dateLabel: String,
    val temperatureHigh: Int,
    val temperatureLow: Int,
    val condition: String,
    val skycon: String
)

data class WeatherAlert(
    val title: String,
    val description: String
)

data class WeatherSnapshot(
    val location: WeatherLocation,
    val current: CurrentWeather,
    val hourly: List<HourlyWeather>,
    val daily: List<DailyWeather>,
    val alerts: List<WeatherAlert>,
    val updatedAt: Long
)

data class CaiyunResponse(
    val status: String? = null,
    val result: CaiyunResult? = null
)

data class CaiyunResult(
    val realtime: CaiyunRealtime? = null,
    val hourly: CaiyunHourly? = null,
    val daily: CaiyunDaily? = null,
    val alert: CaiyunAlert? = null
)

data class CaiyunRealtime(
    val temperature: Double? = null,
    @SerializedName("apparent_temperature")
    val apparentTemperature: Double? = null,
    val skycon: String? = null
)

data class CaiyunHourly(
    val temperature: List<CaiyunHourlyValue>? = null,
    val skycon: List<CaiyunHourlySkycon>? = null,
    val precipitation: List<CaiyunHourlyPrecipitation>? = null
)

data class CaiyunHourlyValue(
    val datetime: String? = null,
    val value: Double? = null
)

data class CaiyunHourlySkycon(
    val datetime: String? = null,
    val value: String? = null
)

data class CaiyunHourlyPrecipitation(
    val datetime: String? = null,
    val probability: Double? = null
)

data class CaiyunDaily(
    val temperature: List<CaiyunDailyTemperature>? = null,
    val skycon: List<CaiyunDailySkycon>? = null
)

data class CaiyunDailyTemperature(
    val date: String? = null,
    val max: Double? = null,
    val min: Double? = null
)

data class CaiyunDailySkycon(
    val date: String? = null,
    val value: String? = null
)

data class CaiyunAlert(
    val status: String? = null,
    val content: List<CaiyunAlertContent>? = null
)

data class CaiyunAlertContent(
    val title: String? = null,
    val description: String? = null
)
