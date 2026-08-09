package com.tdcreator.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tdcreator.core.network.dto.JobStatus
import com.tdcreator.core.network.dto.JobTier
import com.tdcreator.core.network.dto.JobType

/**
 * Local cache of a job. Mirrors the backend `JobResponse` so the job list works offline and
 * survives polling. `uploadLocalPath` is the on-device uri we uploaded from (for re-upload).
 */
@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey val id: String,
    val assetId: String,
    val jobType: JobType,
    val tier: JobTier,
    val status: JobStatus,
    val progress: Int,
    val resultKey: String?,
    val previewKey: String?,
    val creditsCharged: Int,
    val createdAt: String,
    val updatedAt: String,
    // client-only fields
    val localSourcePath: String? = null,
    val lastPolledAt: Long = 0L,
)

/**
 * A queued upload item tracked by the upload WorkManager chain.
 */
@Entity(tableName = "upload_queue")
data class UploadEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val localUri: String,
    val fileName: String,
    val contentType: String,
    val size: Int,
    val assetId: String? = null,
    val jobType: String = "SINGLE_IMAGE",
    val status: UploadStatus,
    val progress: Int = 0,
)

enum class UploadStatus {
    QUEUED,
    PRESIGNING,
    UPLOADING,
    CREATING_JOB,
    DONE,
    FAILED,
}
