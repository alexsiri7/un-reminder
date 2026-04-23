package net.interstellarai.unreminder.service.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.trigger.DedicationLevelManager
import net.interstellarai.unreminder.service.trigger.DismissalTracker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotificationActionReceiver"
    }

    @Inject
    lateinit var triggerRepository: TriggerRepository

    @Inject
    lateinit var dismissalTracker: DismissalTracker

    @Inject
    lateinit var dedicationLevelManager: DedicationLevelManager

    override fun onReceive(context: Context, intent: Intent) {
        val triggerId = intent.getLongExtra(NotificationHelper.EXTRA_TRIGGER_ID, -1)
        val action = intent.getStringExtra(NotificationHelper.EXTRA_ACTION) ?: return

        if (triggerId == -1L) return

        val status = when (action) {
            NotificationHelper.ACTION_COMPLETED -> TriggerStatus.COMPLETED
            NotificationHelper.ACTION_DISMISSED -> TriggerStatus.DISMISSED
            else -> return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                triggerRepository.updateOutcome(triggerId, status)
                if (status == TriggerStatus.COMPLETED) {
                    val trigger = triggerRepository.getById(triggerId)
                    trigger?.habitId?.let { habitId ->
                        try {
                            dedicationLevelManager.maybePromote(habitId)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.w(TAG, "maybePromote failed for habit=$habitId — non-fatal", e)
                        }
                    }
                }
                if (status == TriggerStatus.DISMISSED) {
                    dismissalTracker.onDismissed(triggerId)
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.cancel(triggerId.toInt())
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "onReceive: failed for trigger=$triggerId action=$action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
