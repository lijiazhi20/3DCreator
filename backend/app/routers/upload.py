import uuid
from datetime import datetime, timedelta

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from pydantic import BaseModel, Field

from app.config import settings
from app.dependencies import get_current_user, get_db
from app.models import Asset
from app.services.storage import get_storage_client, save_local

router = APIRouter()

# Accepted extensions for /upload/local (images for generative, archives for
# multi-image/video reconstruction). The pipeline is chosen later by job_type.
_ALLOWED_EXTS = {
    e.strip().lower()
    for e in settings.allowed_upload_exts.split(",")
    if e.strip()
}


def _validate_upload(filename: str, size: int) -> None:
    """Reject oversized or disallowed uploads (dev /upload/local path)."""
    ext = ("." + filename.rsplit(".", 1)[-1].lower()) if "." in filename else ""
    if ext not in _ALLOWED_EXTS:
        raise HTTPException(
            status_code=415,
            detail=f"Unsupported file type '{ext or filename}'. "
            f"Allowed: {sorted(_ALLOWED_EXTS)}",
        )
    if size > settings.max_upload_size:
        raise HTTPException(
            status_code=413,
            detail=f"File too large ({size} bytes > {settings.max_upload_size} max)",
        )


class PresignRequest(BaseModel):
    filename: str = Field(..., description="Original filename")
    content_type: str = Field(..., description="MIME type")
    size: int = Field(..., gt=0, le=2 * 1024 * 1024 * 1024, description="File size in bytes")


class PresignResponse(BaseModel):
    upload_url: str
    asset_id: str
    storage_key: str


@router.post("/presign", response_model=PresignResponse)
async def create_presigned_url(
    req: PresignRequest,
    user=Depends(get_current_user),
    db=Depends(get_db),
):
    """Production upload: returns an R2/S3 presigned PUT URL. 503 in dev (no R2).

    The Asset row is registered here (with the known filename/size/content_type
    and the R2 storage_key) so a subsequent POST /jobs {asset_id, ...} can find
    it. After the client PUTs bytes to `upload_url`, the worker downloads the
    object from R2 (STORAGE_BACKEND=r2) and proceeds to run the pipeline.
    """
    if not settings.r2_endpoint or not settings.r2_bucket:
        raise HTTPException(status_code=503, detail="Storage not configured (use /upload/local in dev)")

    # Validate extension/size before issuing a presigned URL.
    _validate_upload(req.filename, req.size)

    asset_id = str(uuid.uuid4())
    ext = req.filename.split(".")[-1].lower() if "." in req.filename else "bin"
    storage_key = f"uploads/{user['id']}/{asset_id}/{asset_id}.{ext}"

    # Register the asset now so /jobs can validate ownership immediately after
    # the client uploads to R2 (the worker does NOT need to create the row).
    asset = Asset(
        id=uuid.UUID(asset_id),
        owner_id=uuid.UUID(user["id"]),
        filename=req.filename,
        content_type=req.content_type or "application/octet-stream",
        size=req.size,
        storage_key=storage_key,
    )
    db.add(asset)
    await db.flush()

    s3 = get_storage_client()
    upload_url = s3.generate_presigned_url(
        "put_object",
        Params={
            "Bucket": settings.r2_bucket,
            "Key": storage_key,
            "ContentType": req.content_type,
        },
        ExpiresIn=900,
    )

    return PresignResponse(upload_url=upload_url, asset_id=asset_id, storage_key=storage_key)


class LocalUploadResponse(BaseModel):
    asset_id: str
    storage_key: str
    filename: str
    content_type: str
    size: int


@router.post("/local", response_model=LocalUploadResponse)
async def local_upload(
    file: UploadFile = File(...),
    user=Depends(get_current_user),
    db=Depends(get_db),
):
    """
    Dev/local upload: accepts a multipart file, stores it on the local filesystem
    under STORAGE_DIR, creates an Asset row, and returns its asset_id + storage_key.
    This is the no-infra equivalent of /upload/presign for local testing.
    """
    data = await file.read()
    if not data:
        raise HTTPException(status_code=400, detail="Empty file")

    filename = file.filename or "upload.bin"
    _validate_upload(filename, len(data))
    ext = filename.split(".")[-1].lower() if "." in filename else "bin"
    asset_id = str(uuid.uuid4())
    storage_key = f"uploads/{user['id']}/{asset_id}/{asset_id}.{ext}"

    save_local(storage_key, data)

    asset = Asset(
        id=uuid.UUID(asset_id),
        owner_id=uuid.UUID(user["id"]),
        filename=filename,
        content_type=file.content_type or "application/octet-stream",
        size=len(data),
        storage_key=storage_key,
    )
    db.add(asset)
    await db.flush()

    return LocalUploadResponse(
        asset_id=asset_id,
        storage_key=storage_key,
        filename=filename,
        content_type=asset.content_type,
        size=asset.size,
    )
