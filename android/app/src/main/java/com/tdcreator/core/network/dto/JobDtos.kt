package com.tdcreator.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Job type. Values match the backend `JobType` str-enum.
 * SINGLE_IMAGE / MULTI_IMAGE / VIDEO are the canonical user-facing modes;
 * IMAGE_TO_3D / VIDEO_TO_3D are kept as legacy aliases.
 */
@Serializable
enum class JobType {
    @SerialName("single_image")
    SINGLE_IMAGE,

    @SerialName("multi_image")
    MULTI_IMAGE,

    @SerialName("video")
    VIDEO,

    @SerialName("image_to_3d")
    IMAGE_TO_3D,

    @SerialName("video_to_3d")
    VIDEO_TO_3D,
}

/**
 * Quality tier. Values match the backend `JobTier` str-enum (preview | standard | high).
 */
@Serializable
enum class JobTier {
    @SerialName("preview")
    PREVIEW,

    @SerialName("standard")
    STANDARD,

    @SerialName("high")
    HIGH,
}

/**
 * Lifecycle status. Values match the backend `JobStatus` str-enum.
 */
@Serializable
enum class JobStatus {
    @SerialName("queued")
    QUEUED,

    @SerialName("running")
    RUNNING,

    @SerialName("succeeded")
    SUCCEEDED,

    @SerialName("failed")
    FAILED,

    @SerialName("cancelled")
    CANCELLED,
}

/**
 * Request body for `POST /jobs`.
 * Matches backend `app/routers/jobs.py::CreateJobRequest` exactly.
 */
@Serializable
data class CreateJobRequest(
    val asset_id: String,
    val job_type: JobType,
    val tier: JobTier = JobTier.PREVIEW,
)

/**
 * Job representation returned by the backend.
 * Matches backend `JobResponse` (from_attributes) exactly.
 * `result_key` / `preview_key` are nullable on the backend.
 */
@Serializable
data class JobResponse(
    val id: String,
    val asset_id: String,
    val job_type: JobType,
    val tier: JobTier,
    val status: JobStatus,
    val progress: Int,
    val result_key: String? = null,
    val preview_key: String? = null,
    val credits_charged: Int,
    val created_at: String,
    val updated_at: String,
)
