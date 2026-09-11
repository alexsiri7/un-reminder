package net.interstellarai.unreminder.worker

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.sentry.ScopeCallback
import io.sentry.Sentry
import io.sentry.protocol.SentryId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.repository.EveningInvitationRepository
import net.interstellarai.unreminder.data.repository.EveningInvitationSettings
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.domain.AvailabilityStatus
import net.interstellarai.unreminder.domain.HabitAvailabilityService
import net.interstellarai.unreminder.domain.UnavailableReason
import net.interstellarai.unreminder.service.notification.NotificationHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class EveningInvitationWorkerTest {

    private val mockContext: Context = mockk(relaxed = true)
    private val mockWorkerParams: WorkerParameters = mockk(relaxed = true)
    private val repository: EveningInvitationRepository = mockk(relaxUnitFun = true)
    private val triggerRepository: TriggerRepository = mockk()
    private val habitRepository: HabitRepository = mockk()
    private val availabilityService: HabitAvailabilityService = mockk()
    private val notificationHelper: NotificationHelper = mockk(relaxUnitFun = true)
    private val scheduler: EveningInvitationScheduler = mockk(relaxUnitFun = true)

    private lateinit var worker: EveningInvitationWorker

    private val habit = HabitEntity(id = 1L, name = "stretch")

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        // Midnight as the configured time keeps "now is after the configured time" true
        // whenever the test runs; the deferred-run case flips it with LocalTime.MAX.
        every { repository.settings } returns flowOf(EveningInvitationSettings(enabled = true, time = LocalTime.MIN))
        coEvery { repository.lastPostedDate() } returns null
        every { triggerRepository.hasCompletedAnythingToday() } returns flowOf(false)
        every { habitRepository.getAll() } returns flowOf(listOf(habit))
        coEvery { availabilityService.computeForAll(listOf(habit)) } returns mapOf(1L to AvailabilityStatus.Available)

        worker = EveningInvitationWorker(
            mockContext,
            mockWorkerParams,
            repository,
            triggerRepository,
            habitRepository,
            availabilityService,
            notificationHelper,
            scheduler,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `posts when nothing completed today and something is doable`() = runTest {
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) { notificationHelper.postEveningInvitation(any()) }
        coVerify(exactly = 1) { repository.markPostedOn(LocalDate.now()) }
        coVerify(exactly = 1) { scheduler.reschedule() }
    }

    @Test
    fun `does not post when something was completed today`() = runTest {
        every { triggerRepository.hasCompletedAnythingToday() } returns flowOf(true)

        worker.doWork()

        coVerify(exactly = 0) { notificationHelper.postEveningInvitation(any()) }
        coVerify(exactly = 0) { repository.markPostedOn(any()) }
        coVerify(exactly = 1) { scheduler.reschedule() }
    }

    @Test
    fun `does not post when no habit is doable right now`() = runTest {
        coEvery { availabilityService.computeForAll(listOf(habit)) } returns
            mapOf(1L to AvailabilityStatus.Unavailable(listOf(UnavailableReason.TIME_WINDOW)))

        worker.doWork()

        coVerify(exactly = 0) { notificationHelper.postEveningInvitation(any()) }
        coVerify(exactly = 1) { scheduler.reschedule() }
    }

    @Test
    fun `does not post when there are no habits at all`() = runTest {
        every { habitRepository.getAll() } returns flowOf(emptyList())

        worker.doWork()

        coVerify(exactly = 0) { notificationHelper.postEveningInvitation(any()) }
        coVerify(exactly = 0) { availabilityService.computeForAll(any()) }
    }

    @Test
    fun `a brand new habit counts as doable`() = runTest {
        coEvery { availabilityService.computeForAll(listOf(habit)) } returns mapOf(1L to AvailabilityStatus.NewHabit)

        worker.doWork()

        coVerify(exactly = 1) { notificationHelper.postEveningInvitation(any()) }
    }

    @Test
    fun `does not post twice on the same local date`() = runTest {
        coEvery { repository.lastPostedDate() } returns LocalDate.now()

        worker.doWork()

        coVerify(exactly = 0) { notificationHelper.postEveningInvitation(any()) }
        coVerify(exactly = 0) { repository.markPostedOn(any()) }
        coVerify(exactly = 1) { scheduler.reschedule() }
    }

    @Test
    fun `posts again on a later date`() = runTest {
        coEvery { repository.lastPostedDate() } returns LocalDate.now().minusDays(1)

        worker.doWork()

        coVerify(exactly = 1) { notificationHelper.postEveningInvitation(any()) }
    }

    @Test
    fun `does not post when run before the configured time and still reschedules`() = runTest {
        every { repository.settings } returns flowOf(EveningInvitationSettings(enabled = true, time = LocalTime.MAX))

        worker.doWork()

        coVerify(exactly = 0) { notificationHelper.postEveningInvitation(any()) }
        coVerify(exactly = 0) { repository.markPostedOn(any()) }
        coVerify(exactly = 1) { scheduler.reschedule() }
    }

    @Test
    fun `does nothing and does not reschedule when switched off`() = runTest {
        every { repository.settings } returns flowOf(EveningInvitationSettings(enabled = false, time = LocalTime.MIN))

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 0) { notificationHelper.postEveningInvitation(any()) }
        coVerify(exactly = 0) { scheduler.reschedule() }
    }

    @Test
    fun `never touches trigger rows`() = runTest {
        worker.doWork()

        coVerify(exactly = 0) { triggerRepository.insert(any()) }
        coVerify(exactly = 0) { triggerRepository.updateOutcome(any(), any()) }
    }

    @Test
    fun `still reschedules when a check throws`() = runTest {
        every { habitRepository.getAll() } throws RuntimeException("db gone")

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 0) { notificationHelper.postEveningInvitation(any()) }
        coVerify(exactly = 1) { scheduler.reschedule() }
    }

    @Test
    fun `reports a failing reschedule to Sentry instead of failing the run`() = runTest {
        mockkStatic(Sentry::class)
        every { Sentry.captureException(any(), any<ScopeCallback>()) } returns SentryId.EMPTY_ID
        coEvery { scheduler.reschedule() } throws IllegalStateException("WorkManager db gone")

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) { notificationHelper.postEveningInvitation(any()) }
        verify(exactly = 1) { Sentry.captureException(any(), any<ScopeCallback>()) }
        unmockkStatic(Sentry::class)
    }

    @Test
    fun `propagates CancellationException without rescheduling`() = runTest {
        every { habitRepository.getAll() } throws CancellationException("cancelled")

        var threw = false
        try {
            worker.doWork()
        } catch (e: CancellationException) {
            threw = true
        }

        assert(threw) { "Expected CancellationException to propagate" }
        coVerify(exactly = 0) { scheduler.reschedule() }
    }
}
