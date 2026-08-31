package com.huatian.weather.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huatian.weather.data.model.DailyWeather
import com.huatian.weather.data.model.HourlyWeather
import com.huatian.weather.data.model.WeatherSnapshot
import com.huatian.weather.location.LocationProvider
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel,
    locationProvider: LocationProvider
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    suspend fun locate() {
        runCatching { locationProvider.currentLocation() }
            .onSuccess(viewModel::selectLocation)
            .onFailure { viewModel.showError(it.message ?: "定位失败，请稍后重试") }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            scope.launch { locate() }
        } else {
            viewModel.showError("定位权限未开启，仍可使用城市搜索")
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            state.isLoading && state.snapshot == null -> LoadingState(padding)
            state.snapshot != null -> WeatherContent(
                padding = padding,
                snapshot = state.snapshot,
                isRefreshing = state.isRefreshing,
                onSearch = { showSearchDialog = true },
                onLocate = {
                    if (LocationProvider.hasPermission(context)) {
                        scope.launch { locate() }
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                        )
                    }
                },
                onRefresh = viewModel::refresh
            )
            else -> ErrorState(
                padding = padding,
                onRetry = viewModel::refresh
            )
        }
    }

    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSearching) showSearchDialog = false },
            title = { Text("搜索城市或街道") },
            text = {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isSearching,
                    label = { Text("地点名称") },
                    placeholder = { Text("例如：北京市、杭州西湖") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Search
                    )
                )
            },
            confirmButton = {
                TextButton(
                    enabled = searchText.isNotBlank() && !isSearching,
                    onClick = {
                        isSearching = true
                        scope.launch {
                            runCatching { locationProvider.search(searchText.trim()) }
                                .onSuccess {
                                    viewModel.selectLocation(it)
                                    searchText = ""
                                    showSearchDialog = false
                                }
                                .onFailure {
                                    viewModel.showError(it.message ?: "搜索失败")
                                }
                            isSearching = false
                        }
                    }
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("搜索")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSearching,
                    onClick = { showSearchDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun WeatherContent(
    padding: PaddingValues,
    snapshot: WeatherSnapshot?,
    isRefreshing: Boolean,
    onSearch: () -> Unit,
    onLocate: () -> Unit,
    onRefresh: () -> Unit
) {
    val data = snapshot ?: return
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            CurrentWeatherPanel(
                snapshot = data,
                isRefreshing = isRefreshing,
                onSearch = onSearch,
                onLocate = onLocate,
                onRefresh = onRefresh
            )
        }
        if (data.alerts.isNotEmpty()) {
            item { AlertsPanel(data) }
        }
        item { HourlyPanel(data.hourly) }
        item { DailyPanel(data.daily) }
        item {
            Text(
                text = "更新于 ${formatUpdatedAt(data.updatedAt)}",
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun CurrentWeatherPanel(
    snapshot: WeatherSnapshot,
    isRefreshing: Boolean,
    onSearch: () -> Unit,
    onLocate: () -> Unit,
    onRefresh: () -> Unit
) {
    val current = snapshot.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = snapshot.location.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = snapshot.location.detail,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
                IconButton(onClick = onSearch) {
                    Icon(Icons.Outlined.Search, contentDescription = "搜索地点")
                }
                IconButton(onClick = onLocate) {
                    Icon(Icons.Outlined.MyLocation, contentDescription = "定位当前位置")
                }
                IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新天气")
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(
                text = "${current.temperature}°",
                fontSize = 68.sp,
                lineHeight = 72.sp,
                fontWeight = FontWeight.Light
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WeatherIcon(
                    skycon = current.skycon,
                    contentDescription = current.condition,
                    modifier = Modifier.size(42.dp)
                )
                Text(
                    text = current.condition,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricItem("最高", "${current.high}°")
                MetricDivider()
                MetricItem("最低", "${current.low}°")
                MetricDivider()
                MetricItem("体感", "${current.apparentTemperature}°")
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MetricDivider() {
    HorizontalDivider(
        modifier = Modifier
            .height(34.dp)
            .width(1.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun AlertsPanel(snapshot: WeatherSnapshot) {
    val alert = snapshot.alerts.first()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    alert.title,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
                if (alert.description.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        alert.description,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun HourlyPanel(items: List<HourlyWeather>) {
    ForecastSection(
        title = "24小时预报",
        icon = Icons.Outlined.AccessTime
    ) {
        if (items.isEmpty()) {
            EmptyForecast()
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                items.forEach { item -> HourlyItem(item) }
            }
        }
    }
}

@Composable
private fun HourlyItem(item: HourlyWeather) {
    Column(
        modifier = Modifier.width(58.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            item.time,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        WeatherIcon(
            skycon = item.skycon,
            contentDescription = item.condition,
            modifier = Modifier.size(30.dp)
        )
        Text("${item.temperature}°", fontWeight = FontWeight.Bold)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                Icons.Outlined.WaterDrop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(15.dp)
            )
            Text(
                "${item.precipitationProbability}%",
                color = MaterialTheme.colorScheme.tertiary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun DailyPanel(items: List<DailyWeather>) {
    val title = when {
        items.isEmpty() -> "未来预报"
        items.size >= 7 -> "7天预报"
        else -> "${items.size}天预报"
    }
    ForecastSection(
        title = title,
        icon = Icons.Outlined.CalendarMonth
    ) {
        if (items.isEmpty()) {
            EmptyForecast()
        } else {
            Column {
                items.forEachIndexed { index, item ->
                    DailyItem(item)
                    if (index != items.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyItem(item: DailyWeather) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(66.dp)) {
            Text(item.dateLabel, fontWeight = FontWeight.Bold)
            Text(
                item.date.takeLast(5).replace("-", "/"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        WeatherIcon(
            skycon = item.skycon,
            contentDescription = item.condition,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = item.condition,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${item.temperatureLow}°  ${item.temperatureHigh}°",
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ForecastSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}

@Composable
private fun WeatherIcon(
    skycon: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val image = when {
        skycon == "CLEAR_DAY" -> Icons.Outlined.WbSunny
        skycon == "CLEAR_NIGHT" -> Icons.Outlined.NightsStay
        skycon.contains("RAIN") -> Icons.Outlined.Umbrella
        skycon.contains("SNOW") || skycon == "SLEET" -> Icons.Outlined.AcUnit
        skycon == "WIND" -> Icons.Outlined.Air
        skycon == "THUNDER" -> Icons.Outlined.Thunderstorm
        skycon == "HAZE" || skycon == "FOG" -> Icons.Outlined.Cloud
        skycon == "CLOUDY" -> Icons.Outlined.Cloud
        else -> Icons.Outlined.CloudQueue
    }
    Icon(
        imageVector = image,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

@Composable
private fun EmptyForecast() {
    Text(
        "暂无预报数据",
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LoadingState(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(14.dp))
            Text("正在获取天气")
        }
    }
}

@Composable
private fun ErrorState(
    padding: PaddingValues,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.CloudQueue,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(52.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text("暂时没有天气数据")
            Spacer(Modifier.height(14.dp))
            Button(onClick = onRetry) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("重新加载")
            }
        }
    }
}

private fun formatUpdatedAt(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timestamp))
}
