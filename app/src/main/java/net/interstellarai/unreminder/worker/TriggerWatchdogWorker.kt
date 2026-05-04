package net.interstellarai.unreminder.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException
import net.interstellarai.unreminder.data.repository.TriggerRepository
import java.time.Instant
import java.util.concurrent.TimeUnit

@HiltWorker
class TriggerWatchdogWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val workManager: WorkManager,
    private val triggerRepository: TriggerRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "trigger_watchdog"
        const val INTERVAL_HOURS = 24L
        const val STUCK_TRIGGER_AGE_SECONDS = 1800L
        private const val TAG = "TriggerWatchdogWorker"

        // If INTERVAL_HOURS ever changes, switch the policy below to UPDATE so the
        // new cadence takes effect; KEEP preserves the previously-scheduled interval.
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<TriggerWatchdogWorker>(
                INTERVAL_HOURS, TimeUnit.HOURS
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        try {
            val workInfos = workManager
                .getWorkInfosForUniqueWork(RandomIntervalWorker.WORK_NAME)
                .get()
            val healthy = workInfos.any { it.state in HEALTHY_STATES }
            if (!healthy) {
                RandomIntervalWorker.enqueueNext(workManager)
            }

            val cutoff = Instant.now()
                .minusSeconds(STUCK_TRIGGER_AGE_SECONDS)
                .toEpochMilli()
            triggerRepository.deleteScheduledOlderThan(cutoff)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Trigger watchdog worker failed", e)
            Sentry.captureException(e) { scope ->
                scope.setTag("component", "trigger-watchdog-worker")
            }
        }
        return Result.success()
    }
}

private val HEALTHY_STATES = setOf(
    WorkInfo.State.ENQUEUED,
    WorkInfo.State.RUNNING,
    WorkInfo.State.BLOCKED
)
