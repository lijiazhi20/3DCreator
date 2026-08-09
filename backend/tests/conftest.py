"""
Pytest configuration for the 3DCreator backend test-suite.

Sets the dev/local environment BEFORE the app is imported, points the SQLite
DB and the local queue at throwaway files, and exposes a FastAPI ``TestClient``
so tests need no running server / no port juggling.

The "GPU worker" is NOT run as a separate process. Instead each test drives the
real pipeline functions in-process (see ``backend/tests/test_api.py``) and reports
the result back through the real ``/jobs/webhooks/worker`` endpoint. This keeps
the tests fully deterministic and dependency-free while still exercising the
upload -> job -> pipeline -> download contract end to end.
"""
import os
import sys
import tempfile
from pathlib import Path

# --------------------------------------------------------------------------- #
# 1. Configure the dev environment BEFORE importing the app (settings are read
#    from the environment at import time).
# --------------------------------------------------------------------------- #
REPO_ROOT = Path(__file__).resolve().parent.parent.parent  # .../3DCreator
BACKEND_DIR = REPO_ROOT / "backend"
WORKER_DIR = REPO_ROOT / "worker"

_SESSION_TMP = tempfile.mkdtemp(prefix="tdcreator_pytest_")
os.environ["ENV"] = "dev"
os.environ["DATABASE_URL"] = f"sqlite+aiosqlite:///{_SESSION_TMP}/dev.db"
os.environ["STORAGE_BACKEND"] = "local"
os.environ["QUEUE_BACKEND"] = "local"
os.environ["WORKER_SECRET"] = "test-secret"
# NOTE: API_BASE is intentionally left unset so the pipeline's progress reporter
# (which POSTs to API_BASE) no-ops instead of trying to reach a server.

sys.path.insert(0, str(BACKEND_DIR))
sys.path.insert(0, str(WORKER_DIR))

import pytest  # noqa: E402

# --------------------------------------------------------------------------- #
# 2. Import the application + services (after env is configured).
# --------------------------------------------------------------------------- #
from fastapi.testclient import TestClient  # noqa: E402
from app.main import app  # noqa: E402
from app.config import settings  # noqa: E402
from app import services  # noqa: E402


@pytest.fixture(autouse=True)
def _isolate_storage_and_queue(tmp_path):
    """Give every test a fresh storage dir + queue DB so they never collide."""
    settings.storage_dir = str(tmp_path / "storage")
    settings.queue_db = str(tmp_path / "queue.db")
    services.queue.ensure_queue_table()
    import sqlite3

    conn = sqlite3.connect(settings.queue_db)
    try:
        conn.execute("DELETE FROM queue_items")
        conn.commit()
    finally:
        conn.close()
    yield


@pytest.fixture
def client():
    """FastAPI TestClient. The `with` block runs the app lifespan (creates tables)."""
    with TestClient(app) as c:
        yield c
