"""
Backend API contract tests (FastAPI TestClient, in-process pipeline).

Covers:
  (a) POST /upload/local returns an asset_id
  (b) POST /jobs (single_image) -> pipeline -> GET /jobs/{id} -> GET /download
      returns RAW GLB bytes (magic b'glTF') loadable by trimesh
  (c) multi_image with a zip of PNGs -> same, using the real extraction path
  (d) job_type validation rejects garbage (422) and unknown assets (404)

The dev GLB pipeline (trimesh) runs in-process; the result is reported back
through the real worker-webhook endpoint, so the full contract is exercised
without a separate worker process or GPU.
"""
import base64
import io
import pathlib
import shutil
import zipfile

import pytest
import trimesh

from app import services
from app.config import settings
from pipelines.image_to_3d import run as run_image_to_3d
from pipelines.multi_image_to_3d import run as run_multi_image_to_3d
from worker import _collect_input_paths

# 1x1 red PNG (valid image bytes; content is irrelevant for the dev pipeline).
PNG_B64 = (
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
)
PNG = base64.b64decode(PNG_B64)


def _process_job(client, job_id):
    """Pop the queued job, run the REAL pipeline in-process, report success.

    Mirrors worker/worker.py dispatch but runs synchronously in the test thread
    (no concurrency, no port) and reports via the real webhook endpoint.
    """
    task = services.queue.pop_pending()
    assert task is not None, "expected a queued job, found none"
    assert task["job_id"] == job_id, f"job id mismatch: {task['job_id']} != {job_id}"

    source = services.storage.local_path(task["storage_key"])
    job_type, tier = task["job_type"], task["tier"]

    results_dir = services.storage.storage_root() / "results" / job_id
    results_dir.mkdir(parents=True, exist_ok=True)
    params = {"job_id": job_id, "tier": tier, "output_dir": str(results_dir)}

    if job_type in ("single_image", "image_to_3d"):
        res = run_image_to_3d([str(source)], params, tier)
    elif job_type == "multi_image":
        inputs = _collect_input_paths(source, job_type)
        assert len(inputs) >= 2, f"multi_image needs >=2 images, got {len(inputs)}"
        res = run_multi_image_to_3d(inputs, params, tier)
    else:
        raise AssertionError(f"unexpected job_type {job_type!r}")

    result_path = pathlib.Path(res["output_path"])
    result_key = f"results/{job_id}/{result_path.name}"
    dest = services.storage.storage_root() / result_key
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest != result_path:
        shutil.copy(result_path, dest)

    r = client.post(
        "/jobs/webhooks/worker",
        headers={"X-Worker-Secret": settings.worker_secret},
        json={
            "job_id": job_id,
            "status": "succeeded",
            "progress": 100,
            "result_key": result_key,
        },
    )
    assert r.status_code == 200, r.text


def _assert_valid_glb(content: bytes):
    assert content[:4] == b"glTF", f"not a GLB (magic={content[:4]!r})"
    loaded = trimesh.load(io.BytesIO(content), file_type="glb")
    geoms = loaded.geometry.values() if hasattr(loaded, "geometry") else [loaded]
    total_verts = sum(len(g.vertices) for g in geoms if hasattr(g, "vertices"))
    assert total_verts > 0, "GLB loaded but contained no mesh vertices"


# --------------------------------------------------------------------------- #
# (a) upload/local
# --------------------------------------------------------------------------- #
def test_upload_local_returns_asset_id(client):
    r = client.post("/upload/local", files={"file": ("photo.png", PNG, "image/png")})
    assert r.status_code == 200, r.text
    body = r.json()
    assert "asset_id" in body and body["asset_id"]
    assert body["content_type"] == "image/png"
    assert body["size"] == len(PNG)


# --------------------------------------------------------------------------- #
# (b) single_image full path
# --------------------------------------------------------------------------- #
def test_single_image_pipeline_emits_valid_glb(client):
    aid = client.post(
        "/upload/local", files={"file": ("photo.png", PNG, "image/png")}
    ).json()["asset_id"]

    r = client.post(
        "/jobs",
        json={"asset_id": aid, "job_type": "single_image", "tier": "standard"},
    )
    assert r.status_code == 201, r.text
    job_id = r.json()["id"]

    _process_job(client, job_id)

    job = client.get(f"/jobs/{job_id}").json()
    assert job["status"] == "succeeded", job
    assert job["result_key"] == f"results/{job_id}/model.glb"

    dl = client.get(f"/jobs/{job_id}/download")
    assert dl.status_code == 200, dl.status_code
    _assert_valid_glb(dl.content)


# --------------------------------------------------------------------------- #
# (c) multi_image (zip of PNGs) full path
# --------------------------------------------------------------------------- #
def test_multi_image_zip_pipeline_emits_valid_glb(client):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        for i in range(3):
            z.writestr(f"view_{i}.png", PNG)
    buf.seek(0)

    aid = client.post(
        "/upload/local",
        files={"file": ("capture.zip", buf.getvalue(), "application/zip")},
    ).json()["asset_id"]

    r = client.post(
        "/jobs",
        json={"asset_id": aid, "job_type": "multi_image", "tier": "high"},
    )
    assert r.status_code == 201, r.text
    job_id = r.json()["id"]

    _process_job(client, job_id)

    job = client.get(f"/jobs/{job_id}").json()
    assert job["status"] == "succeeded", job
    dl = client.get(f"/jobs/{job_id}/download")
    assert dl.status_code == 200, dl.status_code
    _assert_valid_glb(dl.content)


# --------------------------------------------------------------------------- #
# (d) job_type / asset validation
# --------------------------------------------------------------------------- #
def test_job_type_validation(client):
    # garbage job_type -> 422 (pydantic enum validation)
    aid = client.post(
        "/upload/local", files={"file": ("photo.png", PNG, "image/png")}
    ).json()["asset_id"]
    r = client.post("/jobs", json={"asset_id": aid, "job_type": "garbage"})
    assert r.status_code == 422, r.text

    # valid job_type -> 201 (sanity for the positive path)
    r = client.post(
        "/jobs", json={"asset_id": aid, "job_type": "single_image"}
    )
    assert r.status_code == 201, r.text

    # unknown asset -> 404
    r = client.post(
        "/jobs",
        json={
            "asset_id": "00000000-0000-0000-0000-000000000000",
            "job_type": "single_image",
        },
    )
    assert r.status_code == 404, r.text
