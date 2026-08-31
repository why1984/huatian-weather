package com.huatian.weather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.huatian.weather.location.LocationProvider
import com.huatian.weather.ui.WeatherScreen
import com.huatian.weather.ui.WeatherViewModel
import com.huatian.weather.ui.theme.HuatianWeatherTheme

class MainActivity : ComponentActivity() {
    private val weatherViewModel: WeatherViewModel by viewModels()
    private lateinit var locationProvider: LocationProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        locationProvider = LocationProvider(applicationContext)

        setContent {
            HuatianWeatherTheme {
                WeatherScreen(
                    viewModel = weatherViewModel,
                    locationProvider = locationProvider
                )
            }
        }
    }
}
