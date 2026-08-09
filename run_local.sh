#!/usr/bin/env bash
# =============================================================================
# 3DCreator — one-command LOCAL stack launcher (no Docker, no GPU, no Redis/R2).
#
# Starts:
#   * FastAPI backend  (uvicorn, :8000)  — SQLite + local filesystem storage + sqlite queue
#   * Local worker     (worker.py)       — polls the sqlite queue, builds a real GLB with trimesh
#
# Uses the MANAGED python venv only (never global pip).
# =============================================================================
set -e

# ---- managed python / venv --------------------------------------------------
PY_VER=/Users/george/.workbuddy/binaries/python/versions/3.13.12/bin/python3
VENV=/Users/george/.workbuddy/binaries/python/envs/default
VENV_PY="$VENV/bin/python"

ROOT="$(cd "$(dirname "$0")" && pwd)"
BACKEND="$ROOT/backend"
WORKER="$ROOT/worker"
STORAGE_DIR="$BACKEND/storage"
DB_PATH="$BACKEND/dev.db"

# Create the venv if it doesn't exist yet.
if [ ! -x "$VENV_PY" ]; then
  echo "[run_local] creating managed venv at $VENV"
  "$PY_VER" -m venv "$VENV"
fi

# ---- dev environment --------------------------------------------------------
export ENV=dev
export DATABASE_URL="sqlite+aiosqlite:///$DB_PATH"
export STORAGE_BACKEND=local
export QUEUE_BACKEND=local
export STORAGE_DIR="$STORAGE_DIR"
# Local queue uses its OWN sqlite file so the synchronous queue writer never
# contends with the async DB session that holds the main (jobs) transaction open
# during a request. (Same-file writers would deadlock on SQLite's write lock.)
export QUEUE_DB="$BACKEND/queue.db"
export WORKER_SECRET=dev-secret-change-in-prod
export API_BASE=http://localhost:8000

# ---- install dependencies (idempotent, uses managed venv only) -------------
# Dev runs without Redis/R2/Postgres, so we install only what the local stack
# needs. All heavy/infra deps (redis, boto3, asyncpg, celery, sentry, ...) are
# imported lazily and remain available via backend/requirements.txt in Docker.
echo "[run_local] installing dev dependencies"
"$VENV_PY" -m pip install -q \
  fastapi "uvicorn[standard]" pydantic pydantic-settings \
  sqlalchemy aiosqlite python-multipart aiofiles httpx \
  trimesh requests

# ---- launch -----------------------------------------------------------------
echo "[run_local] starting backend on :8000"
cd "$BACKEND"
"$VENV_PY" -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --log-level info &
BACK_PID=$!

echo "[run_local] starting worker"
cd "$WORKER"
"$VENV_PY" "$WORKER/worker.py" &
WORKER_PID=$!

cleanup() {
  echo "[run_local] shutting down (backend=$BACK_PID worker=$WORKER_PID)"
  kill "$BACK_PID" "$WORKER_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "[run_local] stack is up. Press Ctrl-C to stop."
wait
