package com.alexsiri7.unreminder.service.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alexsiri7.unreminder.data.repository.TriggerRepository
import com.alexsiri7.unreminder.domain.model.TriggerStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var triggerRepository: TriggerRepository

    override fun onReceive(context: Context, intent: Intent) {
        val triggerId = intent.getLongExtra(NotificationHelper.EXTRA_TRIGGER_ID, -1)
        val action = intent.getStringExtra(NotificationHelper.EXTRA_ACTION) ?: return

        if (triggerId == -1L) return

        val status = when (action) {
            NotificationHelper.ACTION_COMPLETED_FULL -> TriggerStatus.COMPLETED_FULL
            NotificationHelper.ACTION_COMPLETED_LOW_FLOOR -> TriggerStatus.COMPLETED_LOW_FLOOR
            NotificationHelper.ACTION_DISMISSED -> TriggerStatus.DISMISSED
            else -> return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                triggerRepository.updateOutcome(triggerId, status)
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.cancel(triggerId.toInt())
            } finally {
                pendingResult.finish()
            }
        }
    }
}
