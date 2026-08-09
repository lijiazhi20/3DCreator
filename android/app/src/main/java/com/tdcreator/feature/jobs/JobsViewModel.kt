package com.tdcreator.feature.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tdcreator.core.data.local.JobEntity
import com.tdcreator.core.data.repository.JobRepository
import com.tdcreator.core.network.dto.JobStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JobsViewModel @Inject constructor(
    private val jobRepo: JobRepository,
) : ViewModel() {

    val jobs: StateFlow<List<JobEntity>> = jobRepo.observeJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _loading = MutableStateFlow(false)
    val loading = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun clearError() { _error.value = null }

    private var activePollJob: Job? = null

    init { refresh(); startPollingActive() }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            runCatching { jobRepo.refreshJobs() }.onFailure { _error.value = it.message }
            _loading.value = false
        }
    }

    /**
     * Periodically re-poll the job list while any job is still active (queued/running). Stops
     * automatically when the list is empty or everything has reached a terminal state. Safe to
     * call repeatedly — a new call replaces the previous poll loop.
     */
    fun startPollingActive(intervalMs: Long = 5000L) {
        activePollJob?.cancel()
        activePollJob = viewModelScope.launch {
            flow { while (true) { emit(Unit); delay(intervalMs) } }
                .onEach {
                    runCatching { jobRepo.refreshJobs() }.onFailure { _error.value = it.message }
                    val hasActive = jobRepo.observeJobs().first()
                        .any { it.status == JobStatus.QUEUED || it.status == JobStatus.RUNNING }
                    if (!hasActive) activePollJob?.cancel()
                }
                .collect { /* driven by onEach */ }
        }
    }

    fun stopPollingActive() { activePollJob?.cancel(); activePollJob = null }

    override fun onCleared() {
        stopPollingActive()
        super.onCleared()
    }
}

@HiltViewModel
class JobDetailViewModel @Inject constructor(
    private val jobRepo: JobRepository,
) : ViewModel() {

    private val _job = MutableStateFlow<JobEntity?>(null)
    val job: StateFlow<JobEntity?> = _job

    private val _polling = MutableStateFlow(false)
    val polling = _polling.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun clearError() { _error.value = null }

    private var pollJob: Job? = null

    fun load(id: String) {
        viewModelScope.launch {
            runCatching { _job.value = jobRepo.refreshJob(id) }.onFailure { _error.value = it.message }
        }
        // Begin continuous polling (terminal-aware) so the existing screen gets live updates.
        startPolling(id)
    }

    /**
     * Poll GET /jobs/{id} every [intervalMs] until the job reaches a terminal state
     * (succeeded / failed / cancelled), then stop automatically. Network errors are swallowed
     * into [error] without crashing the loop.
     */
    fun startPolling(id: String, intervalMs: Long = 3000L) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            _polling.value = true
            flow { while (true) { emit(Unit); delay(intervalMs) } }
                .onEach {
                    runCatching { jobRepo.refreshJob(id) }
                        .onSuccess { j ->
                            _job.value = j
                            if (j.status.isTerminal()) pollJob?.cancel()
                        }
                        .onFailure { _error.value = it.message }
                }
                .collect { /* driven by onEach */ }
            _polling.value = false
        }
    }

    fun stopPolling() { pollJob?.cancel(); _polling.value = false }

    /** One-shot refresh (manual pull-to-refresh / button). */
    fun poll(id: String) {
        viewModelScope.launch {
            runCatching { _job.value = jobRepo.refreshJob(id) }.onFailure { _error.value = it.message }
        }
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }
}

private fun JobStatus.isTerminal(): Boolean =
    this == JobStatus.SUCCEEDED || this == JobStatus.FAILED || this == JobStatus.CANCELLED
