# Android App Design

## 1. Native vs Cross-Platform

**Recommendation: Kotlin + Jetpack Compose (native).**

| Factor | Native | Flutter | React Native |
|--------|--------|---------|--------------|
| Camera (burst, 4K, manual exposure) | Best | Limited | Limited |
| 3D viewer (GLB/OBJ/PLY/Splat) | Best (Filament) | Plugin bridge | Native module |
| Performance | Lean | Bridge overhead | Heaviest |
| Reuse | Android only | iOS+Web | iOS+Web |

Target is a single Honor device; camera + native 3D are exactly where cross-platform struggles. Go native.

## 2. Modules / Screens

1. **Camera Capture** — CameraX preview, auto burst, optional short clip.
2. **Gallery Picker** — Photo Picker API (no broad storage permission).
3. **Upload Queue** — resumable chunked upload, foreground service.
4. **Job Status** — list + detail, real-time WebSocket updates.
5. **3D Viewer** — Filament GLB/OBJ/PLY + Gaussian Splat preview.
6. **Export / Share** — download, export GLB, share via FileProvider.
7. **Settings** — account, quality tier, language switch, battery prefs.

## 3. 3D Rendering Engine

**Filament (Google)** via a thin Kotlin wrapper. Native GL ES 3.1 / Vulkan — ideal for Adreno 740.
- GLB / OBJ / PLY: first-class.
- Gaussian Splat: integrate a ported splat renderer (v1.1).
- Sceneform: deprecated — avoid.
- Three.js WebView: fallback only.

## 4. Architecture

- **Pattern**: MVVM + unidirectional Flow (MVI for Job/Viewer state machines).
- **Repository**: `CaptureRepository`, `UploadRepository`, `JobRepository`, `ModelRepository`.
- **Networking**: Retrofit + OkHttp + WebSocket, coroutines + Flow.
- **Persistence**: Room (queue/jobs/cache), DataStore (prefs).
- **DI**: Hilt. **Async**: Coroutines + Flow. **Uploads**: WorkManager + Foreground Service.

## 5. Internationalization (i18n) — Chinese / English Switch

The app must support runtime switching between Chinese (zh) and English (en).

- **String resources**:
  - `res/values/strings.xml` → English (default)
  - `res/values-zh/strings.xml` → 简体中文
  - Use `stringResource(R.string.key)` everywhere; never hardcode text.
- **Locale manager**:
  - `core/ui/LocaleManager.kt` stores chosen lang in DataStore.
  - `AppCompatActivity.attachBaseContext()` wraps context with `ContextWrapper` applying `Locale`.
  - On change, recreate activity / call `recreate()` so all strings reload.
- **Default**: follow system locale on first launch; user can override in Settings.
- **RTL**: not required (zh/en are LTR), but keep layouts direction-agnostic.

```kotlin
// core/ui/LocaleManager.kt (sketch)
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

## 6. Key APIs & Permissions

- CameraX, Photo Picker, WorkManager + Foreground Service, WebSocket, ExoPlayer, FileProvider, Biometric.
- Permissions: CAMERA, RECORD_AUDIO, INTERNET, POST_NOTIFICATIONS, FOREGROUND_SERVICE (dataSync), REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.
- Avoid `READ_EXTERNAL_STORAGE` (obsolete on 16).

## 7. Project Structure

```
android/
├── app/
├── core/
│   ├── common/
│   ├── network/
│   ├── data/
│   └── ui/                 # Theme, LocaleManager, components
├── feature/
│   ├── camera/  gallery/  upload/
│   ├── jobs/    viewer/   share/   settings/
└── graphics/
    └── filament/
```

## 8. MagicOS / Android 16 Constraints

- 16 KB page size: Filament `.so` must support it.
- `arm64-v8a` only.
- Edge-to-edge enforced; handle WindowInsets.
- Predictive back gesture.
- Scoped storage + Photo Picker.
- Foreground service type `dataSync`.
- `FLAG_SECURE` on viewer/capture.
