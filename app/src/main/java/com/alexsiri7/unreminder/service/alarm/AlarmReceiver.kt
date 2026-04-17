package com.alexsiri7.unreminder.service.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alexsiri7.unreminder.service.trigger.TriggerPipeline
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
            } finally {
                pendingResult.finish()
            }
        }
    }
}
