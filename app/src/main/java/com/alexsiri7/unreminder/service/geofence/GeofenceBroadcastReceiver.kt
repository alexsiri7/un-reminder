package com.alexsiri7.unreminder.service.geofence

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.alexsiri7.unreminder.data.db.TriggerEntity
import com.alexsiri7.unreminder.data.repository.TriggerRepository
import com.alexsiri7.unreminder.domain.model.TriggerStatus
import com.alexsiri7.unreminder.service.alarm.AlarmScheduler
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceBroadcastRcvr"
        private const val DEBOUNCE_MS = 30 * 60 * 1000L // 30 minutes
        private const val ARRIVAL_DELAY_MS = 5 * 60 * 1000L // 5 minutes
        private const val PREFS_NAME = "geofence_debounce"

        fun getPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }

    @Inject
    lateinit var geofenceManager: GeofenceManager

    @Inject
    lateinit var triggerRepository: TriggerRepository

    @Inject
    lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.e(TAG, "Geofence error: code=${event.errorCode}")
            return
        }

        val transition = event.geofenceTransition
        val triggeringGeofences = event.triggeringGeofences ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (geofence in triggeringGeofences) {
                    val locationId = geofence.requestId.toLongOrNull() ?: continue
                    when (transition) {
                        Geofence.GEOFENCE_TRANSITION_ENTER -> {
                            geofenceManager.addLocationId(locationId)

                            if (isDebounced(context, geofence.requestId)) continue

                            recordDebounce(context, geofence.requestId)

                            val fireAt = Instant.now().plusMillis(ARRIVAL_DELAY_MS)
                            val triggerId = triggerRepository.insert(
                                TriggerEntity(
                                    scheduledAt = fireAt,
                                    status = TriggerStatus.SCHEDULED
                                )
                            )
                            alarmScheduler.scheduleExactAlarm(triggerId, fireAt)
                        }
                        Geofence.GEOFENCE_TRANSITION_EXIT -> {
                            geofenceManager.removeLocationId(locationId)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun isDebounced(context: Context, label: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastArrival = prefs.getLong(label, 0)
        return System.currentTimeMillis() - lastArrival < DEBOUNCE_MS
    }

    private fun recordDebounce(context: Context, label: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(label, System.currentTimeMillis()).apply()
    }
}
