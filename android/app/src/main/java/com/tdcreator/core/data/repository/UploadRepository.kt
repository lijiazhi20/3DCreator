package com.tdcreator.core.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.tdcreator.core.data.local.UploadDao
import com.tdcreator.core.data.local.UploadEntity
import com.tdcreator.core.data.local.UploadStatus
import com.tdcreator.core.network.ApiService
import com.tdcreator.core.network.ProgressRequestBody
import com.tdcreator.core.network.dto.JobType
import com.tdcreator.core.network.dto.LocalUploadResponse
import com.tdcreator.core.network.dto.PresignRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedOutputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the upload flow:
 *   1. enqueue an [UploadEntity] (status QUEUED)
 *   2. [UploadWorker] uploads the bytes and obtains an `asset_id`
 *   3. calls `POST /jobs` to create the 3D reconstruction job
 *
 * Two upload strategies are provided:
 *   - [localUpload]   — multipart POST to `upload/local`; the dev/local backend (no R2 needed).
 *                       This is what [UploadWorker] uses today.
 *   - [presignAndUpload] — `POST /upload/presign` then PUT raw bytes to the presigned R2 URL.
 *                       Reserved for the production cloud backend.
 *
 * The network steps are exposed as suspend functions so the Worker can drive them and report
 * progress back into the Room queue.
 */
