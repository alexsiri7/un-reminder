package net.interstellarai.unreminder.service.worker

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RefillScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    companion object {
        /** Spacing between consecutive habits in a paced sweep — well under the Worker's 60 req/min limit. */
        val PACE: Duration = Duration.ofMinutes(1)
    }

    fun enqueueForHabit(habitId: Long) {
        workManager.enqueueUniqueWork(
            "${RefillWorker.WORK_NAME}-$habitId",
            ExistingWorkPolicy.REPLACE,
            request(habitId, Duration.ZERO),
        )
    }

    /**
     * Enqueues one refill per habit, each [PACE] later than the previous, so a generation
     * version bump regenerates every pool without a burst against the Worker's rate limit
     * (#375). KEEP rather than REPLACE: a refill already pending or running for the habit
     * will stamp the new version anyway, and replacing it would only cancel an in-flight
     * generation and pay for it twice.
     */
    fun enqueuePaced(habitIds: List<Long>) {
        habitIds.forEachIndexed { index, habitId ->
            workManager.enqueueUniqueWork(
                "${RefillWorker.WORK_NAME}-$habitId",
                ExistingWorkPolicy.KEEP,
                request(habitId, PACE.multipliedBy(index.toLong())),
            )
        }
    }

    private fun request(habitId: Long, delay: Duration): OneTimeWorkRequest {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        return OneTimeWorkRequestBuilder<RefillWorker>()
            .setInputData(workDataOf(RefillWorker.KEY_HABIT_ID to habitId))
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS,
            )
            .apply { if (!delay.isZero) setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS) }
            .build()
    }
}
