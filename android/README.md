# 3DCreator Android Client

A structurally-complete Android scaffold (Kotlin + Jetpack Compose) for the 3DCreator app:
photo/video capture, upload, job tracking, 3D preview, sharing, and **runtime
Chinese/English language switching (i18n)**.

> This is a SCAFFOLD. It is structurally correct and internally consistent, but it has NOT been
> compiled in this environment (no Android SDK here). Open it in Android Studio / sync Gradle to build.

## Tech stack

| Concern        | Choice |
|----------------|--------|
| Language       | Kotlin 1.9.24 |
| UI             | Jetpack Compose (Material 3), Navigation Compose |
| Architecture   | MVVM + Repository + Hilt (manual DI graph) |
| Camera         | CameraX 1.4.x (photo `ImageCapture` + video `VideoCapture`/`Recorder`) |
| 3D viewer      | **WebView hosting a Three.js GLB viewer** (`app/src/main/assets/glb_viewer.html`). A Filament-native viewer can replace this later; only the `modelUrl` contract matters. |
| Networking     | Retrofit 2.11 + OkHttp 4.12 + kotlinx.serialization |
| Local DB       | Room 2.6 (jobs + upload-queue cache) |
| Prefs          | DataStore (auth token, language, theme) |
| Background     | WorkManager 2.9 + Foreground Service (`dataSync`) |

Gradle/SDK: AGP 8.5.2, **minSdk 26**, **targetSdk / compileSdk 35**, JVM 17, **arm64-v8a only**.

## Project structure

```
android/
├── settings.gradle.kts, build.gradle.kts, gradle.properties, local.properties
├── gradle/libs.versions.toml        # version catalog (single source of truth)
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml       # CAMERA, INTERNET, READ_MEDIA_*, POST_NOTIFICATIONS,
        │                            # FOREGROUND_SERVICE(_DATA_SYNC), FileProvider, MainActivity
        ├── assets/glb_viewer.html    # Three.js GLB viewer used by the Viewer screen
        ├── java/com/tdcreator/
        │   ├── app/                  # App.kt (Hilt), MainActivity.kt (NavHost), di/, navigation/
        │   ├── core/
        │   │   ├── network/          # ApiService, RetrofitClient, dto/ (exact backend DTOs)
        │   │   ├── data/             # local/ (Room), prefs/ (DataStore), repository/
        │   │   ├── ui/               # theme/, components/
        │   │   └── i18n/             # LocaleManager (runtime locale switching)
        │   └── feature/
        │       ├── home/ camera/ gallery/ upload/ jobs/ viewer/ share/ settings/
        └── res/
            ├── values/strings.xml            # English (DEFAULT)
            ├── values-zh/strings.xml         # 简体中文
            ├── values/{colors,themes}.xml
            └── xml/{file_paths,backup_rules,data_extraction_rules}.xml
```

## Open / build / run in Android Studio

1. **SDK path.** Create/refresh `android/local.properties` with your SDK dir, or export
   `ANDROID_SDK_ROOT`. (`local.properties` is git-ignored and machine-specific.)
2. **Generate the wrapper jar** (if missing): run `gradle wrapper` once, or just let Android
   Studio regenerate `gradle/wrapper/gradle-wrapper.jar` on first sync.
3. **Open** the `android/` folder as an existing project. Gradle will sync using
   `libs.versions.toml`.
4. **Run** on the Honor device (MagicOS 10 / Android 16): enable USB debugging, select the
   device, Run ▸ `app`. A `release` build is minified + `arm64-v8a` only.

Target device constraints honored: 16 KB page size (64-bit only), scoped storage + Photo Picker
(no broad `READ_EXTERNAL_STORAGE` on 13+), foreground service type `dataSync`, edge-to-edge
(window insets handled by Compose).

## i18n — runtime Chinese / English switching

Mechanism (no app restart required for the locale itself, only an `Activity.recreate()`):

1. **Strings.** All user-visible text comes from `stringResource(R.string.xxx)`.
   - `res/values/strings.xml` → English (default)
   - `res/values-zh/strings.xml` → 简体中文
2. **Persistence.** The chosen language (`system` | `en` | `zh`) is stored in DataStore via
   `PreferencesRepository` (`core/data/prefs`).
3. **Apply.** `App.attachBaseContext()` and `MainActivity.attachBaseContext()` wrap the base
   `Context` with `LocaleManager.wrap(...)`, which forces the `Locale` (`zh`/`en`) or inherits
   the system locale when set to `system`. See `core/i18n/LocaleManager.kt`.
4. **Switch.** `SettingsScreen` offers **Follow system / English / 中文**. On selection the
   ViewModel persists the choice and calls `activity.recreate()` so every `@Composable` reloads
   its strings in the new locale.
5. Default is **Follow system**; user can override any time.

`R` references use the `com.tdcreator.app` namespace (the app `namespace`).

## Backend API contract (what this app expects)

Base URL is `BuildConfig.BASE_URL` (override per build type). The backend currently mounts its
routers at the **root** — there is **no `/api/v1` prefix** in `backend/app/main.py`. Endpoints:

### 1. Presigned upload — `POST /upload/presign`
Request (`PresignRequest`):
```json
{ "filename": "cat.jpg", "content_type": "image/jpeg", "size": 12345 }
```
Response (`PresignResponse`):
```json
{ "upload_url": "https://...presigned...", "asset_id": "uuid", "storage_key": "uploads/... " }
```
The client then **PUTs the raw file bytes** to `upload_url` (R2 presigned PUT, no auth header).

