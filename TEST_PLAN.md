# 3DCreator — Test Plan (QA / Test Team)

Repo: `3DCreator/`
Owner: QA / Test Team (test files only). Production source is untouched except for one
explicitly-reported test seam (see §5).

## 1. Scope & layout

| Area | Path | Type | Runner |
|------|------|------|--------|
| Backend contract tests | `backend/tests/test_api.py` | pytest (FastAPI `TestClient`) | `python -m pytest backend/tests` |
| Backend checked-in E2E | `backend/tests/test_e2e.py` | pytest + real stack (skippable) | `python -m pytest backend/tests` |
| Android repo unit tests | `android/app/src/test/.../UploadRepositoryTest.kt` | JUnit + Robolectric + Turbine + Mockito | `./gradlew :app:testDebugUnitTest` |
| Android VM unit tests | `android/app/src/test/.../feature/upload/UploadViewModelTest.kt` | same | same |
| Manual Android UI QA | §4 checklist | human | manual |

## 2. What is AUTOMATED

### 2.1 Backend (`test_api.py`, in-process, deterministic)
The "GPU worker" is driven **in-process**: each test pops the real SQLite queue row, runs the
real pipeline functions (`image_to_3d.run` / `multi_image_to_3d.run`) and reports the result
through the real `POST /jobs/webhooks/worker` endpoint. No separate process / GPU / port.

- (a) `POST /upload/local` with a small PNG → `200` + `asset_id`.
- (b) `POST /jobs` (`single_image`) → pipeline → `GET /jobs/{id}` (`succeeded`, `result_key`) →
  `GET /jobs/{id}/download` returns **RAW GLB** (`b'glTF'` magic) that **trimesh can load**.
- (c) `multi_image` with a **zip of 3 PNGs** → same path, exercising the real zip-extraction
  (`_collect_input_paths`) and the reconstruction pipeline → valid GLB.
- (d) Validation: `job_type="garbage"` → `422`; a non-existent `asset_id` → `404`; valid
  `single_image` → `201`.

### 2.2 Backend E2E (`test_e2e.py`, skippable)
Drives the **real** stack started by `run_local.sh` (uvicorn + `worker.py`) over HTTP and
exercises both `single_image` and `multi_image` (zip) paths end-to-end, asserting the download
is a valid GLB.

- If a stack is already healthy at `$API_BASE` (default `http://localhost:8000`) it is reused.
- If not, and `AUTO_START=1`, the test launches `run_local.sh` itself and tears it down on exit.
- Otherwise the suite is **skipped** (no failure) — safe in CI without the stack.

### 2.3 Android (`UploadRepositoryTest`, `UploadViewModelTest`)
- **Mode routing**: `SINGLE_IMAGE` enqueues a single job; `MULTI_IMAGE` goes through
  `bundleAndEnqueueMulti` (one bundled `multi_image` job, never N single jobs).
- **Zip bundling**: `bundleAndEnqueueMulti` writes a zip whose entries are exactly
  `frame_00001.jpg`, `frame_00002.jpg`, … (`UploadRepository.zipUris` naming contract).
- `bundleAndEnqueueMulti` rejects <2 photos (`require`).
- `UploadViewModel.setMode` updates the exposed `mode` `StateFlow` (Turbine-asserted);
  `enqueue`/`enqueueMulti` route to the correct repository path (asserted via a `FakeUploadDao`).

## 3. What is STUBBED / not automated

| Item | Status | Why |
|------|--------|-----|
| **GPU inference** (TRELLIS.2 / Stable-Fast-3D / COLMAP+3DGS+SuGaR) | Stubbed | No GPU; dev pipelines emit a valid placeholder GLB (trimesh primitive / dense sphere). Prod branches are guarded by `INFERENCE_BACKEND=gpu` and not executed here. |
| **R2 / S3 presigned upload** (`/upload/presign`) | Not covered | Dev backend returns `503`; only `/upload/local` is exercised. |
| **Redis queue backend** | Not covered | Tests use the local SQLite queue (`QUEUE_BACKEND=local`). |
| **WorkManager scheduling** | Faked | Unit tests subclass `UploadRepository` with a no-op `scheduleWorker` (see §5). |
| **Network layer** (`ApiService`, `UploadWorker` upload bytes) | Faked | Repository tests use a `FakeUploadDao`; no real HTTP. |
| **Android UI / Compose / WebView GLB viewer** | Manual only | See §4. |

**Known limitations**
- GLB correctness in tests is "magic bytes + trimesh loads + has vertices". It does **not**
  validate geometric fidelity (expected — the real reconstruction is GPU-only).
- E2E `test_e2e.py` only runs when a stack is reachable or `AUTO_START=1`; it is skipped by
  default so `python -m pytest backend/tests` is green out-of-the-box.
- Android unit tests require Robolectric (for `Uri`/`Context`); they are JVM `src/test`, not
  instrumentation tests.

## 4. Manual Android UI QA checklist

Run on a device/emulator (arm64). Use the dev backend (`run_local.sh`) so `/upload/local` works.

### 4.1 Capture 360° (multi_image / high-precision)
- [ ] Home → choose **Multi-photo (360°)** mode; camera captures ≥20 frames around the object.
- [ ] Frames are stored; the app shows a thumbnail strip / count.
- [ ] Initiating upload bundles the photos into ONE zip and enqueues a single `multi_image` job
      (verify in Jobs list: one job, type `multi_image`, not N `single_image` jobs).

### 4.2 Upload
- [ ] `UploadEntity` appears in the queue with `status=QUEUED` → `UPLOADING` → `CREATING_JOB`.
- [ ] For `single_image`, one photo → one `single_image` job.
- [ ] Failed uploads surface `FAILED` and are retryable; no silent drop.

### 4.3 View GLB
- [ ] After the job reaches `succeeded`, opening it loads the GLB in the WebView viewer
      (`jobs/{id}/download` streamed into `glb_viewer.html`).
- [ ] Model renders, rotates/pans; no blank canvas or console GLB-parse error.
- [ ] `preview_key` (if set) shows a thumbnail while the full model streams.

### 4.4 Share
- [ ] Share action exports / sends the GLB (or a shareable link) via the system share sheet.
- [ ] The shared file is a valid `.glb` (opens in another viewer).

### 4.5 Regression
- [ ] Switching mode (single ↔ multi ↔ video) updates `UploadViewModel.mode` and is reflected
      in the next enqueue.
- [ ] Background/foreground does not lose queue state (Room persistence).

## 5. Test seam added to production (reported)

`android/app/src/main/java/com/tdcreator/core/data/repository/UploadRepository.kt`
- Class changed `class UploadRepository` → `open class UploadRepository`.
- `private fun scheduleWorker(uid: Long)` → `protected open fun scheduleWorker(uid: Long)`.

Reason: unit tests subclass it with a no-op / recording scheduler so `WorkManager` is never
touched on the JVM. No behaviour change in production (the override is only used by tests).

## 6. How to run

```bash
# Backend (managed venv at /Users/george/.workbuddy/binaries/python/envs/default)
cd backend
python -m pytest backend/tests            # unit + contract; E2E auto-skips if stack down

# Backend E2E against a live stack:
#   (a) start it:  bash run_local.sh        # in another terminal
#   (b) then:      python -m pytest backend/tests
#   OR let the test start it:  AUTO_START=1 python -m pytest backend/tests

# Android (requires Android SDK / Gradle — not run in this environment):
cd android
./gradlew :app:testDebugUnitTest
```
