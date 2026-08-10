package com.tdcreator.core.data.repository

import com.tdcreator.core.data.local.JobDao
import com.tdcreator.core.data.local.JobEntity
import com.tdcreator.core.network.ApiService
import com.tdcreator.core.network.dto.CreateJobRequest
import com.tdcreator.core.network.dto.JobResponse
import com.tdcreator.core.network.dto.JobTier
import com.tdcreator.core.network.dto.JobType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for jobs: network results are cached in Room and surfaced as Flows
 * so the UI updates automatically during polling.
 */
@Singleton
class JobRepository @Inject constructor(
    private val api: ApiService,
    private val jobDao: JobDao,
) {

    fun observeJobs(): Flow<List<JobEntity>> = jobDao.observeJobs()
    fun observeJob(id: String): Flow<JobEntity?> = jobDao.observeJob(id)

    /** Pull the full job list from the backend and cache it. */
    suspend fun refreshJobs(): List<JobEntity> {
        val remote = api.listJobs()
        val entities = remote.map { toEntity(it) }
        jobDao.upsertAll(entities)
        return entities
    }

    /** Poll a single job (used by the detail screen + periodic worker). */
    suspend fun refreshJob(id: String): JobEntity {
        val remote = api.getJob(id)
        val entity = toEntity(remote)
        jobDao.upsert(entity)
        return entity
    }

    suspend fun createJob(assetId: String, type: JobType, tier: JobTier): JobResponse {
        return api.createJob(CreateJobRequest(asset_id = assetId, job_type = type, tier = tier))
    }

    fun toEntity(r: JobResponse): JobEntity = JobEntity(
        id = r.id,
        assetId = r.asset_id,
        jobType = r.job_type,
        tier = r.tier,
        status = r.status,
        progress = r.progress,
        resultKey = r.result_key,
        previewKey = r.preview_key,
        creditsCharged = r.credits_charged,
        createdAt = r.created_at,
        updatedAt = r.updated_at,
        lastPolledAt = System.currentTimeMillis(),
    )
}
