package net.interstellarai.unreminder.service.geofence

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import io.sentry.Sentry
import io.sentry.SentryLevel
import net.interstellarai.unreminder.widget.WidgetRefresher
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

    @Inject
    lateinit var widgetRefresher: WidgetRefresher

    // No goAsync/DB/network per geofence — ANR-prone in onReceive otherwise (see #137).
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.e(TAG, "Geofence error: code=${event.errorCode}")
            Sentry.captureMessage("Geofence error") { scope ->
                scope.setTag("component", "geofence")
                scope.setTag("error_code", event.errorCode.toString())
                scope.level = SentryLevel.ERROR
            }
            return
        }

        val transition = event.geofenceTransition
        val triggeringIds = event.triggeringGeofences.orEmpty().mapNotNull { it.requestId.toLongOrNull() }

        var changed = false
        for (locationId in triggeringIds) {
            when (transition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> {
                    geofenceManager.addLocationId(locationId, LocationSetChangeCause.ENTER)
                    changed = true
                }
                Geofence.GEOFENCE_TRANSITION_EXIT -> {
                    geofenceManager.removeLocationId(locationId, LocationSetChangeCause.EXIT)
                    changed = true
                }
            }
        }
        // Reported unconditionally: a week with zero of these is the finding that separates
        // "registered but never fires" from "fires but the state is mishandled".
        Sentry.captureMessage("Geofence transition") { scope ->
            scope.setTag("component", "geofence")
            scope.setTag("transition", transitionName(transition))
            scope.setExtra("location_ids", triggeringIds.toString())
            scope.setExtra("resulting_set_size", geofenceManager.currentLocationIds.value.size.toString())
            scope.level = SentryLevel.INFO
        }
        if (changed) widgetRefresher.refresh()
    }

    private fun transitionName(transition: Int): String = when (transition) {
        Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTER"
        Geofence.GEOFENCE_TRANSITION_EXIT -> "EXIT"
        Geofence.GEOFENCE_TRANSITION_DWELL -> "DWELL"
        else -> "UNKNOWN($transition)"
    }
}
