package net.interstellarai.unreminder.service.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    private val context: Context
) {
    companion object {
        const val EXTRA_TRIGGER_ID = "trigger_id"
        private const val TAG = "AlarmScheduler"
    }

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleExactAlarm(triggerId: Long, fireAt: Instant): Boolean {
        if (!alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Cannot schedule exact alarms — permission not granted")
            return false
        }

        val pendingIntent = createPendingIntent(triggerId)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            fireAt.toEpochMilli(),
            pendingIntent
        )
        return true
    }

    fun cancelAlarm(triggerId: Long) {
        val pendingIntent = createPendingIntent(triggerId)
        alarmManager.cancel(pendingIntent)
    }

    fun cancelAllAlarms(triggerIds: List<Long>) {
        triggerIds.forEach { cancelAlarm(it) }
    }

    private fun createPendingIntent(triggerId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_TRIGGER_ID, triggerId)
        }
        return PendingIntent.getBroadcast(
            context,
            triggerId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
