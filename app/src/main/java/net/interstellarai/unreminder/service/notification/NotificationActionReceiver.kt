package net.interstellarai.unreminder.service.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.trigger.DismissalTracker
import net.interstellarai.unreminder.widget.WidgetRefresher
import dagger.hilt.android.AndroidEntryPoint
import io.sentry.Sentry
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
    lateinit var widgetRefresher: WidgetRefresher

    override fun onReceive(context: Context, intent: Intent) {
        val triggerId = intent.getLongExtra(NotificationHelper.EXTRA_TRIGGER_ID, -1)
        val action = intent.getStringExtra(NotificationHelper.EXTRA_ACTION) ?: return

        if (triggerId == -1L) return

        val status = when (action) {
            NotificationHelper.ACTION_COMPLETED -> TriggerStatus.COMPLETED
            NotificationHelper.ACTION_DISMISSED -> TriggerStatus.DISMISSED
            NotificationHelper.ACTION_LATER -> TriggerStatus.LATER
            else -> return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val manager = context.getSystemService(NotificationManager::class.java)
            try {
                if (!triggerRepository.recordOutcome(triggerId, status)) {
                    // A swipe can land between a tap and the cancel below (#291), delivering a
                    // second broadcast for the same trigger, and a later trigger can supersede
                    // this one (#369). Either way the outcome is already recorded and the
                    // repository declined the write.
                    Log.d(TAG, "onReceive: trigger=$triggerId already resolved, ignoring $action")
                    manager.cancel(triggerId.toRequestCode())
                    return@launch
                }
                when (status) {
                    TriggerStatus.COMPLETED -> dismissalTracker.onCompleted(triggerId)
                    TriggerStatus.DISMISSED -> dismissalTracker.onDismissed(triggerId)
                    // Wrong moment, not too big: Later never reaches the DismissalTracker (#370).
                    TriggerStatus.LATER -> {}
                    else -> {}
                }
                manager.cancel(triggerId.toRequestCode())
                widgetRefresher.refresh()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "onReceive: failed for trigger=$triggerId action=$action", e)
                Sentry.captureException(e) { scope ->
                    scope.setTag("component", "notification-action")
                    scope.setTag("trigger_id", triggerId.toString())
                    scope.setTag("action", action)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
