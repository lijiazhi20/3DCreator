"""
Shared progress-reporting helper for all pipelines.

Reads optional job metadata from `params` and POSTs a progress webhook to the
backend. Failures are swallowed so pipeline logic never crashes on a reporting
error. Mirrors the `report()` helper in worker/worker.py.
"""

from __future__ import annotations

import os

try:
    import requests
except ImportError:  # pragma: no cover - requests is a hard worker dep
    requests = None


def report(job_id: str, status: str, progress: int, params: dict | None = None) -> None:
    """Report progress to the backend webhook if API info is present in params."""
    if not params:
        return
    api_base = params.get("api_base") or os.getenv("API_BASE")
    secret = params.get("worker_secret") or os.getenv("WORKER_SECRET")
    if not api_base or requests is None:
        return
    try:
        requests.post(
            f"{api_base}/jobs/webhooks/worker",
            headers={"X-Worker-Secret": secret} if secret else {},
            json={
                "job_id": job_id,
                "status": status,
                "progress": progress,
            },
            timeout=10,
        )
    except Exception as exc:  # noqa: BLE001
        print(f"[{job_id}] progress report failed: {exc}")
