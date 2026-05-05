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

        // Kick-start the random-interval chain after reboot. KEEP semantics make this
        // a no-op when the chain is still alive, but recover quickly when it isn't.
        RandomIntervalWorker.enqueueInitial(workManager)
    }
}
