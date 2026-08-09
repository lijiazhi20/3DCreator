package com.tdcreator.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Request body for `POST /upload/presign`.
 * Matches backend `app/routers/upload.py::PresignRequest` exactly.
 */
@Serializable
data class PresignRequest(
    val filename: String,
    val content_type: String,
    val size: Int,
)

/**
 * Response from `POST /upload/presign`.
 * The client then PUTs the raw file bytes to [upload_url] (S3/R2 presigned PUT).
 * Matches backend `PresignResponse` exactly.
 */
@Serializable
data class PresignResponse(
    val upload_url: String,
    val asset_id: String,
    val storage_key: String,
)

/**
 * Response from `POST /upload/local` (dev/local multipart upload — no R2 needed).
 * Matches backend `LocalUploadResponse` exactly.
 */
@Serializable
data class LocalUploadResponse(
    val asset_id: String,
    val storage_key: String,
    val filename: String,
    val content_type: String,
    val size: Int,
)
