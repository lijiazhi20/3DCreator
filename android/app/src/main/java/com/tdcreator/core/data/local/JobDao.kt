package com.tdcreator.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {

    @Query("SELECT * FROM jobs ORDER BY createdAt DESC")
    fun observeJobs(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE id = :id")
    fun observeJob(id: String): Flow<JobEntity?>

    @Query("SELECT * FROM jobs WHERE status IN ('queued','running')")
    suspend fun getActiveJobs(): List<JobEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: JobEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(jobs: List<JobEntity>)

    @Update
    suspend fun update(job: JobEntity)

    @Query("DELETE FROM jobs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM jobs")
    suspend fun clear()
}

@Dao
interface UploadDao {

    @Query("SELECT * FROM upload_queue ORDER BY uid ASC")
    fun observeQueue(): Flow<List<UploadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: UploadEntity): Long

    @Update
    suspend fun update(item: UploadEntity)

    @Query("DELETE FROM upload_queue WHERE uid = :uid")
    suspend fun delete(uid: Long)

    @Query("SELECT * FROM upload_queue WHERE status != 'DONE' AND status != 'FAILED'")
    suspend fun getPending(): List<UploadEntity>
}
