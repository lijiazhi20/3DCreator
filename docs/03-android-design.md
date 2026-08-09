# Android 设计

## 技术栈选择：Kotlin + Jetpack Compose（原生）

| 维度 | Native Kotlin | Flutter | React Native |
|------|---------------|---------|--------------|
| CameraX/Camera2 深度控制 | ✅ 最佳 | ⚠️ 需原生通道 | ⚠️ 需原生模块 |
| Filament 3D 渲染 | ✅ 直接集成 | ⚠️ 平台通道 | ⚠️ 平台通道 |
| 性能 / 包体积 | ✅ 最小 | 中等 | 最大 |
| 跨平台复用 | ❌ 仅 Android | ✅ iOS+Web | ✅ iOS+Web |

结论：目标设备是单台 Honor Android，硬需求（相机、3D）恰是跨平台弱势区，选择 **原生 Kotlin + Jetpack Compose**。

## 模块与页面

1. **CameraCaptureScreen** — CameraX 预览、拍照、短视频录制、拍摄引导框。
2. **GalleryPickerScreen** — Android Photo Picker，无需广泛存储权限。
3. **UploadQueueScreen** — 上传列表、进度、暂停/恢复、前台服务保活。
4. **JobListScreen / JobDetailScreen** — 任务状态（queued → reconstructing → ready → failed）、WebSocket 实时进度、缩略图、ETA。
5. **ViewerScreen** — Filament 渲染 GLB/OBJ/PLY，支持旋转/缩放/光照。
6. **ExportShareScreen** — 下载模型、系统分享（FileProvider）。
7. **SettingsScreen** — 账户、画质预设、通知、缓存清理。

## 架构模式

- **MVVM / MVI**：状态通过 `StateFlow` 驱动 Compose UI。
- **Repository 层**：`CaptureRepository`、`UploadRepository`、`JobRepository`、`ModelRepository`。
- **网络**：Retrofit + OkHttp + kotlinx.serialization；WebSocket 用于任务实时更新。
- **本地存储**：Room（任务/队列/缓存元数据）+ DataStore（偏好设置）。
- **后台上传**：WorkManager + Foreground Service。
- **依赖注入**：Hilt。

## 3D 渲染引擎

**Filament（Google）**
- OpenGL ES 3.1 / Vulkan，适配 Adreno 740。
- 原生支持 glTF/GLB；OBJ/PLY 通过 `filament-utils` 或自定义 loader。
- Gaussian Splat：MVP 不内置，v1.1 引入 splat renderer 或 WebView 降级。
- 避免 Sceneform（已废弃）。

## 关键权限

- `CAMERA`、`RECORD_AUDIO`
- `POST_NOTIFICATIONS`（Android 13+）
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_TYPE_DATA_SYNC`
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`（Honor 系统杀后台激进）
- 推荐用 Photo Picker 避免 `READ_MEDIA_*`

## MagicOS / Android 16 注意点

- **16 KB 内存页**：Filament 等 native `.so` 需支持 16 KB page size，否则 Android 16 安装失败。
- **仅 64-bit**：仅打包 `arm64-v8a`。
- **Edge-to-edge 强制**：正确处理 `WindowInsets`。
- **Predictive back**：使用系统返回 API。
- **Scoped storage**：只使用应用专属目录 + MediaStore。
- **电池优化**：前台服务 + 进度持久化到 Room，防止被 Honor 系统杀掉。

## 多语言（i18n）：中英文运行时切换

App 必须支持中文（zh）/ 英文（en）运行时切换。

- **字符串资源**：
  - `res/values/strings.xml` → 英文（默认）
  - `res/values-zh/strings.xml` → 简体中文
  - 所有 UI 文案使用 `stringResource(R.string.xxx)`，禁止硬编码。
- **Locale 管理**：
  - `core/ui/LocaleManager.kt` 用 DataStore 存语言偏好。
  - `AppCompatActivity.attachBaseContext()` 用 `ContextWrapper` 注入 `Locale`。
  - 切换后 `recreate()` 重建 Activity，所有文案即时刷新。
- **默认行为**：首次启动跟随系统语言，可在设置里覆盖。
- **布局方向**：中英文均 LTR，但保持布局方向无关。

```kotlin
// core/ui/LocaleManager.kt（草图）
object LocaleManager {
    fun setLocale(context: Context, lang: String) {
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val cfg = context.resources.configuration
        cfg.setLocale(locale)
        context.resources.updateConfiguration(cfg, context.resources.displayMetrics)
    }
}
```

## 项目结构

```
android/
├── app/                      # Application、Hilt、NavHost
├── core/
│   ├── common/              # 工具、扩展
│   ├── network/             # Retrofit/OkHttp/WebSocket、DTO
│   ├── data/                # Room、Repository、DataStore（含 Locale 偏好）
│   └── ui/                  # Compose 主题、LocaleManager、组件
├── feature/
│   ├── camera/
│   ├── gallery/
│   ├── upload/
│   ├── jobs/
│   ├── viewer/
│   ├── share/
│   └── settings/            # 含语言切换入口
└── graphics/
    └── filament/            # FilamentEngine、ModelLoader、SplatRenderer
```

## MVP 裁剪

首版只实现：CameraX 拍照 + Photo Picker → WorkManager 上传 → WebSocket 任务跟踪 → Filament GLB/OBJ/PLY 预览 → 导出分享。Gaussian Splat 预览、生物识别登录、iOS 版本延后。
