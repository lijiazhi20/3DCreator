import uuid
from datetime import datetime
from typing import List
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import FileResponse, RedirectResponse
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.dependencies import get_db, get_current_user, verify_worker_secret
from app.models import Asset, Job, JobStatus, JobTier, JobType
from app.services.queue import publish_job
from app.services.storage import generate_download_url, local_path

router = APIRouter()


class CreateJobRequest(BaseModel):
    asset_id: str
    job_type: JobType
    tier: JobTier = JobTier.preview


class JobResponse(BaseModel):
    id: UUID
    asset_id: UUID
    job_type: str
    tier: str
    status: str
    progress: int
    result_key: str | None
    preview_key: str | None
    credits_charged: int
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True


class WorkerWebhook(BaseModel):
    job_id: str
    status: JobStatus | None = None
    progress: int = Field(0, ge=0, le=100)
    result_key: str | None = None
    preview_key: str | None = None
    error_message: str | None = None


def _credits_for_tier(tier: JobTier) -> int:
    return {JobTier.preview: 1, JobTier.standard: 5, JobTier.high: 20}.get(tier, 1)


@router.post("", response_model=JobResponse, status_code=201)
async def create_job(req: CreateJobRequest, db: AsyncSession = Depends(get_db), user=Depends(get_current_user)):
    asset = await db.get(Asset, uuid.UUID(req.asset_id))
    if not asset or str(asset.owner_id) != user["id"]:
        raise HTTPException(status_code=404, detail="Asset not found")

    job = Job(
        id=uuid.uuid4(),
        owner_id=uuid.UUID(user["id"]),
        asset_id=asset.id,
        job_type=req.job_type.value,
        tier=req.tier.value,
        status=JobStatus.queued.value,
        credits_charged=_credits_for_tier(req.tier),
    )
    db.add(job)
    await db.flush()

    await publish_job(str(job.id), req.job_type.value, req.tier.value, asset.storage_key)

    return job


@router.get("", response_model=List[JobResponse])
async def list_jobs(db: AsyncSession = Depends(get_db), user=Depends(get_current_user)):
    result = await db.execute(select(Job).where(Job.owner_id == uuid.UUID(user["id"])).order_by(Job.created_at.desc()))
    return result.scalars().all()


@router.get("/{job_id}", response_model=JobResponse)
async def get_job(job_id: str, db: AsyncSession = Depends(get_db), user=Depends(get_current_user)):
    job = await db.get(Job, uuid.UUID(job_id))
    if not job or str(job.owner_id) != user["id"]:
        raise HTTPException(status_code=404, detail="Job not found")
    return job


@router.post("/webhooks/worker")
async def worker_webhook(
    payload: WorkerWebhook,
    db: AsyncSession = Depends(get_db),
    secret=Depends(verify_worker_secret),
):
    job = await db.get(Job, uuid.UUID(payload.job_id))
    if not job:
        raise HTTPException(status_code=404, detail="Job not found")

    if payload.status:
        job.status = payload.status.value
    job.progress = payload.progress
    if payload.result_key:
        job.result_key = payload.result_key
    if payload.preview_key:
        job.preview_key = payload.preview_key
    if payload.error_message:
        job.error_message = payload.error_message
    job.updated_at = datetime.utcnow()

    return {"status": "updated"}


@router.get("/{job_id}/download")
async def download_result(
    job_id: str,
    db: AsyncSession = Depends(get_db),
    user=Depends(get_current_user),
):
    """
    Download the generated 3D result.
    - dev/local (STORAGE_BACKEND=local): serves the file directly.
    - prod (r2): redirects to a presigned download URL.
    """
    job = await db.get(Job, uuid.UUID(job_id))
    if not job or str(job.owner_id) != user["id"]:
        raise HTTPException(status_code=404, detail="Job not found")
    if not job.result_key:
        raise HTTPException(status_code=409, detail="Result not ready")

    if settings.storage_backend == "local":
        path = local_path(job.result_key)
        if not path.exists():
            raise HTTPException(status_code=404, detail="Result file missing")
        return FileResponse(str(path), media_type="model/gltf-binary", filename=path.name)

    return RedirectResponse(generate_download_url(job.result_key))
