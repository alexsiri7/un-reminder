package net.interstellarai.unreminder.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.sentry.Sentry
import io.sentry.ScopeCallback
import io.sentry.protocol.SentryId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.db.WindowEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.data.repository.WindowRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import net.interstellarai.unreminder.service.trigger.TriggerPipeline
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalTime

class RandomIntervalWorkerTest {

    private val mockContext: Context = mockk(relaxed = true)
    private val mockWorkerParams: WorkerParameters = mockk(relaxed = true)
    private val mockWindowRepository: WindowRepository = mockk()
    private val mockHabitRepository: HabitRepository = mockk()
    private val mockTriggerRepository: TriggerRepository = mockk(relaxUnitFun = true)
    private val mockGeofenceManager: GeofenceManager = mockk(relaxed = true)
    private val mockTriggerPipeline: TriggerPipeline = mockk()
    private val mockWorkManager: WorkManager = mockk(relaxed = true)

    private lateinit var worker: RandomIntervalWorker

    @Before
    fun setup() {
        worker = RandomIntervalWorker(
            mockContext,
            mockWorkerParams,
            mockWindowRepository,
            mockHabitRepository,
            mockTriggerRepository,
            mockGeofenceManager,
            mockTriggerPipeline,
            mockWorkManager
        )
    }

    private fun windowCoveringAllDay(): WindowEntity = WindowEntity(
        startTime = LocalTime.MIN,
        endTime = LocalTime.MAX,
        daysOfWeekBitmask = 0b1111111,
        frequencyPerDay = 1,
        active = true
    )

    @Test
    fun `doWork returns success and enqueues next when no windows active`() = runTest {
        coEvery { mockWindowRepository.getActiveWindows() } returns emptyList()

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 0) { mockTriggerRepository.insert(any()) }
        verify(exactly = 1) { mockWorkManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>()) }
    }

    @Test
    fun `doWork returns success and enqueues next when no eligible habits`() = runTest {
        coEvery { mockWindowRepository.getActiveWindows() } returns listOf(windowCoveringAllDay())
        coEvery { mockHabitRepository.getEligibleHabits(any()) } returns emptyList()

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 0) { mockTriggerRepository.insert(any()) }
        verify(exactly = 1) { mockWorkManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>()) }
    }

    @Test
    fun `doWork inserts trigger and executes pipeline on happy path`() = runTest {
        coEvery { mockWindowRepository.getActiveWindows() } returns listOf(windowCoveringAllDay())
        coEvery { mockHabitRepository.getEligibleHabits(any()) } returns listOf(mockk())
        coEvery { mockTriggerRepository.insert(any()) } returns 42L
        coEvery { mockTriggerPipeline.execute(42L) } returns Unit

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) {
            mockTriggerRepository.insert(match { it.source == "random_interval" })
        }
        coVerify(exactly = 1) { mockTriggerPipeline.execute(42L) }
        verify(exactly = 1) { mockWorkManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>()) }
    }

    @Test
    fun `doWork still enqueues next when pipeline throws non-cancellation exception`() = runTest {
        coEvery { mockWindowRepository.getActiveWindows() } returns listOf(windowCoveringAllDay())
        coEvery { mockHabitRepository.getEligibleHabits(any()) } returns listOf(mockk())
        coEvery { mockTriggerRepository.insert(any()) } returns 1L
        coEvery { mockTriggerPipeline.execute(any()) } throws RuntimeException("pipeline error")

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        verify(exactly = 1) { mockWorkManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>()) }
    }

    @Test
    fun `doWork reports to Sentry when pipeline throws non-cancellation exception`() = runTest {
        mockkStatic(Sentry::class)
        every { Sentry.captureException(any(), any<ScopeCallback>()) } returns SentryId.EMPTY_ID

        coEvery { mockWindowRepository.getActiveWindows() } returns listOf(windowCoveringAllDay())
        coEvery { mockHabitRepository.getEligibleHabits(any()) } returns listOf(mockk())
        coEvery { mockTriggerRepository.insert(any()) } returns 1L
        coEvery { mockTriggerPipeline.execute(any()) } throws RuntimeException("pipeline error")

        worker.doWork()

        verify(exactly = 1) { Sentry.captureException(any(), any<ScopeCallback>()) }
        unmockkStatic(Sentry::class)
    }

    @Test
    fun `doWork propagates CancellationException without enqueuing next`() = runTest {
        coEvery { mockWindowRepository.getActiveWindows() } throws CancellationException("cancelled")

        var threw = false
        try {
            worker.doWork()
        } catch (e: CancellationException) {
            threw = true
        }

        assert(threw) { "Expected CancellationException to propagate" }
        verify(exactly = 0) { mockWorkManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>()) }
    }

    @Test
    fun `doWork marks trigger dismissed when CancellationException thrown after insert`() = runTest {
        coEvery { mockWindowRepository.getActiveWindows() } returns listOf(windowCoveringAllDay())
        coEvery { mockHabitRepository.getEligibleHabits(any()) } returns listOf(mockk())
        coEvery { mockTriggerRepository.insert(any()) } returns 99L
        coEvery { mockTriggerPipeline.execute(any()) } throws CancellationException("cancelled")

        try {
            worker.doWork()
        } catch (e: CancellationException) {
            // expected
        }

        coVerify(exactly = 1) { mockTriggerRepository.updateOutcome(99L, TriggerStatus.DISMISSED) }
        verify(exactly = 0) { mockWorkManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>()) }
    }
}
