from pathlib import Path

from app.config import settings


def storage_root() -> Path:
    """Absolute root directory for local dev storage (uploads/ + results/)."""
    return Path(settings.storage_dir).resolve()


def local_path(storage_key: str) -> Path:
    return storage_root() / storage_key


def save_local(storage_key: str, data: bytes) -> None:
    """Persist raw bytes under STORAGE_DIR/<storage_key> (dev/local backend)."""
    p = local_path(storage_key)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_bytes(data)


def get_storage_client():
    """Return a boto3 S3 client configured for R2/S3 (production backend)."""
    import boto3
    from botocore.config import Config

    return boto3.client(
        "s3",
        endpoint_url=settings.r2_endpoint,
        aws_access_key_id=settings.r2_access_key,
        aws_secret_access_key=settings.r2_secret_key,
        config=Config(signature_version="s3v4"),
    )


def generate_download_url(storage_key: str, expires_in: int = 3600) -> str:
    s3 = get_storage_client()
    return s3.generate_presigned_url(
        "get_object",
        Params={"Bucket": settings.r2_bucket, "Key": storage_key},
        ExpiresIn=expires_in,
    )


def put_to_r2(upload_url: str, data: bytes) -> None:
    """Upload raw bytes to a presigned PUT URL (R2/S3, production path).

    Used by the client (or a thin CLI) to push the asset to object storage
    after obtaining `upload_url` from POST /upload/presign. This is the analog
    of `save_local` for the prod storage backend. Uses only stdlib + requests
    so it runs without boto3.
    """
    import requests

    resp = requests.put(
        upload_url,
        data=data,
        headers={"Content-Type": "application/octet-stream"},
        timeout=300,
    )
    resp.raise_for_status()


def get_object_bytes(storage_key: str) -> bytes:
    """Fetch an object's bytes from R2/S3 by storage_key (used by the worker)."""
    s3 = get_storage_client()
    obj = s3.get_object(Bucket=settings.r2_bucket, Key=storage_key)
    return obj["Body"].read()
