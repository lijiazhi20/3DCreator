import json
import sqlite3
from contextlib import contextmanager
from typing import Optional

from app.config import settings

QUEUE_NAME = "gpu_jobs"
QUEUE_TABLE = "queue_items"


def _sqlite_path() -> Optional[str]:
    """Resolve the sqlite file used for the local queue."""
    if settings.queue_db:
        return settings.queue_db
    url = settings.database_url
    if url.startswith("sqlite"):
        # sqlite+aiosqlite:///path  or  sqlite:///path  -> take what's after the 3rd slash
        return url.split("///", 1)[-1]
    return None


@contextmanager
def _conn():
    path = _sqlite_path()
    if path is None:
        raise RuntimeError("Local queue requires a sqlite database_url or QUEUE_DB env var")
    c = sqlite3.connect(path, timeout=30)
    try:
        yield c
        c.commit()
    finally:
        c.close()


def ensure_queue_table() -> None:
    with _conn() as c:
        c.execute(
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


def _enqueue(job_id: str, job_type: str, tier: str, storage_key: str) -> None:
    ensure_queue_table()
    with _conn() as c:
        c.execute(
            f"INSERT INTO {QUEUE_TABLE} (job_id, job_type, tier, storage_key, status) "
            f"VALUES (?, ?, ?, ?, 'pending')",
            (job_id, job_type, tier, storage_key),
        )


def pop_pending() -> Optional[dict]:
    """Claim and return the next pending job (sync). Used by the worker."""
    ensure_queue_table()
    path = _sqlite_path()
    c = sqlite3.connect(path, timeout=30)
    try:
        row = c.execute(
            f"SELECT id, job_id, job_type, tier, storage_key FROM {QUEUE_TABLE} "
            f"WHERE status='pending' ORDER BY id ASC LIMIT 1"
        ).fetchone()
        if not row:
            return None
        c.execute(f"UPDATE {QUEUE_TABLE} SET status='claimed' WHERE id=?", (row[0],))
        c.commit()
        return {
            "id": row[0],
            "job_id": row[1],
            "job_type": row[2],
            "tier": row[3],
            "storage_key": row[4],
        }
    finally:
        c.close()


async def publish_job(job_id: str, job_type: str, tier: str, storage_key: str) -> None:
    """Enqueue a job. Uses a sqlite polling table in dev, Redis in production."""
    if settings.queue_backend == "local":
        _enqueue(job_id, job_type, tier, storage_key)
        return

    import redis.asyncio as redis  # lazy import: only needed for the redis backend

    r = redis.from_url(settings.redis_url, decode_responses=True)
    try:
        await r.lpush(
            QUEUE_NAME,
            json.dumps({
                "job_id": job_id,
                "job_type": job_type,
                "tier": tier,
                "storage_key": storage_key,
            }),
        )
    finally:
        await r.close()
