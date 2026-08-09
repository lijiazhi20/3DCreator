package com.tdcreator.core.data.repository

import android.content.Context
import android.net.Uri
import com.tdcreator.core.data.local.UploadStatus
import com.tdcreator.core.network.ApiService
import com.tdcreator.core.network.dto.JobType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.zip.ZipFile

/**
 * Unit tests for [UploadRepository].
 *
 * - (a) MODE ROUTING: SINGLE_IMAGE enqueues a single-item job; MULTI_IMAGE goes
 *      through [UploadRepository.bundleAndEnqueueMulti] (one bundled job, not N).
 * - (b) ZIP BUNDLING: [UploadRepository.bundleAndEnqueueMulti] writes a zip whose
 *      entries are named frame_00001.jpg, frame_00002.jpg, ... for the N photos.
 *
 * A fake subclass records the scheduling call so we never touch WorkManager.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UploadRepositoryTest {

    /** Records scheduling instead of enqueuing a WorkManager request. */
    class TestUploadRepository(
        ctx: Context,
        api: ApiService,
        dao: UploadDao,
    ) : UploadRepository(ctx, api, dao) {
        val scheduled = mutableListOf<Long>()
        override fun scheduleWorker(uid: Long) {
            scheduled += uid
        }
    }

    private fun repo(ctx: Context, dao: FakeUploadDao) =
        TestUploadRepository(ctx, mock(), dao)

    @Test
    fun enqueueSingle_routesSingleImageAndSchedules() = runTest {
        val ctx = TestContexts.make()
        val dao = FakeUploadDao()
        val r = repo(ctx, dao)

        val uid = r.enqueue(Uri.parse("content://test/0"), JobType.SINGLE_IMAGE)

        val entity = dao.items.first { it.uid == uid }
        assertEquals("SINGLE_IMAGE", entity.jobType)
        assertEquals(UploadStatus.QUEUED, entity.status)
        assertTrue("scheduleWorker should have been called once", r.scheduled == listOf(uid))
    }

    @Test
    fun bundleAndEnqueueMulti_tagsMultiImageAndSchedulesSingleJob() = runTest {
        val ctx = TestContexts.make()
        val dao = FakeUploadDao()
        val r = repo(ctx, dao)

        val uris = listOf(
            Uri.parse("content://test/0"),
            Uri.parse("content://test/1"),
            Uri.parse("content://test/2"),
        )
        val uid = r.bundleAndEnqueueMulti(uris)

        // A single multi_image job is enqueued (NOT 3 single_image jobs).
        assertEquals(1, dao.items.count { it.uid == uid })
        assertEquals("MULTI_IMAGE", dao.items.first { it.uid == uid }.jobType)
        assertTrue(r.scheduled == listOf(uid))
    }

    @Test
    fun bundleAndEnqueueMulti_producesFrameNamedZipEntries() = runTest {
        val ctx = TestContexts.make()
        val dao = FakeUploadDao()
        val r = repo(ctx, dao)

        val uris = listOf(
            Uri.parse("content://test/0"),
            Uri.parse("content://test/1"),
            Uri.parse("content://test/2"),
        )
        r.bundleAndEnqueueMulti(uris)

        val zip = ctx.cacheDir.listFiles()!!.first {
            it.name.startsWith("tdcreator_multi_") && it.extension == "zip"
        }
        val entries = ZipFile(zip).use { zf ->
            zf.entries().toList().map { it.name }.sorted()
        }
        assertEquals(
            listOf("frame_00001.jpg", "frame_00002.jpg", "frame_00003.jpg"),
            entries,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun bundleAndEnqueueMulti_rejectsSinglePhoto() = runTest {
        val ctx = TestContexts.make()
        val dao = FakeUploadDao()
        repo(ctx, dao).bundleAndEnqueueMulti(listOf(Uri.parse("content://test/0")))
    }
}
