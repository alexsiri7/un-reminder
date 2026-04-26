package net.interstellarai.unreminder.service.geofence

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceBroadcastRcvr"

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

    // Keep all work O(1) in-memory — no goAsync/DB/network. ANR-prone otherwise (see #137).
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.e(TAG, "Geofence error: code=${event.errorCode}")
            return
        }

        val transition = event.geofenceTransition
        val triggeringGeofences = event.triggeringGeofences ?: return

        for (geofence in triggeringGeofences) {
            val locationId = geofence.requestId.toLongOrNull() ?: continue
            when (transition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> {
                    geofenceManager.addLocationId(locationId)
                }
                Geofence.GEOFENCE_TRANSITION_EXIT -> {
                    geofenceManager.removeLocationId(locationId)
                }
            }
        }
    }
}
