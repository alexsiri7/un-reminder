package net.interstellarai.unreminder.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val workManager = WorkManager.getInstance(context)

        // Re-register geofences
        workManager.enqueue(
            OneTimeWorkRequestBuilder<BootReschedulerWorker>().build()
        )

        // Re-enqueue random interval worker
        workManager.enqueueUniqueWork(
            RandomIntervalWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RandomIntervalWorker>()
                .setInitialDelay(RandomIntervalWorker.MIN_DELAY_MINUTES, TimeUnit.MINUTES)
                .build()
        )
    }
}
