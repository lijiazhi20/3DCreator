package com.tdcreator.feature.camera

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tdcreator.core.network.dto.JobType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Orchestrates capture for the three reconstruction modes and holds the results until the user
 * confirms and navigates to the upload screen.
 *
 *  - MULTI_IMAGE: sequential burst of [frameTarget] photos (20–50) around 360°. Frames are
 *    accumulated in [capturedFrames]; when the target is reached the session moves to REVIEW.
 *  - SINGLE_IMAGE: one photo (held in [capturedPhoto]).
 *  - VIDEO: one short clip (held in [capturedVideo]).
 *
 * The CameraX use-cases live in [capture] ([CameraCaptureHelper]) — the single camera engine.
 * The screen calls the thin wrappers below: [captureFrame] (360° multi-photo), [captureSingle]
 * (one photo), and [startVideoCapture]/[stopVideoCapture] (video). No capture logic lives in the
 * screen, eliminating the dual inline/helper implementation.
 */
class CameraViewModel : ViewModel() {

    /** Capture orchestration (CameraX use-cases + burst/recording drivers). */
    val capture = CameraCaptureHelper()

    private val _mode = MutableStateFlow(JobType.SINGLE_IMAGE)
    val mode = _mode.asStateFlow()
    fun setMode(m: JobType) { _mode.value = m }

    private val _capturedPhoto = MutableStateFlow<Uri?>(null)
    val capturedPhoto = _capturedPhoto.asStateFlow()

    private val _capturedVideo = MutableStateFlow<Uri?>(null)
    val capturedVideo = _capturedVideo.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    /** MULTI_IMAGE: collected frame URIs (content URIs / file URIs in cacheDir). */
    private val _capturedFrames = MutableStateFlow<List<Uri>>(emptyList())
    val capturedFrames = _capturedFrames.asStateFlow()

    /** Target frame count for the 360° burst (clamped 20–50). */
    private val _frameTarget = MutableStateFlow(DEFAULT_FRAMES)
    val frameTarget = _frameTarget.asStateFlow()
    fun setFrameTarget(n: Int) { _frameTarget.value = n.coerceIn(MIN_FRAMES, MAX_FRAMES) }

    private val _capturePhase = MutableStateFlow(CapturePhase.IDLE)
    val capturePhase = _capturePhase.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun clearError() { _error.value = null }

    // ---- single / video setters (used by the existing CameraScreen buttons) ----
    fun setPhoto(uri: Uri?) { _capturedPhoto.value = uri; _capturedVideo.value = null }
    fun setVideo(uri: Uri?) { _capturedVideo.value = uri; _capturedPhoto.value = null }
    fun setRecording(v: Boolean) { _isRecording.value = v }

    /** Append a frame for MULTI_IMAGE; auto-advances phase when the target is reached. */
    fun addFrame(uri: Uri) {
        _capturedFrames.update { it + uri }
        if (_capturedFrames.value.size >= _frameTarget.value) _capturePhase.value = CapturePhase.REVIEW
    }

    fun clearFrames() { _capturedFrames.value = emptyList(); _capturePhase.value = CapturePhase.IDLE }

    /**
     * MULTI_IMAGE: capture ONE frame manually. The user controls pacing/angle as they walk the
     * 360°, so we add a frame per tap rather than auto-bursting. Frames accumulate in
     * [capturedFrames]; [CapturePhase] flips to REVIEW once the target is reached.
     */
    fun captureFrame(context: Context) {
        _capturePhase.value = CapturePhase.CAPTURING
        viewModelScope.launch {
            runCatching { capture.capturePhoto(context) }
                .onSuccess { addFrame(it) }
                .onFailure { _error.value = it.message }
        }
    }

    /** SINGLE_IMAGE: capture one photo into [capturedPhoto]. */
    fun captureSingle(context: Context) {
        _capturePhase.value = CapturePhase.CAPTURING
        viewModelScope.launch {
            runCatching { capture.capturePhoto(context) }
                .onSuccess { uri -> _capturedPhoto.value = uri; _capturePhase.value = CapturePhase.REVIEW }
                .onFailure { _error.value = it.message }
        }
    }

    /** VIDEO: start recording (results stream into [capturedVideo] on finalize). */
    fun startVideoCapture(context: Context) {
        _capturePhase.value = CapturePhase.CAPTURING
        capture.startVideo(
            context = context,
            onStart = { _isRecording.value = true },
            onResult = { uri ->
                _capturedVideo.value = uri
                _isRecording.value = false
                _capturePhase.value = CapturePhase.REVIEW
            },
            onError = { _error.value = it.message; _isRecording.value = false },
        )
    }

    /** VIDEO: stop recording. */
    fun stopVideoCapture() { capture.stopVideo(); _isRecording.value = false }

    fun reset() {
        capture.cancelBurst()
        _capturedPhoto.value = null
        _capturedVideo.value = null
        _capturedFrames.value = emptyList()
        _isRecording.value = false
        _capturePhase.value = CapturePhase.IDLE
        _error.value = null
    }

    override fun onCleared() {
        capture.release()
        super.onCleared()
    }

    enum class CapturePhase { IDLE, CAPTURING, REVIEW }

    companion object {
        const val DEFAULT_FRAMES = 24
        const val MIN_FRAMES = 20
        const val MAX_FRAMES = 50
    }
}
