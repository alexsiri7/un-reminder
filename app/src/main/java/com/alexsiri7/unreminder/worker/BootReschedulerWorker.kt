package com.alexsiri7.unreminder.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alexsiri7.unreminder.data.repository.TriggerRepository
import com.alexsiri7.unreminder.service.alarm.AlarmScheduler
import com.alexsiri7.unreminder.service.geofence.GeofenceManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant

@HiltWorker
class BootReschedulerWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val triggerRepository: TriggerRepository,
    private val alarmScheduler: AlarmScheduler,
    private val geofenceManager: GeofenceManager
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "boot_rescheduler"
    }

    override suspend fun doWork(): Result {
        // Re-register geofences
        geofenceManager.registerAllFromDb()

        // Re-schedule all future SCHEDULED triggers
        val scheduledTriggers = triggerRepository.getAllScheduled()
        val now = Instant.now()
        for (trigger in scheduledTriggers) {
            if (trigger.scheduledAt.isAfter(now)) {
                alarmScheduler.scheduleExactAlarm(trigger.id, trigger.scheduledAt)
            }
        }

        return Result.success()
    }
}
