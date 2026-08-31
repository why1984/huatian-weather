# 花田天气

基于 Kotlin + Jetpack Compose + Material 3 的单页安卓天气应用。应用包名为 `com.huatian.weather`，数据源接入彩云天气综合预报接口。

## 已实现功能

- 当前天气卡片：精确地点、当前温度、天气状况、最高/最低温度、体感温度。
- 位置能力：支持动态申请定位权限，通过 Android 原生 LocationManager 获取当前位置经纬度；地址显示使用系统 Geocoder 尝试解析。
- 手动搜索：内置国内主要城市离线坐标；系统 Geocoder 可用时，也会尝试搜索更多地点。
- 天气预警：有预警时展示醒目的 Material 3 横幅，无预警时自动隐藏。
- 天气数据：彩云天气。
- 小时预报：接口拉取未来 72 小时，首页横向展示未来 24 小时温度、天气图标和降水概率。
- 日预报：纵向展示未来 7 天预报；若 API 权限或服务端限制返回更短列表，界面会按实际返回天数展示。
- 状态处理：加载动画、刷新状态、Snackbar 错误提示、重试按钮。
- 缓存：使用 SharedPreferences 缓存最后一次成功查询结果。


## 下载

[点击下载花田天气 APK](https://github.com/why1984/huatian-weather/releases)