@Singleton
open class UploadRepository @Inject constructor(
    private val context: Context,
    private val api: ApiService,
    private val uploadDao: UploadDao,
) {

    fun observeQueue(): Flow<List<UploadEntity>> = uploadDao.observeQueue()

    /** One-shot lookup used by the Worker (which cannot collect a Flow). */
    suspend fun observeQueueOnce(uid: Long): UploadEntity? =
        uploadDao.getPending().firstOrNull { it.uid == uid }

    /**
     * Shared upload-session state: the reconstruction mode chosen on Home
     * (SINGLE_IMAGE / MULTI_IMAGE / VIDEO). Held here (a @Singleton) so the
     * HomeScreen and GalleryScreen — which get *separate* UploadViewModel
     * instances from hiltViewModel() — still agree on the mode.
     */
    private val _mode = MutableStateFlow(JobType.SINGLE_IMAGE)
    val mode: StateFlow<JobType> = _mode.asStateFlow()
    fun setMode(m: JobType) { _mode.value = m }

    suspend fun enqueue(uri: Uri, jobType: JobType = JobType.SINGLE_IMAGE): Long {
        val cr = context.contentResolver
        val fileName = fileNameFromUri(cr, uri)
        val contentType = contentTypeFromUri(cr, uri) ?: "application/octet-stream"
        val size = sizeFromUri(cr, uri)
        val entity = UploadEntity(
            localUri = uri.toString(),
            fileName = fileName,
            contentType = contentType,
            size = size,
            jobType = jobType.name,
            status = UploadStatus.QUEUED,
        )
        val uid = uploadDao.upsert(entity)
        scheduleWorker(uid)
        return uid
    }

    // ---- network steps driven by the Worker ----

    /**
     * Dev/local upload: multipart POST to `upload/local` (no R2 needed).
     * Returns the created asset_id so a 3D job can be created against it.
     */
    suspend fun localUpload(item: UploadEntity): String = localUpload(item) { }

    /**
     * Progress-aware variant of [localUpload]. [onProgress] receives 0–100 as bytes are streamed
     * to the backend; the Worker forwards this into Room via [updateProgress].
     */
    suspend fun localUpload(item: UploadEntity, onProgress: (percent: Int) -> Unit): String {
        uploadDao.update(item.copy(status = UploadStatus.UPLOADING, progress = 0))
        val bytes = context.contentResolver.openInputStream(Uri.parse(item.localUri))
            ?.use { it.readBytes() }
            ?: throw IllegalStateException("Cannot open ${item.localUri}")
        val body = ProgressRequestBody(
            bytes.toRequestBody(item.contentType.toMediaTypeOrNull()),
            onProgress,
        )
        val part = MultipartBody.Part.createFormData("file", item.fileName, body)
        val resp: LocalUploadResponse = api.localUpload(part)
        uploadDao.update(item.copy(status = UploadStatus.CREATING_JOB, assetId = resp.asset_id, progress = 100))
        return resp.asset_id
    }

    /**
     * Multi-photo 360° (HIGH-PRECISION) path.
     *
     * Bundle the selected photos into a single `.zip` and enqueue ONE `multi_image` job.
     * The worker uploads the zip; the backend extracts it and runs COLMAP + 3DGS + SuGaR
     * reconstruction from the 20–50 views.
     *
     * This is essential: enqueuing each photo separately would create N `single_image`
     * (generative / hallucinated) jobs, which is exactly the low-precision path the user
     * does NOT want for 360° capture.
     */
    suspend fun bundleAndEnqueueMulti(uris: List<Uri>): Long {
        require(uris.size >= 2) { "multi_image needs at least 2 photos" }
        val zipFile = File(context.cacheDir, "tdcreator_multi_${System.currentTimeMillis()}.zip")
        zipUris(uris, zipFile)
        return enqueue(Uri.fromFile(zipFile), JobType.MULTI_IMAGE)
    }

    private fun zipUris(uris: List<Uri>, zipFile: File) {
        val zipUri = Uri.fromFile(zipFile)
        context.contentResolver.openOutputStream(zipUri)?.use { os ->
            ZipOutputStream(BufferedOutputStream(os)).use { zos ->
                uris.forEachIndexed { i, uri ->
                    val ext = extensionFor(uri)
                    val entryName = String.format(Locale.US, "frame_%05d.%s", i + 1, ext)
                    zos.putNextEntry(ZipEntry(entryName))
                    context.contentResolver.openInputStream(uri)?.use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        } ?: throw IllegalStateException("Cannot create ${zipFile.absolutePath}")
    }

    /** Map a content Uri to a real image extension so the backend's image glob picks it up. */
    private fun extensionFor(uri: Uri): String {
        val mime = context.contentResolver.getType(uri)
        return when {
            mime == null -> uri.lastPathSegment
                ?.substringAfterLast('.')?.takeIf { it.length in 2..4 } ?: "jpg"
            mime.startsWith("image/png") -> "png"
            mime.startsWith("image/webp") -> "webp"
            mime.startsWith("image/bmp") -> "bmp"
            mime.startsWith("image/tiff") -> "tiff"
            else -> "jpg"
        }
    }

    suspend fun presignAndUpload(item: UploadEntity): String {
        uploadDao.update(item.copy(status = UploadStatus.PRESIGNING))
        val presign = api.presignUpload(
            PresignRequest(
                filename = item.fileName,
                content_type = item.contentType,
                size = item.size,
            ),
        )
        uploadDao.update(item.copy(status = UploadStatus.UPLOADING, assetId = presign.asset_id))

        val bytes = context.contentResolver.openInputStream(Uri.parse(item.localUri))
            ?.use { it.readBytes() }
            ?: throw IllegalStateException("Cannot open $item.localUri")

        val body = bytes.toRequestBody(item.contentType.toMediaTypeOrNull())
        api.putToPresignedUrl(presign.upload_url, body)

        uploadDao.update(item.copy(status = UploadStatus.CREATING_JOB, assetId = presign.asset_id))
        return presign.asset_id
    }

    suspend fun markDone(uid: Long) {
        val it = uploadDao.getPending().firstOrNull { it.uid == uid } ?: return
        uploadDao.update(it.copy(status = UploadStatus.DONE, progress = 100))
    }

    /** Persist incremental upload progress (0–100) so the UI can show a live bar. */
    suspend fun updateProgress(uid: Long, progress: Int) {
        val it = uploadDao.getPending().firstOrNull { it.uid == uid } ?: return
        if (it.status == UploadStatus.DONE || it.status == UploadStatus.FAILED) return
        if (progress <= it.progress && progress < 100) return
        uploadDao.update(it.copy(status = UploadStatus.UPLOADING, progress = progress))
    }

    /**
     * Mark an item FAILED. When [willRetry] is true (a retry is still pending) the status is
     * reset to QUEUED so the next attempt can re-upload; when false (retries exhausted) it is
     * set to FAILED. The no-arg [markFailed] keeps the original "always FAILED" behaviour.
     */
    suspend fun markFailed(uid: Long, e: Throwable) = markFailed(uid, e, willRetry = false)

    suspend fun markFailed(uid: Long, e: Throwable, willRetry: Boolean) {
        val it = uploadDao.getPending().firstOrNull { it.uid == uid } ?: return
        uploadDao.update(
            it.copy(
                status = if (willRetry) UploadStatus.QUEUED else UploadStatus.FAILED,
                progress = 0,
            ),
        )
    }

    // Test seam: open so unit tests can subclass with a no-op / recording scheduler
    // (the production path enqueues a WorkManager OneTimeWorkRequest).
    protected open fun scheduleWorker(uid: Long) {
        val req = OneTimeWorkRequestBuilder<com.tdcreator.feature.upload.UploadWorker>()
            .setInputData(Data.Builder().putLong(UploadWorker.KEY_UID, uid).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5_000L, TimeUnit.MILLISECONDS)
            .addTag("upload")
            .build()
        WorkManager.getInstance(context).enqueue(req)
        // Honor background-kill mitigation: keep the process foregrounded while the queue is
        // non-empty so aggressive OEM policies (e.g. Honor/MagicOS) don't evict the upload.
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, com.tdcreator.feature.upload.UploadForegroundService::class.java),
            )
        }
    }

    private fun fileNameFromUri(cr: ContentResolver, uri: Uri): String {
        val name = uri.lastPathSegment?.substringAfterLast('/')
        return name ?: "upload_${System.currentTimeMillis()}"
    }

    private fun contentTypeFromUri(cr: ContentResolver, uri: Uri): String? =
        cr.getType(uri) ?: MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(uri.lastPathSegment?.substringAfterLast('.'))

    private fun sizeFromUri(cr: ContentResolver, uri: Uri): Int =
        cr.openFileDescriptor(uri, "r")?.statSize?.toInt() ?: 0
}
