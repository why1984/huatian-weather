# 花田天气

基于 Kotlin + Jetpack Compose + Material 3 的单页安卓天气应用。应用包名为 `com.huatian.weather`，数据源接入彩云天气综合预报接口。

## 已实现功能

- 当前天气卡片：精确地点、当前温度、天气状况、最高/最低温度、体感温度。
- 位置能力：支持动态申请定位权限，通过 FusedLocationProviderClient 获取经纬度，并用 Geocoder 转换为地址。
- 手动搜索：输入城市、街道或地标后，通过 Geocoder 获取经纬度并刷新天气。
- 天气预警：有预警时展示醒目的 Material 3 横幅，无预警时自动隐藏。
- 天气数据：综合接口请求 `alert=true&dailysteps=7&hourlysteps=72`。
- 小时预报：接口拉取未来 72 小时，首页横向展示未来 24 小时温度、天气图标和降水概率。
- 日预报：纵向展示未来 7 天预报；若 API 权限或服务端限制返回更短列表，界面会按实际返回天数展示。
- 状态处理：加载动画、刷新状态、Snackbar 错误提示、重试按钮。
- 缓存：使用 SharedPreferences 缓存最后一次成功查询结果。

## 构建

当前机器已配置：

- JDK 17：`C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`
- Android SDK：`C:\Android\Sdk`
- Gradle：项目内临时工具目录 `.tools\gradle-8.10.2`

命令行构建：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:\Android\Sdk'
$env:ANDROID_SDK_ROOT='C:\Android\Sdk'
.\.tools\gradle-8.10.2\bin\gradle.bat assembleDebug
```

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 彩云 Token

彩云 Token 不提交到 GitHub。请在本地 `local.properties` 中配置：

```text
sdk.dir=C\:\\Android\\Sdk
caiyun.token=你的彩云天气Token
```

也可以通过 Gradle 属性或环境变量 `CAIYUN_TOKEN` 注入。`app/build.gradle.kts` 会按以下优先级读取：

本地 `local.properties` > Gradle 属性 `CAIYUN_TOKEN` > 环境变量 `CAIYUN_TOKEN`。

正式发布前建议通过后端下发或安全配置管理 Token。
