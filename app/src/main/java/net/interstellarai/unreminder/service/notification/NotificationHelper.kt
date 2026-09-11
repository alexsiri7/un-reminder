package net.interstellarai.unreminder.service.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import net.interstellarai.unreminder.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    private val context: Context,
    private val emojiRotator: EmojiRotator
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    companion object {
        const val CHANNEL_ID = "un_reminder_triggers"
        const val CHANNEL_NAME = "Habit Triggers"
        const val EXTRA_TRIGGER_ID = "trigger_id"
        const val EXTRA_ACTION = "action"
        const val ACTION_COMPLETED = "COMPLETED"
        const val ACTION_DISMISSED = "DISMISSED"
        const val CHANNEL_ID_SYSTEM = "un_reminder_system"
        const val CHANNEL_NAME_SYSTEM = "Habit Status"
        const val CHANNEL_ID_INVITATION = "un_reminder_invitations"
        const val CHANNEL_NAME_INVITATION = "Evening Invitations"
        const val EXTRA_OPEN_TIMER = "open_timer"
        const val EXTRA_OPEN_DETAIL = "open_detail"
        const val EXTRA_OPEN_NOW = "open_now"
        // Paused-habit notifications use habitId as offset.
        // Base chosen well above realistic trigger ID values to avoid collisions.
        const val NOTIFICATION_ID_PAUSED_BASE = 900_000L
        // Content intent base — above the * 3 action-intent range and PAUSED_BASE.
        const val NOTIFICATION_CONTENT_BASE = 2_000_000L
        const val NOTIFICATION_DETAIL_BASE = 3_000_000L
        // Single fixed id: there is at most one evening invitation, and it is never
        // keyed by a trigger. Kept above DETAIL_BASE so it can't collide with per-trigger codes.
        const val NOTIFICATION_ID_EVENING_INVITATION = 4_000_000L
    }

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Stochastic habit nudges"
        }
        notificationManager.createNotificationChannel(channel)

        val systemChannel = NotificationChannel(
            CHANNEL_ID_SYSTEM,
            CHANNEL_NAME_SYSTEM,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Habit lifecycle status updates"
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(systemChannel)

        val invitationChannel = NotificationChannel(
            CHANNEL_ID_INVITATION,
            CHANNEL_NAME_INVITATION,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "An evening nudge on days with nothing done yet"
        }
        notificationManager.createNotificationChannel(invitationChannel)
    }

    fun postTriggerNotification(
        triggerId: Long,
        promptText: String,
        habitName: String,
        actionUrl: String? = null,
    ) {
        val emoji = emojiRotator.pick(triggerId)
        val completedIntent = createActionIntent(triggerId, ACTION_COMPLETED, 0)
        val dismissIntent = createActionIntent(triggerId, ACTION_DISMISSED, 1)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$emoji $habitName")
            .setContentText(promptText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(promptText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "Did it", completedIntent)
            .addAction(0, "Dismiss", dismissIntent)

        if (actionUrl != null && actionUrl.startsWith("https://")) {
            val watchIntent = PendingIntent.getActivity(
                context,
                (triggerId * 3 + 2).toRequestCode(),
                Intent(Intent.ACTION_VIEW, Uri.parse(actionUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, "Watch", watchIntent)
        }

        // Content intent: opens ReminderDetailScreen when user taps notification body
        val detailIntent = Intent(context, net.interstellarai.unreminder.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TRIGGER_ID, triggerId)
            putExtra(EXTRA_OPEN_DETAIL, true)
        }
        val detailPendingIntent = PendingIntent.getActivity(
            context,
            (NOTIFICATION_DETAIL_BASE + triggerId).toRequestCode(),
            detailIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.setContentIntent(detailPendingIntent)

        notificationManager.notify(triggerId.toRequestCode(), builder.build())
    }

    fun postHabitPausedNotification(habitId: Long, habitName: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SYSTEM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Paused $habitName")
            .setContentText("Tap to re-activate or lower its dedication level.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        notificationManager.notify((NOTIFICATION_ID_PAUSED_BASE + habitId).toRequestCode(), notification)
    }

    fun postEveningInvitation(body: String) {
        val openNowIntent = Intent(context, net.interstellarai.unreminder.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_NOW, true)
        }
        val openNow = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_EVENING_INVITATION.toRequestCode(),
            openNowIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notTonight = PendingIntent.getBroadcast(
            context,
            (NOTIFICATION_ID_EVENING_INVITATION + 1).toRequestCode(),
            Intent(context, EveningInvitationDismissReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // No delete intent: swiping this away must stay a no-op for triggers and habits.
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_INVITATION)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(EveningInvitationWording.TITLE)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openNow)
            .addAction(0, "Show me", openNow)
            .addAction(0, "Not tonight", notTonight)
            .build()
        notificationManager.notify(NOTIFICATION_ID_EVENING_INVITATION.toRequestCode(), notification)
    }

    fun cancelNotification(triggerId: Long) {
        notificationManager.cancel(triggerId.toRequestCode())
    }

    fun cancelEveningInvitation() {
        notificationManager.cancel(NOTIFICATION_ID_EVENING_INVITATION.toRequestCode())
    }

    private fun createActionIntent(triggerId: Long, action: String, requestCodeOffset: Int): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            putExtra(EXTRA_TRIGGER_ID, triggerId)
            putExtra(EXTRA_ACTION, action)
        }
        return PendingIntent.getBroadcast(
            context,
            (triggerId * 3 + requestCodeOffset).toRequestCode(), // * 3 = slots per trigger: 0=COMPLETED, 1=DISMISSED; slot 2=WATCH uses getActivity (see postTriggerNotification)
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
