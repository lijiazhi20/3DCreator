import uuid
from datetime import datetime
from enum import Enum

from sqlalchemy import Column, DateTime, ForeignKey, Integer, String, Text, Uuid
from sqlalchemy.ext.asyncio import AsyncAttrs
from sqlalchemy.orm import declarative_base, relationship

Base = declarative_base()


class JobStatus(str, Enum):
    queued = "queued"
    running = "running"
    succeeded = "succeeded"
    failed = "failed"
    cancelled = "cancelled"


class JobTier(str, Enum):
    preview = "preview"
    standard = "standard"
    high = "high"


class JobType(str, Enum):
    # Canonical user-facing modes (match worker DISPATCH + Android app):
    single_image = "single_image"   # 1 photo  -> generative preview
    multi_image = "multi_image"     # 20-50 photos (archive) -> HIGH-PRECISION reconstruction
    video = "video"                 # video    -> HIGH-PRECISION reconstruction
    # Legacy aliases kept for backward compatibility:
    image_to_3d = "image_to_3d"
    video_to_3d = "video_to_3d"


class User(Base):
    __tablename__ = "users"

    id = Column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    email = Column(String(255), unique=True, nullable=False)
    credits = Column(Integer, default=0, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    jobs = relationship("Job", back_populates="owner", lazy="selectin")


class Asset(Base):
    __tablename__ = "assets"

    id = Column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    owner_id = Column(Uuid(as_uuid=True), ForeignKey("users.id"), nullable=False)
    filename = Column(String(512), nullable=False)
    content_type = Column(String(128), nullable=False)
    size = Column(Integer, nullable=False)
    storage_key = Column(String(512), nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)


class Job(Base, AsyncAttrs):
    __tablename__ = "jobs"

    id = Column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    owner_id = Column(Uuid(as_uuid=True), ForeignKey("users.id"), nullable=False)
    asset_id = Column(Uuid(as_uuid=True), ForeignKey("assets.id"), nullable=False)
    job_type = Column(String(32), nullable=False)
    tier = Column(String(32), nullable=False)
    status = Column(String(32), default=JobStatus.queued.value, nullable=False)
    progress = Column(Integer, default=0, nullable=False)
    result_key = Column(String(512), nullable=True)
    preview_key = Column(String(512), nullable=True)
    error_message = Column(Text, nullable=True)
    credits_charged = Column(Integer, default=0, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False)

    owner = relationship("User", back_populates="jobs", lazy="selectin")
