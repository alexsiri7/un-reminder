package net.interstellarai.unreminder.ui.settings

import com.google.android.gms.location.GeofenceStatusCodes
import net.interstellarai.unreminder.service.geofence.GeofenceRegistration
import net.interstellarai.unreminder.service.geofence.LocationSettingsCheck
import net.interstellarai.unreminder.service.geofence.RegistrationHealth

/** What Settings says about location tracking: healthy, a named fault with what to do, or neutral. */
sealed interface LocationTrackingStatus {
    val label: String
    val advice: String?
    val isFault: Boolean get() = advice != null

    data object Checking : LocationTrackingStatus {
        override val label = "checking…"
        override val advice = null
    }

    data object NoLocations : LocationTrackingStatus {
        override val label = "no saved locations"
        override val advice = null
    }

    data object Healthy : LocationTrackingStatus {
        override val label = "healthy"
        override val advice = null
    }

    data object BackgroundLocationMissing : LocationTrackingStatus {
        override val label = "background location not granted"
        override val advice =
            "Allow location “all the time” in system settings; the in-app prompt cannot grant it."
    }

    data object LocationSettingsOff : LocationTrackingStatus {
        override val label = "system location accuracy off"
        override val advice = "Turn on Location and Google Location Accuracy in system settings."
    }

    data object BatteryRestricted : LocationTrackingStatus {
        override val label = "battery usage restricted"
        override val advice = "Set this app’s battery usage to unrestricted in system settings."
    }

    data class RegistrationFailed(val statusLabel: String) : LocationTrackingStatus {
        override val label = "registration failed"
        override val advice = "Some geofences did not register ($statusLabel). Reopen the app to retry."
    }

    data object LocationSettingsUnverified : LocationTrackingStatus {
        override val label = "location settings unverified"
        override val advice =
            "Could not confirm Location and Google Location Accuracy are on. Reopen the app to retry."
    }

    companion object {
        fun of(health: RegistrationHealth?, backgroundRestricted: Boolean): LocationTrackingStatus {
            if (health == null) return Checking
            if (health.savedCount == 0) return NoLocations
            if (!health.backgroundLocationGranted) return BackgroundLocationMissing
            if (health.locationEnabled == false ||
                health.locationSettings is LocationSettingsCheck.Unavailable ||
                health.lastFailure == GeofenceRegistration.Rejected(GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE)
            ) {
                return LocationSettingsOff
            }
            health.lastFailure?.let { return RegistrationFailed(it.statusLabel) }
            if (backgroundRestricted) return BatteryRestricted
            return when (health.locationSettings) {
                LocationSettingsCheck.Available -> Healthy
                is LocationSettingsCheck.Unavailable -> LocationSettingsOff
                LocationSettingsCheck.TimedOut, is LocationSettingsCheck.Failed -> LocationSettingsUnverified
            }
        }
    }
}
