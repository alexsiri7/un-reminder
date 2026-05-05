package net.interstellarai.unreminder.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.common.util.concurrent.Futures
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.sentry.Sentry
import io.sentry.ScopeCallback
import io.sentry.protocol.SentryId
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.data.repository.TriggerRepository
import org.junit.Assert.assertEquals
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
            mockWorkManager.getWorkInfosForUniqueWork(RandomIntervalWorker.WORK_NAME)
        } returns Futures.immediateFuture(infos)
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
    fun `doWork sweeps stuck scheduled triggers`() = runTest {
        stubWorkInfos(listOf(WorkInfo.State.ENQUEUED))

        worker.doWork()

        coVerify(exactly = 1) { mockTriggerRepository.deleteScheduledOlderThan(any()) }
    }

    @Test
    fun `reports unexpected exceptions to Sentry with component tag`() = runTest {
        mockkStatic(Sentry::class)
        every { Sentry.captureException(any(), any<ScopeCallback>()) } returns SentryId.EMPTY_ID
        every {
            mockWorkManager.getWorkInfosForUniqueWork(RandomIntervalWorker.WORK_NAME)
        } throws RuntimeException("boom")

        worker.doWork()

        verify(exactly = 1) { Sentry.captureException(any(), any<ScopeCallback>()) }
        unmockkStatic(Sentry::class)
    }
}
