package com.tdcreator.feature.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tdcreator.core.data.repository.JobRepository
import com.tdcreator.core.network.ApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Resolves the model URL for the WebView GLB viewer. The viewer HTML
 * (assets/glb_viewer.html) reads the URL from the `?model=` query parameter.
 *
 * The backend streams the binary GLB at `GET /jobs/{id}/download` (no signed
 * URL / JSON envelope). The viewer loads it directly, so [modelUrl] is just
 * `${BASE_URL}jobs/{id}/download`.
 */
@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val api: ApiService,
    private val jobRepo: JobRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ViewerState>(ViewerState.Loading)
    val state: StateFlow<ViewerState> = _state

    fun load(jobId: String) {
        viewModelScope.launch {
            _state.value = ViewerState.Loading
            runCatching {
                // Confirm the model is ready, then build the direct download URL
                // (the backend serves the GLB file at this endpoint; the WebView loads it).
                val job = jobRepo.refreshJob(jobId)
                if (job.status != com.tdcreator.core.network.dto.JobStatus.SUCCEEDED) {
                    throw IllegalStateException("model not ready")
                }
                "${com.tdcreator.app.BuildConfig.BASE_URL}jobs/$jobId/download"
            }.onSuccess { url -> _state.value = ViewerState.Ready(url) }
                .onFailure { _state.value = ViewerState.Error }
        }
    }

    sealed interface ViewerState {
        data object Loading : ViewerState
        data class Ready(val modelUrl: String) : ViewerState
        data object Error : ViewerState
    }
}
