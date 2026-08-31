package com.huatian.weather.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huatian.weather.data.model.WeatherLocation
import com.huatian.weather.data.model.WeatherSnapshot
import com.huatian.weather.data.repository.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WeatherUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val snapshot: WeatherSnapshot? = null,
    val selectedLocation: WeatherLocation = WeatherRepository.defaultLocation,
    val error: String? = null
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WeatherRepository(application)
    private val _uiState = MutableStateFlow(
        WeatherUiState(
            snapshot = repository.cached(),
            selectedLocation = repository.cached()?.location ?: WeatherRepository.defaultLocation,
            isLoading = repository.cached() == null
        )
    )
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        load(_uiState.value.selectedLocation)
    }

    fun selectLocation(location: WeatherLocation) {
        _uiState.update { it.copy(selectedLocation = location, error = null) }
        load(location)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun showError(message: String) {
        _uiState.update { it.copy(error = message) }
    }

    private fun load(location: WeatherLocation) {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = it.snapshot == null,
                    isRefreshing = true,
                    error = null
                )
            }
            runCatching { repository.fetch(location) }
                .onSuccess { snapshot ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            snapshot = snapshot,
                            selectedLocation = location,
                            error = null
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = throwable.message ?: "天气数据加载失败"
                        )
                    }
                }
        }
    }
}
