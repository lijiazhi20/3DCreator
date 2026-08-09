package com.tdcreator.feature.camera

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Owns the CameraX use-cases and drives capture for all three reconstruction modes:
 *  - MULTI_IMAGE: sequential burst of [frameCount] photos (20–50) around 360°.
 *  - SINGLE_IMAGE: one photo.
 *  - VIDEO: one short clip.
 *
 * The screen is responsible for granting permission and calling [bind]; the session then
 * exposes [capturePhoto], [captureBurst] and [startVideo]/[stopVideo]. All CameraX objects are
 * created here so the screen stays thin and the capture logic is unit-testable in isolation.
 */
class CameraCaptureHelper {

    val imageCapture: ImageCapture = ImageCapture.Builder().build()
    val videoCapture: VideoCapture<Recorder> =
        VideoCapture.Builder(Recorder.Builder().build()).build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recording: Recording? = null
    private var burstJob: Job? = null

    var lensFacing: Int = CameraSelector.LENS_FACING_BACK
        private set

    /**
     * Bind the preview + capture use-cases to the lifecycle. Resolves [ProcessCameraProvider]
     * asynchronously (no main-thread block) and invokes [onReady] once bound.
     */
    fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int = this.lensFacing,
        onReady: () -> Unit = {},
    ) {
        this.lensFacing = lensFacing
        val future = ProcessCameraProvider.getInstance(previewView.context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture, videoCapture)
            onReady()
        }, ContextCompat.getMainExecutor(previewView.context))
    }

    fun flipLens(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        bind(lifecycleOwner, previewView, lensFacing)
    }

    private suspend fun takePhoto(context: Context): Uri = suspendCancellableCoroutine { cont ->
        val file = File(context.cacheDir, "shot_${System.nanoTime()}.jpg")
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                    cont.resume(r.savedUri ?: Uri.fromFile(file))
                }
                override fun onError(e: ImageCaptureException) = cont.resumeWithException(e)
            },
        )
    }

    /** Single photo capture. */
    suspend fun capturePhoto(context: Context): Uri = takePhoto(context)

    /**
     * Sequential burst for MULTI_IMAGE: captures [frameCount] photos [intervalMs] apart and
     * reports each saved frame via [onFrame]. Completes with [onComplete] (or [onError]).
     */
    fun captureBurst(
        context: Context,
        frameCount: Int,
        intervalMs: Long = 800L,
        onFrame: (Uri) -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        burstJob?.cancel()
        burstJob = scope.launch {
            try {
                repeat(frameCount) { i ->
                    if (!burstJob!!.isActive) return@launch
                    onFrame(takePhoto(context))
                    if (i < frameCount - 1) delay(intervalMs)
                }
                onComplete()
            } catch (e: Throwable) {
                onError(e)
            }
        }
    }

    fun cancelBurst() { burstJob?.cancel(); burstJob = null }

    fun startVideo(
        context: Context,
        onStart: () -> Unit,
        onResult: (Uri) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val file = File(context.cacheDir, "vid_${System.nanoTime()}.mp4")
        recording = videoCapture.output
            .prepareRecording(context, FileOutputOptions.Builder(file).build())
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> onStart()
                    is VideoRecordEvent.Finalize -> {
                        if (!event.hasError()) onResult(Uri.fromFile(file))
                        else onError(Exception("video finalize error ${event.error}"))
                    }
                }
            }
    }

    fun stopVideo() { recording?.stop(); recording = null }

    fun release() {
        cancelBurst()
        stopVideo()
        scope.cancel()
    }
}
