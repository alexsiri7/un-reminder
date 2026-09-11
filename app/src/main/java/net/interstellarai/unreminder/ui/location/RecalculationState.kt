package net.interstellarai.unreminder.ui.location

import net.interstellarai.unreminder.service.geofence.Reconciliation

/** What the locations screen says about the last "recalculate now" press. */
sealed interface RecalculationState {
    data object Idle : RecalculationState
    data object Running : RecalculationState
    data class Failed(val label: String, val advice: String) : RecalculationState

    companion object {
        // A success needs no banner: the rows and their checked-at times update in place.
        fun of(outcome: Reconciliation): RecalculationState = when (outcome) {
            Reconciliation.Reconciled -> Idle
            Reconciliation.PermissionMissing -> Failed(
                label = "location permission not granted",
                advice = "Allow location access in system settings, then try again.",
            )
            Reconciliation.LocationDisabled -> Failed(
                label = "system location is off",
                advice = "Turn on Location in system settings, then try again.",
            )
            Reconciliation.NoFix -> Failed(
                label = "no location fix in time",
                advice = "Move somewhere with a clearer view of the sky and try again.",
            )
            is Reconciliation.Failed -> Failed(
                label = "could not check your location",
                advice = "Something went wrong reading your position. Try again.",
            )
            // reconcileNow waits for the lock and ignores the debounce, so it never skips; if that
            // ever stops holding the screen should say something rather than appear to do nothing.
            Reconciliation.Skipped -> Failed(
                label = "another location check was already running",
                advice = "Try again in a moment.",
            )
        }
    }
}
