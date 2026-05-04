package net.interstellarai.unreminder.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import io.sentry.Sentry
import io.sentry.ScopeCallback
import io.sentry.protocol.SentryId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.data.repository.TriggerRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TriggerWatchdogWorkerTest {

    private val mockContext: Context = mockk(relaxed = true)
    private val mockWorkerParams: WorkerParameters = mockk(relaxed = true)
    private val mockWorkManager: WorkManager = mockk(relaxed = true)
    private val mockTriggerRepository: TriggerRepository = mockk(relaxUnitFun = true)

    private lateinit var worker: TriggerWatchdogWorker

    @Before
    fun setup() {
        worker = TriggerWatchdogWorker(
            mockContext,
            mockWorkerParams,
            mockWorkManager,
            mockTriggerRepository
        )
    }

    private fun stubWorkInfos(states: List<WorkInfo.State>) {
        val infos = states.map { state ->
            mockk<WorkInfo> { every { this@mockk.state } returns state }
        }
        every {
            mockWorkManager.getWorkInfosForUniqueWorkFlow(RandomIntervalWorker.WORK_NAME)
        } returns flowOf(infos)
    }

    @Test
    fun `doWork re-enqueues random interval worker when no work is scheduled`() = runTest {
        stubWorkInfos(emptyList())

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        verify(exactly = 1) {
            mockWorkManager.enqueueUniqueWork(
                eq(RandomIntervalWorker.WORK_NAME),
                any<ExistingWorkPolicy>(),
                any<OneTimeWorkRequest>()
            )
        }
        coVerify(exactly = 1) { mockTriggerRepository.deleteScheduledOlderThan(any()) }
    }

    @Test
    fun `doWork re-enqueues when only terminal states are present`() = runTest {
        stubWorkInfos(listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED))

        worker.doWork()

        verify(exactly = 1) {
            mockWorkManager.enqueueUniqueWork(
                eq(RandomIntervalWorker.WORK_NAME),
                any<ExistingWorkPolicy>(),
                any<OneTimeWorkRequest>()
            )
        }
    }

    @Test
    fun `doWork does not re-enqueue when random interval worker is enqueued`() = runTest {
        stubWorkInfos(listOf(WorkInfo.State.ENQUEUED))

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        verify(exactly = 0) {
            mockWorkManager.enqueueUniqueWork(
                eq(RandomIntervalWorker.WORK_NAME),
                any<ExistingWorkPolicy>(),
                any<OneTimeWorkRequest>()
            )
        }
    }

    @Test
    fun `doWork does not re-enqueue when random interval worker is running`() = runTest {
        stubWorkInfos(listOf(WorkInfo.State.RUNNING))

        worker.doWork()

        verify(exactly = 0) {
            mockWorkManager.enqueueUniqueWork(
                eq(RandomIntervalWorker.WORK_NAME),
                any<ExistingWorkPolicy>(),
                any<OneTimeWorkRequest>()
            )
        }
    }

    @Test
    fun `doWork does not re-enqueue when random interval worker is blocked`() = runTest {
        stubWorkInfos(listOf(WorkInfo.State.BLOCKED))

        worker.doWork()

        verify(exactly = 0) {
            mockWorkManager.enqueueUniqueWork(
                eq(RandomIntervalWorker.WORK_NAME),
                any<ExistingWorkPolicy>(),
                any<OneTimeWorkRequest>()
            )
        }
    }

    @Test
    fun `doWork sweeps stuck scheduled triggers older than configured age`() = runTest {
        stubWorkInfos(listOf(WorkInfo.State.ENQUEUED))
        val cutoffSlot = slot<Long>()
        coEvery { mockTriggerRepository.deleteScheduledOlderThan(capture(cutoffSlot)) } returns Unit

        val before = System.currentTimeMillis()
        worker.doWork()
        val after = System.currentTimeMillis()

        val ageMillis = TriggerWatchdogWorker.STUCK_TRIGGER_AGE_SECONDS * 1000
        val expectedCutoffLow = before - ageMillis
        val expectedCutoffHigh = after - ageMillis
        assertTrue(
            "cutoff ${cutoffSlot.captured} outside expected window [$expectedCutoffLow, $expectedCutoffHigh]",
            cutoffSlot.captured in expectedCutoffLow..expectedCutoffHigh
        )
    }

    @Test
    fun `doWork captures exception to Sentry and returns success when workInfos query throws`() = runTest {
        mockkStatic(android.util.Log::class)
        mockkStatic(Sentry::class)
        try {
            every { android.util.Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
            every { Sentry.captureException(any(), any<ScopeCallback>()) } returns SentryId.EMPTY_ID

            every {
                mockWorkManager.getWorkInfosForUniqueWorkFlow(RandomIntervalWorker.WORK_NAME)
            } returns flow { throw RuntimeException("workmanager unavailable") }

            val result = worker.doWork()

            assertEquals(Result.success(), result)
            verify(exactly = 1) { Sentry.captureException(any(), any<ScopeCallback>()) }
        } finally {
            unmockkStatic(Sentry::class)
            unmockkStatic(android.util.Log::class)
        }
    }

    @Test
    fun `doWork propagates CancellationException`() = runTest {
        every {
            mockWorkManager.getWorkInfosForUniqueWorkFlow(RandomIntervalWorker.WORK_NAME)
        } returns flow { throw CancellationException("cancelled") }

        var threw = false
        try {
            worker.doWork()
        } catch (e: CancellationException) {
            threw = true
        }

        assertTrue("Expected CancellationException to propagate", threw)
    }
}
