package net.interstellarai.unreminder.service.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import net.interstellarai.unreminder.data.repository.HabitRepository
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
    lateinit var habitRepository: HabitRepository

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
            val notifManager = context.getSystemService(NotificationManager::class.java)
            try {
                triggerRepository.updateOutcome(triggerId, status)
                if (status == TriggerStatus.DISMISSED) {
                    dismissalTracker.onDismissed(triggerId)
                }
                if (status == TriggerStatus.COMPLETED) {
                    val trigger = triggerRepository.getById(triggerId)
                    val habitId = trigger?.habitId
                    when {
                        trigger == null ->
                            Log.w(TAG, "COMPLETED: trigger $triggerId not found — promotion skipped")
                        habitId == null ->
                            Log.w(TAG, "COMPLETED: trigger $triggerId has no habitId — promotion skipped")
                        else -> {
                            val habit = habitRepository.getByIdOnce(habitId)
                            if (habit == null) {
                                Log.w(TAG, "COMPLETED: habit $habitId not found — promotion skipped")
                            } else {
                                try {
                                    dedicationLevelManager.maybePromote(habit)
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Log.w(TAG, "maybePromote failed — promotion skipped", e)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "onReceive: failed for trigger=$triggerId action=$action", e)
            } finally {
                notifManager.cancel(triggerId.toInt())
                pendingResult.finish()
            }
        }
    }
}
