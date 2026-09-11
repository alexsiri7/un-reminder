package net.interstellarai.unreminder.service.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// "Not tonight" only clears the invitation. It is deliberately not routed through
// NotificationActionReceiver: this notification belongs to no trigger or habit, so
// nothing about dismissing it may touch trigger rows or dedication levels.
class EveningInvitationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(NotificationHelper.NOTIFICATION_ID_EVENING_INVITATION.toRequestCode())
    }
}
