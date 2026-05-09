package net.interstellarai.unreminder.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException
import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.data.repository.WindowRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import net.interstellarai.unreminder.service.trigger.TriggerPipeline
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@HiltWorker
class RandomIntervalWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val windowRepository: WindowRepository,
    private val habitRepository: HabitRepository,
    private val triggerRepository: TriggerRepository,
    private val geofenceManager: GeofenceManager,
    private val triggerPipeline: TriggerPipeline,
    private val workManager: WorkManager
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "random_interval"
        const val MIN_DELAY_MINUTES = 15L
        const val MAX_DELAY_MINUTES = 30L
        private const val MIN_POST_NOTIF_MINUTES = 60L
        private const val MAX_POST_NOTIF_MINUTES = 90L
        private const val TAG = "RandomIntervalWorker"

        fun enqueueNext(workManager: WorkManager) {
            val delay = Random.nextLong(MIN_DELAY_MINUTES, MAX_DELAY_MINUTES)
            val request = OneTimeWorkRequestBuilder<RandomIntervalWorker>()
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun enqueueNextAfterFire(workManager: WorkManager) {
            val delay = Random.nextLong(MIN_POST_NOTIF_MINUTES, MAX_POST_NOTIF_MINUTES)
            val request = OneTimeWorkRequestBuilder<RandomIntervalWorker>()
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        // Bootstrap entrypoint for app start / boot. Uses KEEP so an already-alive
        // chain is not disturbed; enqueues a fresh first tick when nothing is scheduled.
        // Recovery from a dead/terminal chain remains the watchdog's job (REPLACE).
        fun enqueueInitial(workManager: WorkManager) {
            val delay = Random.nextLong(MIN_DELAY_MINUTES, MAX_DELAY_MINUTES)
            val request = OneTimeWorkRequestBuilder<RandomIntervalWorker>()
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        var step = "init"
        var triggerId: Long? = null
        var fired = false
        try {
            step = "windowQuery"
            val activeWindows = windowRepository.getActiveWindows()
            val dayBit = 1 shl (LocalDate.now().dayOfWeek.value - 1)
            val currentSecondOfDay = LocalTime.now().toSecondOfDay()

            val inWindow = activeWindows.any { window ->
                window.daysOfWeekBitmask and dayBit != 0 &&
                    window.startTime.toSecondOfDay() <= currentSecondOfDay &&
                    window.endTime.toSecondOfDay() >= currentSecondOfDay
            }

            if (!inWindow) {
                Log.d(TAG, "No active window covers now, skipping")
                scheduleNext()
                return Result.success()
            }

            step = "habitQuery"
            val eligibleHabits = habitRepository.getEligibleHabits(geofenceManager.currentLocationIds.value)
            if (eligibleHabits.isEmpty()) {
                Log.d(TAG, "No eligible habits, skipping")
                scheduleNext()
                return Result.success()
            }

            step = "triggerInsert"
            triggerId = triggerRepository.insert(
                TriggerEntity(
                    scheduledAt = Instant.now(),
                    status = TriggerStatus.SCHEDULED,
                    source = "random_interval"
                )
            )

            step = "pipeline"
            triggerPipeline.execute(triggerId)
            fired = triggerRepository.getById(triggerId)?.status == TriggerStatus.FIRED
        } catch (e: CancellationException) {
            // Intentionally not calling scheduleNext here — WorkManager may not be
            // in a safe state to accept new work during coroutine cancellation.
            // Recovery: UnReminderApp calls enqueueInitial() on next app start
            // (KEEP policy re-enqueues after CANCELLED terminal state).
            triggerId?.let { triggerRepository.updateOutcome(it, TriggerStatus.DISMISSED) }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Random interval worker failed at step=$step", e)
            Sentry.captureException(e) { scope ->
                scope.setTag("component", "random-interval-worker")
                scope.setTag("step", step)
            }
            triggerId?.let { triggerRepository.updateOutcome(it, TriggerStatus.DISMISSED) }
        }

        scheduleNext(fired)
        return Result.success()
    }

    private fun scheduleNext(fired: Boolean = false) {
        if (fired) enqueueNextAfterFire(workManager) else enqueueNext(workManager)
    }
}