### 2. Create job — `POST /jobs`
Request (`CreateJobRequest`):
```json
{ "asset_id": "<from presign>", "job_type": "image_to_3d", "tier": "preview" }
```
`job_type` ∈ `single_image | multi_image | video` (legacy aliases `image_to_3d | video_to_3d` also accepted);
`tier` ∈ `preview | standard | high` (default `preview`).
- `single_image` → fast **generative** preview (one photo; back/bottom are hallucinated, NOT measured).
- `multi_image`  → **HIGH-PRECISION** reconstruction (20–50 photos zipped → COLMAP + 3DGS + SuGaR).
- `video`        → **HIGH-PRECISION** reconstruction (frames extracted, then same path as multi_image).
Response (`JobResponse`, 201):
```json
{
  "id": "uuid", "asset_id": "uuid", "job_type": "image_to_3d", "tier": "preview",
  "status": "queued", "progress": 0,
  "result_key": null, "preview_key": null, "credits_charged": 1,
  "created_at": "2026-...", "updated_at": "2026-..."
}
```

### 3. List jobs — `GET /jobs` → `List<JobResponse>`
### 4. Job detail — `GET /jobs/{id}` → `JobResponse`
Status values: `queued | running | succeeded | failed | cancelled` (poll `GET /jobs/{id}`).

### 5. Download result — `GET /jobs/{id}/download` → raw GLB/OBJ file (binary)
**Implemented** in `backend/app/routers/jobs.py` (dev: serves the file via `FileResponse`;
prod: redirects to a presigned R2 URL). There is **no JSON envelope** — the client streams the
bytes directly.
- `ViewerViewModel` builds the URL `{BASE_URL}jobs/{id}/download` for the WebView GLB viewer.
- `ShareViewModel` calls `api.downloadResult(id): ResponseBody` and saves the bytes to cache.

All DTOs live in `core/network/dto/` and match the FastAPI Pydantic models field-for-field
(`snake_case`). Auth: a Bearer JWT from DataStore is added by an OkHttp interceptor **only** to
requests whose host equals our API host (never to presigned R2 URLs).

## Upload flow

1. User captures (CameraX) or picks media (Photo Picker) and chooses a **reconstruction mode** on
   Home (Single photo / Multi-photo 360° / Video).
2. Each item is inserted into the Room `upload_queue` (`QUEUED`) and a `OneTimeWorkRequest`
   (`UploadWorker`) is enqueued in WorkManager.
3. `UploadWorker` (foreground, `dataSync`):
   - dev/local: `localUpload` → `POST /upload/local` (multipart) — no R2 needed;
   - prod: `presignUpload` → PUT bytes to the presigned R2 URL;
   - for `multi_image`, the photos were already zipped into one file by
     `bundleAndEnqueueMulti()` before the worker ran;
   - `jobRepo.createJob(assetId, type)` → `POST /jobs` (type comes from the selected mode;
     video input forces `video`),
   - updates queue status to `DONE` (or `FAILED`, with up to 3 retries).
4. Jobs screen polls `GET /jobs/{id}`; when `succeeded`, Viewer/Share become available.
5. `GET /jobs/{id}/download` streams the GLB for the WebView viewer and sharing.

> **Multi-photo (high-precision) — implemented.** For `multi_image`, `GalleryScreen` bundles the
> selected photos into a single `.zip` (entries `frame_00001.jpg`, `frame_00002.jpg`, …; extension
> inferred from each photo's MIME type) and enqueues **one** `multi_image` job via
> `UploadRepository.bundleAndEnqueueMulti()`. The backend worker extracts the zip and runs
> COLMAP + 3DGS + SuGaR reconstruction. (Enqueuing each photo separately would create N
> `single_image` generative jobs — the low-precision path — which is exactly what this bundling
> avoids.) The mode is shared between Home and Gallery through the singleton `UploadRepository.mode`
> (the two screens get separate `UploadViewModel` instances from `hiltViewModel()`).

## Notes / TODO

- Filament is the design-doc-preferred native viewer; the scaffold ships a Three.js WebView
  viewer to avoid native `.so`/16 KB page-size complications. Swap `ViewerScreen` when ready.
- WebSocket real-time status (design doc) is not yet wired; the app polls instead.
- `UploadForegroundService` currently stays alive for the upload session; wire work-queue idle
  detection to `stopSelf()` when no active work remains.
- `local.properties` and `gradle-wrapper.jar` are machine/CI-generated and intentionally not
  committed.

---

## Cloud Build (GitHub Actions)

The Android client is also compiled **in the cloud** via GitHub Actions
(`.github/workflows/build.yml`), so you get a built `app-debug.apk` artifact on
every push/PR **without a local SDK**.

- **Trigger:** push to the default branch (or any PR touching `android/**`) →
  the **Actions** tab runs the workflow automatically.
- **Runner:** `ubuntu-latest` (GitHub cloud) with JDK 17 (Temurin), Android SDK
  (`platforms;android-35`, `build-tools;35.0.0`), and a cached `~/.gradle`.
- **Commands run:** `gradle lintDebug` (non-fatal), `gradle testDebugUnitTest`,
  `gradle assembleDebug`. The debug APK is uploaded as the `app-debug` artifact.
- **Release:** built and uploaded (`app-release`) **only if** the repo secrets
  `KEYSTORE_BASE64`, `KEY_ALIAS`, `KEY_PASSWORD`, `KEYSTORE_PASSWORD` are set.
  See `BUILD.md` §5 for setup.

> **Note:** `gradle-wrapper.jar` is intentionally not committed, so CI installs
> Gradle 8.9 directly and calls `gradle` (see `BUILD.md` for details). The 3D
> reconstruction itself runs on a separate GPU cloud (Modal/RunPod) — **not** in
> CI.
