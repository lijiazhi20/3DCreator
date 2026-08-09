# 3DCreator Backend — Deployment Guide

This document covers GPU deployment of the 3DCreator backend (FastAPI + worker)
for the two reconstruction paths that actually need a GPU:

- **multi_image** / **video** → high-precision reconstruction (COLMAP SfM + 3D
  Gaussian Splatting + SuGaR mesh extraction). GPU-heavy.
- **single_image** → generative preview (TRELLIS.2 / Stable Fast 3D).

In `dev` (no GPU) all paths fall back to a **valid, watertight GLB** built with
`trimesh` (or a pure-Python GLB writer), so the whole stack runs end-to-end
with zero infra.

---

## 1. Architecture recap

- **API** (`backend/`): FastAPI + `aiosqlite` `dev.db` (jobs/users/assets).
- **Worker** (`worker/`): polls the **separate** `queue.db`, runs a pipeline,
  writes a GLB, and reports progress back via `POST /jobs/webhooks/worker`
  (authenticated by `WORKER_SECRET`).
- **Storage**: `STORAGE_BACKEND=local` (dev) or `r2` (prod, Cloudflare R2/S3).
- **Queue**: `QUEUE_BACKEND=local` (`queue.db`) or `redis` (`gpu_jobs` list).

The worker and the API **may run in the same process in dev**, but in prod the
API is a stateless web service while the worker runs on a GPU machine.

---

## 2. Environment variables

| Variable | Default | Notes |
|---|---|---|
| `ENV` | `dev` | `dev` => auth OFF by default; `prod` => set auth. |
| `DATABASE_URL` | `sqlite+aiosqlite:///./dev.db` | Prod: `postgresql+asyncpg://…` |
| `STORAGE_BACKEND` | `local` | `r2` for production object storage. |
| `STORAGE_DIR` | `storage` | Local storage root (dev). |
| `QUEUE_BACKEND` | `local` | `redis` in production. |
| `QUEUE_DB` | (same file as `DATABASE_URL`) | **Keep a SEPARATE file** from `dev.db` in local mode (see §4). |
| `R2_ENDPOINT` / `R2_ACCESS_KEY` / `R2_SECRET_KEY` / `R2_BUCKET` | — | Required when `STORAGE_BACKEND=r2`. |
| `REDIS_URL` | `redis://localhost:6379/0` | Used when `QUEUE_BACKEND=redis`. |
| `WORKER_SECRET` | `dev-secret-change-in-prod` | Shared API↔worker webhook secret. |
| `AUTH_TOKEN` | (empty) | Static bearer token the API accepts in prod. |
| `DEV_AUTH_OFF` | `true` | Set `false` in prod to require `Authorization: Bearer <AUTH_TOKEN>`. |
| `INFERENCE_BACKEND` | `dev` | Set `gpu` on GPU workers to enable real models. |
| `API_BASE` | `http://localhost:8000` | Base URL the worker uses for webhooks. |

Auth: with `DEV_AUTH_OFF=true` (default in dev) or no `AUTH_TOKEN`, the API
returns a demo owner and requires no token. In prod set `DEV_AUTH_OFF=false` and
`AUTH_TOKEN=<secret>`; every request must send `Authorization: Bearer <secret>`.
TODO: replace the static token with Supabase JWT verification.

### R2 upload flow (prod)
1. Client `POST /upload/presign {filename, content_type, size}` →
   `{upload_url, asset_id, storage_key}`. The Asset row is registered immediately.
2. Client `PUT <upload_url>` raw bytes (via `services.storage.put_to_r2`).
3. Client `POST /jobs {asset_id, job_type}`.
4. Worker downloads the object from R2 (`STORAGE_BACKEND=r2` →
   `worker.fetch_source`), runs the pipeline, uploads the result GLB back to R2,
   and reports `result_key`.
5. `GET /jobs/{id}/download` redirects to a presigned R2 URL.

---

## 3. Modal deployment (PRIMARY)

Modal runs the GPU worker as a serverless function; the API can be a separate
Modal web endpoint or any ASGI host. Recommended split:

- **API**: Modal `Image` with `backend/requirements.txt`, `fastapi` ASGI app,
  `QUEUE_BACKEND=redis` (Modal Redis), `STORAGE_BACKEND=r2`.
- **Worker**: a Modal `function` with a GPU (`A100`/`L40S`) that polls Redis and
  runs the pipeline. Set `INFERENCE_BACKEND=gpu` and install the heavy deps
  (COLMAP/pycolmap, gsplat, SuGaR, TRELLIS.2, diffusers, torch) in the image.

Sketch (`modal_app.py`):

```python
import modal
app = modal.App("3dcreator-worker")
gpu_image = (
    modal.Image.debian_slim()
    .pip_install("fastapi", "boto3", "redis", "requests")
    .apt_install("colmap")          # SfM
    .pip_install("gsplat", "torch", "diffusers", "trimesh")
)
@app.function(image=gpu_image, gpu="A100", secrets=[modal.Secret.from_name("3dcreator-prod")])
def run_worker():
    import os
    os.environ["INFERENCE_BACKEND"] = "gpu"
    os.environ["QUEUE_BACKEND"] = "redis"
    os.environ["STORAGE_BACKEND"] = "r2"
    os.system("python /worker/worker.py")
```

The dev fallback guarantees the worker still returns a valid GLB if a model
fails to load, so the service degrades gracefully instead of erroring.

---

## 4. RunPod deployment (FALLBACK)

Use RunPod when you need a long-lived GPU container (persistent workers, custom
CUDA env, or when Modal concurrency limits are hit).

- Create a Pod with a CUDA image; install `backend/requirements.txt` + the
  heavy ML deps + `colmap` via apt.
- Run the API with `uvicorn app.main:app --host 0.0.0.0 --port 8000`.
- Run the worker in the same container (or a second pod) with
  `INFERENCE_BACKEND=gpu`, `QUEUE_BACKEND=redis`, `STORAGE_BACKEND=r2`,
  and `API_BASE` pointed at the API pod's URL.
- Expose the API port via RunPod's HTTP proxy; set `AUTH_TOKEN` + `DEV_AUTH_OFF=false`.

### queue.db handling
- **Local dev**: `QUEUE_DB` MUST be a **separate file** from `dev.db`. The
  worker writes synchronously to `queue.db` while the API holds an open
  `dev.db` transaction; sharing one SQLite file deadlocks on the write lock.
  `run_local.sh` already sets `QUEUE_DB=backend/queue.db` (separate from
  `dev.db`).
- **Production**: use `QUEUE_BACKEND=redis` — there is no `queue.db` and no
  SQLite write-lock concern. `queue.py` auto-switches based on `QUEUE_BACKEND`.

---

## 5. Local smoke test (no GPU)

```bash
bash run_local.sh            # starts API + worker (dev, trimesh fallback)
python /tmp/e2e_test.py     # hits /health, /upload/local, /jobs, /download
```

Both `single_image` and `multi_image` must produce a **valid GLB**
(magic bytes `glTF`).
