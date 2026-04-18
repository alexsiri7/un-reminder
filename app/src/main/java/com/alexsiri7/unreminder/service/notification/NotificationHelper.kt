package com.alexsiri7.unreminder.service.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.alexsiri7.unreminder.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "un_reminder_triggers"
        const val CHANNEL_NAME = "Habit Triggers"
        const val EXTRA_TRIGGER_ID = "trigger_id"
        const val EXTRA_ACTION = "action"
        const val ACTION_COMPLETED_FULL = "COMPLETED_FULL"
        const val ACTION_COMPLETED_LOW_FLOOR = "COMPLETED_LOW_FLOOR"
        const val ACTION_DISMISSED = "DISMISSED"
        const val CHANNEL_ID_SYSTEM = "un_reminder_system"
        const val CHANNEL_NAME_SYSTEM = "Habit Status"
        // Paused-habit notifications use habitId as offset.
        // Base chosen well above realistic trigger ID values to avoid collisions.
        const val NOTIFICATION_ID_PAUSED_BASE = 900_000L
    }

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Stochastic habit nudges"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val systemChannel = NotificationChannel(
            CHANNEL_ID_SYSTEM,
            CHANNEL_NAME_SYSTEM,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Habit lifecycle status updates"
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(systemChannel)
    }

    fun postTriggerNotification(triggerId: Long, promptText: String, habitName: String) {
        val fullIntent = createActionIntent(triggerId, ACTION_COMPLETED_FULL, 0)
        val lowFloorIntent = createActionIntent(triggerId, ACTION_COMPLETED_LOW_FLOOR, 1)
        val dismissIntent = createActionIntent(triggerId, ACTION_DISMISSED, 2)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(habitName)
            .setContentText(promptText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(promptText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "Full version", fullIntent)
            .addAction(0, "Low-floor", lowFloorIntent)
            .addAction(0, "Dismiss", dismissIntent)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(triggerId.toInt(), notification)
    }

    fun postHabitPausedNotification(habitId: Long, habitName: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SYSTEM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Paused $habitName")
            .setContentText("Rewrite its low-floor description to re-activate.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify((NOTIFICATION_ID_PAUSED_BASE + habitId).toInt(), notification)
    }

    private fun createActionIntent(triggerId: Long, action: String, requestCodeOffset: Int): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            putExtra(EXTRA_TRIGGER_ID, triggerId)
            putExtra(EXTRA_ACTION, action)
        }
        return PendingIntent.getBroadcast(
            context,
            triggerId.toInt() * 3 + requestCodeOffset,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
