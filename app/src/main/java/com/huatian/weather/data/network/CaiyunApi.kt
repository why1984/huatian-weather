package com.huatian.weather.data.network

import com.huatian.weather.data.model.CaiyunResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CaiyunApi {
    @GET("v2.6/{token}/{longitude},{latitude}/weather")
    suspend fun getWeather(
        @Path("token") token: String,
        @Path("longitude") longitude: Double,
        @Path("latitude") latitude: Double,
        @Query("alert") alert: Boolean = true,
        @Query("dailysteps") dailySteps: Int = 7,
        @Query("hourlysteps") hourlySteps: Int = 72
    ): CaiyunResponse
}
