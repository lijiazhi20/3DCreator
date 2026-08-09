from typing import AsyncGenerator

from fastapi import Depends, HTTPException, Header
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine
from sqlalchemy.orm import sessionmaker

from app.config import settings

# SQLite (dev) needs check_same_thread=False; Postgres does not use connect_args.
if settings.database_url.startswith("sqlite"):
    engine = create_async_engine(
        settings.database_url,
        future=True,
        echo=settings.debug,
        connect_args={"check_same_thread": False},
    )
else:
    engine = create_async_engine(settings.database_url, future=True, echo=settings.debug)
async_session = sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)


async def get_db() -> AsyncGenerator[AsyncSession, None]:
    async with async_session() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
        finally:
            await session.close()


async def get_redis() -> AsyncGenerator["redis.Redis", None]:  # type: ignore[name-defined]
    import redis.asyncio as redis  # imported lazily so dev (sqlite) runs without redis installed

    r = redis.from_url(settings.redis_url, decode_responses=True)
    try:
        yield r
    finally:
        await r.close()


async def verify_worker_secret(x_worker_secret: str = Header(...)):
    if x_worker_secret != settings.worker_secret:
        raise HTTPException(status_code=401, detail="Invalid worker secret")
    return x_worker_secret


# ---------------------------------------------------------------------------
# Auth
# ---------------------------------------------------------------------------
# Demo owner used when auth is disabled (dev / DEV_AUTH_OFF). All dev uploads
# and jobs are scoped to this id, so the worker (which uses storage keys under
# this prefix) stays consistent with the API's owner_id scoping.
DEMO_OWNER = {
    "id": "00000000-0000-0000-0000-000000000000",
    "email": "demo@3dcreator.app",
}


def _auth_required() -> bool:
    """Auth is OFF when DEV_AUTH_OFF is set, or when no AUTH_TOKEN is configured."""
    if settings.dev_auth_off:
        return False
    if not settings.auth_token:
        return False
    return True


async def get_current_user(authorization: str = Header(None)) -> dict:
    """
    Bearer-token auth dependency.

    - DEV_AUTH_OFF=true (default in dev) OR no AUTH_TOKEN configured -> returns
      the demo owner (auth disabled). This keeps the local stack and the E2E
      harness working with zero config.
    - Otherwise requires `Authorization: Bearer <AUTH_TOKEN>`.

    TODO(prod): replace the static-token check with Supabase JWT verification
    (settings.supabase_jwt_secret) and derive the real `owner_id` from the
    verified claims so per-user asset/job scoping is enforced.
    """
    if not _auth_required():
        return DEMO_OWNER

    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(
            status_code=401,
            detail="Missing bearer token",
            headers={"WWW-Authenticate": "Bearer"},
        )
    token = authorization.split(" ", 1)[1].strip()
    if token != settings.auth_token:
        raise HTTPException(
            status_code=401,
            detail="Invalid token",
            headers={"WWW-Authenticate": "Bearer"},
        )
    # Static-token path: gate everything behind the demo owner. Swap for the
    # JWT-derived user id when Supabase auth is wired up.
    return DEMO_OWNER
