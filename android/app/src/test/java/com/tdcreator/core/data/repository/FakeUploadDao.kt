package com.tdcreator.core.data.repository

import com.tdcreator.core.data.local.UploadDao
import com.tdcreator.core.data.local.UploadEntity
import com.tdcreator.core.data.local.UploadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory fake of [UploadDao] for unit tests. Persists entities in a list and
 * exposes [items] so tests can assert on what was enqueued / updated.
 */
class FakeUploadDao : UploadDao {
    val items = mutableListOf<UploadEntity>()
    private var nextUid = 1L

    override fun observeQueue(): Flow<List<UploadEntity>> = flowOf(items.toList())

    override suspend fun upsert(item: UploadEntity): Long {
        val uid = if (item.uid == 0L) nextUid++ else item.uid
        items.removeAll { it.uid == uid }
        items.add(item.copy(uid = uid))
        return uid
    }

    override suspend fun update(item: UploadEntity) {
        val idx = items.indexOfFirst { it.uid == item.uid }
        if (idx >= 0) items[idx] = item else items.add(item)
    }

    override suspend fun delete(uid: Long) {
        items.removeAll { it.uid == uid }
    }

    override suspend fun getPending(): List<UploadEntity> =
        items.filter { it.status != UploadStatus.DONE && it.status != UploadStatus.FAILED }
}
