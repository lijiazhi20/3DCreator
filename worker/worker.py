import os
import shutil
import sqlite3
import sys
import time
from pathlib import Path

import requests

# Allow `python worker.py` (run from the worker/ dir) to import the pipelines package.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# Pipeline dispatch. Each exposes run(input_paths, params, mode) -> dict.
# (See worker/pipelines/__init__.py DISPATCH for the canonical job_type table.)
from pipelines.image_to_3d import run as run_image_to_3d
from pipelines.single_image_to_3d import run as run_single_image_to_3d
from pipelines.multi_image_to_3d import run as run_multi_image_to_3d
from pipelines.video_to_3d import run as run_video_to_3d

# Image extensions we treat as reconstruction inputs after archive extraction.
_IMAGE_EXTS = (".jpg", ".jpeg", ".png", ".webp", ".bmp", ".tif", ".tiff")


def _is_safe_member(name: str) -> bool:
    """Reject zip-slip / path-traversal members (absolute or containing '..')."""
    if name.startswith("/") or name.startswith("\\"):
        return False
    if ".." in name.replace("\\", "/").split("/"):
        return False
    return True


def _safe_extract_zip(zf: "zipfile.ZipFile", dest: Path) -> None:
    for member in zf.namelist():
        if not _is_safe_member(member):
            # Reject archives that try to escape the extraction dir.
            raise RuntimeError(f"Unsafe zip entry rejected: {member!r}")
        target = (dest / member).resolve()
        if not str(target).startswith(str(dest.resolve())):
            raise RuntimeError(f"Unsafe zip entry rejected: {member!r}")
        info = zf.getinfo(member)
        if not info.is_dir():
            dest.mkdir(parents=True, exist_ok=True)
            target.parent.mkdir(parents=True, exist_ok=True)
            with zf.open(info) as src, open(target, "wb") as out:
                out.write(src.read())


def _safe_extract_tar(tf: "tarfile.TarFile", dest: Path) -> None:
    for member in tf.getmembers():
        if not _is_safe_member(member.name):
            raise RuntimeError(f"Unsafe tar entry rejected: {member.name!r}")
        target = (dest / member.name).resolve()
        if member.isfile() and not str(target).startswith(str(dest.resolve())):
            raise RuntimeError(f"Unsafe tar entry rejected: {member.name!r}")
    tf.extractall(dest)  # safe members only after the checks above


def _collect_input_paths(source: Path, job_type: str) -> list[str]:
    """
    Build the `input_paths` list passed to a pipeline's run().

    - single_image / video: a single file (image / video).
    - multi_image: the backend packs the 20-50 photos into an archive
      (.zip/.tar/.tgz). We extract it here and return the discovered images.
      Archive extraction is guarded against path-traversal (zip-slip) attacks.
    """
    name = source.name.lower()
    if job_type in ("multi_image",) and (
        name.endswith(".zip") or name.endswith((".tar", ".tgz", ".tar.gz"))
    ):
        import tarfile
        import zipfile

        extract_dir = source.parent / "extracted"
        extract_dir.mkdir(parents=True, exist_ok=True)
        if name.endswith(".zip"):
            with zipfile.ZipFile(source) as zf:
                _safe_extract_zip(zf, extract_dir)
        else:
            with tarfile.open(source) as tf:
                _safe_extract_tar(tf, extract_dir)
        images = sorted(
            str(p) for p in extract_dir.rglob("*")
            if p.suffix.lower() in _IMAGE_EXTS and p.is_file()
        )
        if not images:
            raise RuntimeError(f"No images found in archive {source}")
        return images
    return [str(source)]

API_BASE = os.getenv("API_BASE", "http://localhost:8000")
WORKER_SECRET = os.getenv("WORKER_SECRET", "dev-secret-change-in-prod")
STORAGE_DIR = os.getenv("STORAGE_DIR", "")
QUEUE_DB = os.getenv("QUEUE_DB", "")
POLL_INTERVAL = float(os.getenv("POLL_INTERVAL", "1"))

# Storage backend. STORAGE_BACKEND=local -> read source from the local fs.
# STORAGE_BACKEND=r2    -> the asset bytes live in R2; the worker downloads
# them to a temp file before running the pipeline. (Prod path.)
STORAGE_BACKEND = os.getenv("STORAGE_BACKEND", "local")
R2_ENDPOINT = os.getenv("R2_ENDPOINT", "")
R2_ACCESS_KEY = os.getenv("R2_ACCESS_KEY", "")
R2_SECRET_KEY = os.getenv("R2_SECRET_KEY", "")
R2_BUCKET = os.getenv("R2_BUCKET", "3dcreator-dev")

QUEUE_TABLE = "queue_items"


def storage_root() -> Path:
    if not STORAGE_DIR:
        raise RuntimeError("STORAGE_DIR env var is required in local mode")
    return Path(STORAGE_DIR).resolve()


def local_path(storage_key: str) -> Path:
    return storage_root() / storage_key


