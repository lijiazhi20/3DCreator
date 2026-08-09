package com.tdcreator.feature.upload

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tdcreator.core.data.local.UploadEntity
import com.tdcreator.core.data.repository.UploadRepository
import com.tdcreator.core.network.dto.JobType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UploadViewModel @Inject constructor(
    private val uploadRepo: UploadRepository,
) : ViewModel() {

    val queue: StateFlow<List<UploadEntity>> = uploadRepo.observeQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Convenience: how many items are still in flight. */
    val queueSummary: StateFlow<Int> = uploadRepo.observeQueue()
        .map { it.count { e -> e.status != UploadStatus.DONE && e.status != UploadStatus.FAILED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Selected reconstruction mode (single photo / multi-photo 360° / video). */
    val mode: StateFlow<JobType> = uploadRepo.mode
    fun setMode(m: JobType) = uploadRepo.setMode(m)

    fun enqueue(uri: Uri) {
        viewModelScope.launch { uploadRepo.enqueue(uri, uploadRepo.mode.value) }
    }

    /** Bundle multiple photos into one multi_image (high-precision) job. */
    fun enqueueMulti(uris: List<Uri>) {
        viewModelScope.launch { uploadRepo.bundleAndEnqueueMulti(uris) }
    }
}
