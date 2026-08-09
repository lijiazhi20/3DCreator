from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    app_name: str = "3DCreator API"
    debug: bool = False

    # --- Environment switch ---
    # ENV=dev  -> use SQLite + local storage + local (sqlite) queue, no external services.
    # ENV=prod -> use PostgreSQL / R2 / Redis (override the URLs below via env).
    env: str = "dev"

    # Database: defaults to a local SQLite file so the stack runs with zero infra.
    # Set DATABASE_URL to a postgresql+asyncpg://... URL for production.
    database_url: str = "sqlite+aiosqlite:///./dev.db"

    redis_url: str = "redis://localhost:6379/0"

    worker_secret: str = "dev-secret-change-in-prod"

    # --- API auth ---------------------------------------------------------
    # AUTH_TOKEN: static bearer token the API accepts (production).
    # DEV_AUTH_OFF: when true, the API accepts requests with NO bearer token
    #   (a demo owner is used). Default ON in dev so the local stack + E2E
    #   harness work with zero config. Set DEV_AUTH_OFF=false + AUTH_TOKEN in
    #   production to require `Authorization: Bearer <AUTH_TOKEN>`.
    auth_token: str = ""
    dev_auth_off: bool = True

    # --- Upload validation -------------------------------------------------
    # Max bytes accepted by /upload/local (dev). Matches the presign ceiling.
    max_upload_size: int = 2 * 1024 * 1024 * 1024
    # Accepted file extensions for /upload/local (images for generative, and
    # archives for multi-image/video reconstruction). The pipeline is chosen
    # later by job_type, so we accept both here.
    allowed_upload_exts: str = ".png,.jpg,.jpeg,.webp,.bmp,.gif,.tif,.tiff,.zip,.tar,.tgz"

    # --- Inference backend -------------------------------------------------
    # INFERENCE_BACKEND=dev  -> trimesh / pure-python GLB fallback (no GPU).
    # INFERENCE_BACKEND=gpu  -> real models (COLMAP+3DGS+SuGaR for multi,
    #                           TRELLIS.2/Stable Fast 3D for single).
    inference_backend: str = "dev"

    # --- Object storage backend ---
    # STORAGE_BACKEND=local -> store uploads/results on the local filesystem under STORAGE_DIR.
    # STORAGE_BACKEND=r2    -> use Cloudflare R2 / S3 (requires the r2_* settings below).
    storage_backend: str = "local"
    storage_dir: str = "storage"  # resolved relative to the backend cwd unless absolute

    r2_endpoint: str = ""
    r2_access_key: str = ""
    r2_secret_key: str = ""
    r2_bucket: str = "3dcreator-dev"

    # --- Task queue backend ---
    # QUEUE_BACKEND=local -> an in-process polling table in the SQLite dev.db (no Redis/RabbitMQ).
    # QUEUE_BACKEND=redis -> push to Redis list "gpu_jobs" (production).
    queue_backend: str = "local"
    # Explicit path to the sqlite file used for the local queue (defaults to DATABASE_URL's file).
    queue_db: str = ""

    supabase_url: str = ""
    supabase_jwt_secret: str = ""


settings = Settings()