def fetch_source(storage_key: str) -> Path:
    """
    Resolve the job's input file as a local path.

    - local backend: return the on-disk path directly.
    - r2 backend: download the object from R2 to the results temp dir and
      return that path (the worker never reads directly from object storage).
    """
    if STORAGE_BACKEND != "r2":
        return local_path(storage_key)

    import boto3
    from botocore.config import Config

    s3 = boto3.client(
        "s3",
        endpoint_url=R2_ENDPOINT or None,
        aws_access_key_id=R2_ACCESS_KEY,
        aws_secret_access_key=R2_SECRET_KEY,
        config=Config(signature_version="s3v4"),
    )
    tmp_dir = storage_root() / "r2_tmp"
    tmp_dir.mkdir(parents=True, exist_ok=True)
    dest = tmp_dir / Path(storage_key).name
    print(f"[worker] downloading R2 object {storage_key} -> {dest}")
    s3.download_file(R2_BUCKET, storage_key, str(dest))
    return dest


def report(job_id, status, progress, result_key=None, preview_key=None, error=None):
    try:
        requests.post(
            f"{API_BASE}/jobs/webhooks/worker",
            headers={"X-Worker-Secret": WORKER_SECRET},
            json={
                "job_id": job_id,
                "status": status,
                "progress": progress,
                "result_key": result_key,
                "preview_key": preview_key,
                "error_message": error,
            },
            timeout=30,
        )
    except Exception as e:
        print(f"[{job_id}] report failed: {e}")


def _ensure_table() -> None:
    conn = sqlite3.connect(QUEUE_DB, timeout=30)
    try:
        conn.execute(
            f"""CREATE TABLE IF NOT EXISTS {QUEUE_TABLE} (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                job_id TEXT NOT NULL,
                job_type TEXT NOT NULL,
                tier TEXT NOT NULL,
                storage_key TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'pending',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )"""
        )
        conn.commit()
    finally:
        conn.close()


def pop_pending() -> dict | None:
    """Claim and return the next pending job from the sqlite queue."""
    if not QUEUE_DB:
        raise RuntimeError("QUEUE_DB env var is required in local mode")
    _ensure_table()
    conn = sqlite3.connect(QUEUE_DB, timeout=30)
    try:
        row = conn.execute(
            f"SELECT id, job_id, job_type, tier, storage_key FROM {QUEUE_TABLE} "
            f"WHERE status='pending' ORDER BY id ASC LIMIT 1"
        ).fetchone()
        if not row:
            return None
        conn.execute(f"UPDATE {QUEUE_TABLE} SET status='claimed' WHERE id=?", (row[0],))
        conn.commit()
        return {
            "id": row[0],
            "job_id": row[1],
            "job_type": row[2],
            "tier": row[3],
            "storage_key": row[4],
        }
    finally:
        conn.close()


def main():
    print(f"Worker started (local mode). storage={storage_root()} queue={QUEUE_DB}")
    while True:
        try:
            task = pop_pending()
        except Exception as e:
            print(f"queue error: {e}")
            time.sleep(POLL_INTERVAL)
            continue

        if not task:
            time.sleep(POLL_INTERVAL)
            continue

        job_id = task["job_id"]
        job_type = task["job_type"]
        tier = task["tier"]
        storage_key = task["storage_key"]

        report(job_id, "running", 0)
        print(f"[{job_id}] processing {job_type} tier={tier} src={storage_key}")

        try:
            source = fetch_source(storage_key)
            if not source.exists():
                raise FileNotFoundError(f"source file not found: {source}")

            results_dir = storage_root() / "results" / job_id
            results_dir.mkdir(parents=True, exist_ok=True)

            if job_type in ("image_to_3d", "single_image"):
                # Generative single-image path. `single_image` uses the
                # canonical module; `image_to_3d` is the legacy alias.
                run_fn = run_single_image_to_3d if job_type == "single_image" else run_image_to_3d
                params = {
                    "job_id": job_id, "tier": tier,
                    "output_dir": str(results_dir),
                    "api_base": API_BASE, "worker_secret": WORKER_SECRET,
                    "inference_backend": os.getenv("INFERENCE_BACKEND", "dev"),
                }
                res = run_fn([str(source)], params, tier)
                result_path = Path(res["output_path"])
            elif job_type in ("video_to_3d", "video"):
                params = {
                    "job_id": job_id, "tier": tier,
                    "output_dir": str(results_dir),
                    "api_base": API_BASE, "worker_secret": WORKER_SECRET,
                    "fps": 2,
                }
                res = run_video_to_3d([str(source)], params, tier)
                result_path = Path(res["output_path"])
            elif job_type == "multi_image":
                input_paths = _collect_input_paths(source, job_type)
                params = {
                    "job_id": job_id, "tier": tier,
                    "output_dir": str(results_dir),
                    "api_base": API_BASE, "worker_secret": WORKER_SECRET,
                    "images": len(input_paths),
                    "inference_backend": os.getenv("INFERENCE_BACKEND", "dev"),
                }
                res = run_multi_image_to_3d(input_paths, params, tier)
                result_path = Path(res["output_path"])
            else:
                raise ValueError(f"Unknown job_type: {job_type}")

            # Canonical result key = results/{job_id}/{filename}
            result_key = f"results/{job_id}/{result_path.name}"
            dest = storage_root() / result_key
            dest.parent.mkdir(parents=True, exist_ok=True)
            if dest != result_path:
                shutil.copy(result_path, dest)

            report(job_id, "succeeded", 100, result_key=result_key)
            print(f"[{job_id}] done -> {result_key}")

        except Exception as e:
            print(f"[{job_id}] error: {e}")
            report(job_id, "failed", 0, error=str(e))


if __name__ == "__main__":
    main()
