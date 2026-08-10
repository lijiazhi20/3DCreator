package com.tdcreator.feature.upload

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.tdcreator.app.R
import com.tdcreator.core.data.repository.JobRepository
import com.tdcreator.core.data.repository.UploadRepository
import com.tdcreator.core.network.dto.JobTier
import com.tdcreator.core.network.dto.JobType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Drives one upload item end-to-end:
 *   upload bytes (local or presigned) → create 3D job (POST /jobs).
 * Runs as a foreground service (dataSync) so Honor's aggressive battery policies don't kill it.
 * Uses exponential backoff (see [MAX_RETRIES]) and persists progress to Room.
 */
@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val uploadRepo: UploadRepository,
    private val jobRepo: JobRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uid = inputData.getLong(KEY_UID, -1L)
        if (uid < 0) return@withContext Result.failure()

        setForeground(createForegroundInfo())
        try {
            val item = uploadRepo.observeQueueOnce(uid)
                ?: return@withContext Result.failure()
            // Stream bytes with live progress into Room. The okio write callback is NOT a suspend
            // context, so we bridge into one with runBlocking for the tiny Room write.
            val assetId = uploadRepo.localUpload(item) { pct ->
                runCatching { runBlocking { uploadRepo.updateProgress(uid, pct) } }
            }
            // Honor the user-selected reconstruction mode, but force VIDEO for video input.
            val type = if (item.contentType.startsWith("video")) {
                JobType.VIDEO
            } else {
                runCatching { JobType.valueOf(item.jobType) }.getOrDefault(JobType.SINGLE_IMAGE)
            }
            // Map mode → quality tier so multi-photo 360° gets HIGH precision.
            val tier = when (type) {
                JobType.MULTI_IMAGE -> JobTier.HIGH
                JobType.VIDEO -> JobTier.STANDARD
                else -> JobTier.PREVIEW
            }
            jobRepo.createJob(assetId, type, tier)
            uploadRepo.markDone(uid)
            Result.success()
        } catch (e: Throwable) {
            // Retry with exponential backoff until MAX_RETRIES, then mark FAILED.
            val willRetry = runAttemptCount < MAX_RETRIES
            uploadRepo.markFailed(uid, e, willRetry)
            if (willRetry) Result.retry() else Result.failure()
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val channelId = "upload_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(
                NotificationChannel(channelId, "Uploads", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification: Notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText(applicationContext.getString(R.string.upload_uploading))
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIF_ID, notification)
    }

    companion object {
        const val KEY_UID = "upload_uid"
        const val NOTIF_ID = 1001
        const val MAX_RETRIES = 3
    }
}
