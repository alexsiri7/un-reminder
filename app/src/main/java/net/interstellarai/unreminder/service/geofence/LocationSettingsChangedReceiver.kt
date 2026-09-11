package net.interstellarai.unreminder.service.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import androidx.core.content.ContextCompat

/**
 * Re-registers geofences when system Location or a location provider is toggled; Play Services
 * drops fences while the network provider is off and expects apps to re-register afterwards.
 */
class LocationSettingsChangedReceiver(private val geofenceManager: GeofenceManager) : BroadcastReceiver() {

    companion object {
        private val ACTIONS = setOf(LocationManager.MODE_CHANGED_ACTION, LocationManager.PROVIDERS_CHANGED_ACTION)

        // Context-registered because manifest receivers no longer get these implicit system
        // broadcasts. They are protected broadcasts only the system can send, so NOT_EXPORTED
        // costs nothing and mirrors BootReceiver's exported="false".
        fun register(context: Context, geofenceManager: GeofenceManager) {
            val filter = IntentFilter().apply { ACTIONS.forEach(::addAction) }
            ContextCompat.registerReceiver(
                context,
                LocationSettingsChangedReceiver(geofenceManager),
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        geofenceManager.refreshRegistration()
    }
}
