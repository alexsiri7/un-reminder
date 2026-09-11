package net.interstellarai.unreminder.worker

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.data.repository.EveningInvitationRepository
import net.interstellarai.unreminder.data.repository.EveningInvitationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

class EveningInvitationSchedulerTest {

    private val workManager: WorkManager = mockk(relaxed = true)
    private val repository: EveningInvitationRepository = mockk()

    private fun scheduler(settings: EveningInvitationSettings): EveningInvitationScheduler {
        every { repository.settings } returns flowOf(settings)
        return EveningInvitationScheduler(workManager, repository)
    }

    @Test
    fun `delayUntilNext targets today when the time is still ahead`() {
        val now = LocalDateTime.of(2026, 9, 11, 18, 0)

        val delay = EveningInvitationScheduler.delayUntilNext(now, LocalTime.of(20, 30), jitterMinutes = 0)

        assertEquals(Duration.ofMinutes(150), delay)
    }

    @Test
    fun `delayUntilNext targets tomorrow once the time has passed`() {
        val now = LocalDateTime.of(2026, 9, 11, 21, 0)

        val delay = EveningInvitationScheduler.delayUntilNext(now, LocalTime.of(20, 30), jitterMinutes = 0)

        assertEquals(Duration.ofHours(23).plusMinutes(30), delay)
    }

    @Test
    fun `delayUntilNext targets tomorrow when now is exactly the configured time`() {
        val now = LocalDateTime.of(2026, 9, 11, 20, 30)

        val delay = EveningInvitationScheduler.delayUntilNext(now, LocalTime.of(20, 30), jitterMinutes = 0)

        assertEquals(Duration.ofDays(1), delay)
    }

    @Test
    fun `jitter only ever pushes the fire time later`() {
        val now = LocalDateTime.of(2026, 9, 11, 18, 0)

        val delay = EveningInvitationScheduler.delayUntilNext(now, LocalTime.of(20, 30), jitterMinutes = 7)

        assertEquals(Duration.ofMinutes(157), delay)
    }

    @Test
    fun `ensureScheduled enqueues with KEEP when enabled`() = runTest {
        scheduler(EveningInvitationSettings(enabled = true, time = LocalTime.of(20, 30))).ensureScheduled()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                EveningInvitationScheduler.WORK_NAME,
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>(),
            )
        }
    }

    @Test
    fun `reschedule enqueues with REPLACE when enabled`() = runTest {
        scheduler(EveningInvitationSettings(enabled = true, time = LocalTime.of(20, 30))).reschedule()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                EveningInvitationScheduler.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                any<OneTimeWorkRequest>(),
            )
        }
    }

    @Test
    fun `enqueued request delay lands within the jitter window after the configured time`() = runTest {
        val request = slot<OneTimeWorkRequest>()
        val before = LocalDateTime.now()
        scheduler(EveningInvitationSettings(enabled = true, time = LocalTime.of(20, 30))).reschedule()
        val after = LocalDateTime.now()

        verify { workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), capture(request)) }
        val actual = Duration.ofMillis(request.captured.workSpec.initialDelay)
        val earliest = EveningInvitationScheduler.delayUntilNext(after, LocalTime.of(20, 30), 0)
        val latest = EveningInvitationScheduler.delayUntilNext(before, LocalTime.of(20, 30), EveningInvitationScheduler.MAX_JITTER_MINUTES)
        assertTrue("delay $actual not in [$earliest, $latest]", actual >= earliest && actual <= latest)
    }

    @Test
    fun `cancels the unique work instead of enqueuing when switched off`() = runTest {
        scheduler(EveningInvitationSettings(enabled = false, time = LocalTime.of(20, 30))).reschedule()

        verify(exactly = 1) { workManager.cancelUniqueWork(EveningInvitationScheduler.WORK_NAME) }
        verify(exactly = 0) { workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>()) }
    }
}
