"""
Checked-in end-to-end test for the 3DCreator local stack.

Unlike ``test_api.py`` (which runs the pipeline in-process), this exercises the
REAL stack: a live FastAPI backend + the real ``worker.py`` process driven by
``run_local.sh``. It covers both reconstruction paths:

  * single_image  -> generative preview GLB
  * multi_image   -> zip of photos -> reconstruction GLB

Running modes
-------------
By default the test is SKIPPED unless a stack is already reachable at $API_BASE
(default http://localhost:8000). Set ``AUTO_START=1`` to have pytest launch
``run_local.sh`` itself (it installs dev deps, starts uvicorn + worker, and
tears them down on exit).
"""
import io
import os
import shutil
import subprocess
import time
import zipfile

import httpx
import pytest

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
BASE = os.getenv("API_BASE", "http://localhost:8000").rstrip("/")
AUTO_START = os.getenv("AUTO_START") == "1"

PNG_B64 = (
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
)
PNG = __import__("base64").b64decode(PNG_B64)


@pytest.fixture(scope="module")
def stack():
    """Yield a live stack, starting run_local.sh if AUTO_START=1; else skip."""
    proc = None

    # 1) Reuse a stack that is already up.
    try:
        if httpx.get(f"{BASE}/health", timeout=3).status_code == 200:
            yield {"base": BASE}
            return
    except Exception:
        pass

    # 2) Optionally auto-start run_local.sh.
    if not AUTO_START:
        pytest.skip(
            f"3DCreator stack not reachable at {BASE}. "
            "Start run_local.sh first, or set AUTO_START=1 to launch it from the test."
        )

    script = os.path.join(REPO_ROOT, "run_local.sh")
    if not os.path.exists(script):
        pytest.skip(f"run_local.sh not found at {script}")

    proc = subprocess.Popen(["bash", script], cwd=REPO_ROOT)
    deadline = time.time() + 180
    up = False
    while time.time() < deadline:
        try:
            if httpx.get(f"{BASE}/health", timeout=3).status_code == 200:
                up = True
                break
        except Exception:
            pass
        if proc.poll() is not None:
            pytest.skip("run_local.sh exited before the stack became healthy")
        time.sleep(1.0)

    if not up:
        if proc.poll() is None:
            proc.terminate()
        pytest.skip("Stack did not become healthy within timeout")

    try:
        yield {"base": BASE}
    finally:
        if proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=10)
            except Exception:
                proc.kill()


def _upload(stack, fn, data, ctype):
    r = httpx.post(
        f"{stack['base']}/upload/local",
        files={"file": (fn, data, ctype)},
        timeout=30,
    )
    assert r.status_code == 200, f"upload failed {r.status_code} {r.text}"
    return r.json()["asset_id"]


def _create_job(stack, asset_id, job_type, tier="standard"):
    r = httpx.post(
        f"{stack['base']}/jobs",
        json={"asset_id": asset_id, "job_type": job_type, "tier": tier},
        timeout=30,
    )
    assert r.status_code == 201, f"create_job failed {r.status_code} {r.text}"
    return r.json()["id"]


def _poll(stack, job_id, timeout=120):
    deadline = time.time() + timeout
    while time.time() < deadline:
        j = httpx.get(f"{stack['base']}/jobs/{job_id}", timeout=10).json()
        if j["status"] in ("succeeded", "failed"):
            return j
        time.sleep(1.0)
    raise AssertionError(f"job {job_id} did not finish within {timeout}s")


def _assert_glb(content: bytes):
    assert content[:4] == b"glTF", f"not a GLB (magic={content[:4]!r})"
    try:
        import trimesh

        loaded = trimesh.load(io.BytesIO(content), file_type="glb")
        geoms = loaded.geometry.values() if hasattr(loaded, "geometry") else [loaded]
        assert sum(len(g.vertices) for g in geoms if hasattr(g, "vertices")) > 0
    except ImportError:
        # trimesh optional in the E2E env; the magic header is the key assertion.
        pass


def test_single_image_e2e(stack):
    aid = _upload(stack, "photo.png", PNG, "image/png")
    jid = _create_job(stack, aid, "single_image", "standard")
    res = _poll(stack, jid)
    assert res["status"] == "succeeded", res
    dl = httpx.get(f"{stack['base']}/jobs/{jid}/download", timeout=30)
    assert dl.status_code == 200, dl.status_code
    _assert_glb(dl.content)


def test_multi_image_zip_e2e(stack):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        for i in range(3):
            z.writestr(f"view_{i}.png", PNG)
    buf.seek(0)
    aid = _upload(stack, "capture.zip", buf.getvalue(), "application/zip")
    jid = _create_job(stack, aid, "multi_image", "high")
    res = _poll(stack, jid)
    assert res["status"] == "succeeded", res
    dl = httpx.get(f"{stack['base']}/jobs/{jid}/download", timeout=30)
    assert dl.status_code == 200, dl.status_code
    _assert_glb(dl.content)
