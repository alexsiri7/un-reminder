package net.interstellarai.unreminder.service.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import net.interstellarai.unreminder.service.trigger.TriggerPipeline
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var triggerPipeline: TriggerPipeline

    override fun onReceive(context: Context, intent: Intent) {
        val triggerId = intent.getLongExtra(AlarmScheduler.EXTRA_TRIGGER_ID, -1)
        if (triggerId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                triggerPipeline.execute(triggerId)
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Pipeline execution failed for trigger=$triggerId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
