package net.interstellarai.unreminder.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class BootReschedulerWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val geofenceManager: GeofenceManager
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "boot_rescheduler"
    }

    override suspend fun doWork(): Result {
        geofenceManager.registerAllFromDb()
        return Result.success()
    }
}
