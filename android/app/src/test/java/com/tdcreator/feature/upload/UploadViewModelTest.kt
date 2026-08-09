package com.tdcreator.feature.upload

import android.net.Uri
import app.cash.turbine.test
import com.tdcreator.core.data.local.UploadStatus
import com.tdcreator.core.data.repository.FakeUploadDao
import com.tdcreator.core.data.repository.TestContexts
import com.tdcreator.core.data.repository.UploadRepository
import com.tdcreator.core.network.ApiService
import com.tdcreator.core.network.dto.JobType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [UploadViewModel]:
 *  - setMode updates the exposed [UploadViewModel.mode] StateFlow (asserted with Turbine)
 *  - enqueue(uri) routes to the repository using the CURRENT mode
 *  - enqueueMulti(uris) routes to the multi-image bundling path
 *
 * Uses a real (fake-subclass) [UploadRepository] backed by a [FakeUploadDao] and the
 * shared Robolectric [TestContexts] context, so the routing is verified against the
 * actual enqueue output rather than a mock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UploadViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var dao: FakeUploadDao
    private lateinit var repo: UploadRepository
    private lateinit var vm: UploadViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        val ctx = TestContexts.make()
        dao = FakeUploadDao()
        // Real repository (no-op scheduler) so mode routing is exercised for real.
        repo = object : UploadRepository(ctx, mock<ApiService>(), dao) {
            override fun scheduleWorker(uid: Long) = Unit
        }
        vm = UploadViewModel(repo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setMode_emitsUpdatedMode() = runTest {
        vm.mode.test {
            assertEquals(JobType.SINGLE_IMAGE, awaitItem()) // initial value
            vm.setMode(JobType.MULTI_IMAGE)
            assertEquals(JobType.MULTI_IMAGE, awaitItem())
        }
    }

    @Test
    fun enqueue_usesCurrentMode() = runTest {
        vm.setMode(JobType.MULTI_IMAGE)
        val uri = Uri.parse("content://test/0")
        vm.enqueue(uri)
        dispatcher.scheduler.advanceUntilIdle()

        val entity = dao.items.first()
        assertEquals("MULTI_IMAGE", entity.jobType)
        assertEquals(UploadStatus.QUEUED, entity.status)
    }

    @Test
    fun enqueueSingleMode_tagsSingleImage() = runTest {
        vm.setMode(JobType.SINGLE_IMAGE)
        val uri = Uri.parse("content://test/0")
        vm.enqueue(uri)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("SINGLE_IMAGE", dao.items.first().jobType)
    }

    @Test
    fun enqueueMulti_bundlesOneMultiImageJob() = runTest {
        val uris = listOf(
            Uri.parse("content://test/0"),
            Uri.parse("content://test/1"),
            Uri.parse("content://test/2"),
        )
        vm.enqueueMulti(uris)
        dispatcher.scheduler.advanceUntilIdle()

        // Exactly one job, tagged MULTI_IMAGE (the 360 deg high-precision path).
        assertEquals(1, dao.items.size)
        assertEquals("MULTI_IMAGE", dao.items.first().jobType)
    }
}
