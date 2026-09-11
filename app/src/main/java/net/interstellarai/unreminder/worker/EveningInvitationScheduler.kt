package net.interstellarai.unreminder.worker

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import net.interstellarai.unreminder.data.repository.EveningInvitationRepository
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class EveningInvitationScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val repository: EveningInvitationRepository,
) {
    companion object {
        const val WORK_NAME = "evening_invitation"
        const val MAX_JITTER_MINUTES = 10L

        // Jitter only ever pushes the fire time later. The worker refuses to post before
        // the configured time, which is how a run deferred past midnight is kept quiet.
        fun delayUntilNext(now: LocalDateTime, time: LocalTime, jitterMinutes: Long): Duration {
            val today = now.toLocalDate().atTime(time)
            val target = if (today.isAfter(now)) today else today.plusDays(1)
            return Duration.between(now, target).plusMinutes(jitterMinutes)
        }
    }

    /** App start and boot: leaves an already-scheduled run alone. */
    suspend fun ensureScheduled() = schedule(ExistingWorkPolicy.KEEP)

    /** Settings changes and post-fire: the next run must reflect the current settings. */
    suspend fun reschedule() = schedule(ExistingWorkPolicy.REPLACE)

    private suspend fun schedule(policy: ExistingWorkPolicy) {
        val settings = repository.settings.first()
        if (!settings.enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val jitter = Random.nextLong(0, MAX_JITTER_MINUTES + 1)
        val delay = delayUntilNext(LocalDateTime.now(), settings.time, jitter)
        val request = OneTimeWorkRequestBuilder<EveningInvitationWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, policy, request)
    }
}
