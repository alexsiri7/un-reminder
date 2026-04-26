package net.interstellarai.unreminder.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val workManager = WorkManager.getInstance(context)

        // Re-register geofences
        workManager.enqueue(
            OneTimeWorkRequestBuilder<BootReschedulerWorker>().build()
        )

        // Re-enqueue random interval worker
        RandomIntervalWorker.ensureEnqueued(context)
    }
}
