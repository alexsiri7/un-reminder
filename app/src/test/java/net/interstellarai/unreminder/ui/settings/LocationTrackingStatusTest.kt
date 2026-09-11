package net.interstellarai.unreminder.ui.settings

import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.LocationSettingsStatusCodes
import net.interstellarai.unreminder.service.geofence.GeofenceRegistration
import net.interstellarai.unreminder.service.geofence.LocationSettingsCheck
import net.interstellarai.unreminder.service.geofence.RegistrationHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LocationTrackingStatusTest {

    private val registered = listOf(1L to GeofenceRegistration.Registered)
    private val notAvailable = GeofenceRegistration.Rejected(GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE)

    private fun health(
        outcomes: List<Pair<Long, GeofenceRegistration>> = registered,
        backgroundLocationGranted: Boolean = true,
        locationEnabled: Boolean? = true,
        locationSettings: LocationSettingsCheck = LocationSettingsCheck.Available,
    ) = RegistrationHealth(
        outcomes = outcomes,
        fineLocationGranted = true,
        backgroundLocationGranted = backgroundLocationGranted,
        locationEnabled = locationEnabled,
        locationSettings = locationSettings,
        checkedAt = Instant.EPOCH,
    )

    private fun statusOf(health: RegistrationHealth?, backgroundRestricted: Boolean = false) =
        LocationTrackingStatus.of(health, backgroundRestricted)

    @Test
    fun `nothing reported yet reads as checking, not healthy`() {
        val status = statusOf(null)
        assertEquals(LocationTrackingStatus.Checking, status)
        assertFalse(status.isFault)
    }

    @Test
    fun `no saved locations is neutral even when everything else is broken`() {
        val status = statusOf(
            health(
                outcomes = emptyList(),
                backgroundLocationGranted = false,
                locationEnabled = false,
                locationSettings = LocationSettingsCheck.Unavailable(LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE),
            ),
            backgroundRestricted = true,
        )
        assertEquals(LocationTrackingStatus.NoLocations, status)
        assertFalse(status.isFault)
        assertNull(status.advice)
    }

    @Test
    fun `missing background location is named before any platform fault`() {
        val status = statusOf(
            health(
                outcomes = listOf(1L to GeofenceRegistration.PermissionMissing),
                backgroundLocationGranted = false,
                locationSettings = LocationSettingsCheck.Unavailable(LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE),
            )
        )
        assertEquals(LocationTrackingStatus.BackgroundLocationMissing, status)
        assertTrue(status.advice!!.contains("system settings"))
    }

    @Test
    fun `location accuracy off is detected from the settings check`() {
        val status = statusOf(
            health(
                outcomes = listOf(1L to notAvailable),
                locationSettings = LocationSettingsCheck.Unavailable(LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE),
            )
        )
        assertEquals(LocationTrackingStatus.LocationSettingsOff, status)
    }

    @Test
    fun `location accuracy off is still detected when only the rejection code says so`() {
        assertEquals(
            LocationTrackingStatus.LocationSettingsOff,
            statusOf(health(outcomes = listOf(1L to notAvailable), locationSettings = LocationSettingsCheck.TimedOut)),
        )
        assertEquals(
            LocationTrackingStatus.LocationSettingsOff,
            statusOf(health(locationEnabled = false)),
        )
    }

    @Test
    fun `battery restriction is a fault only once registration itself is fine`() {
        assertEquals(LocationTrackingStatus.BatteryRestricted, statusOf(health(), backgroundRestricted = true))
        assertEquals(
            LocationTrackingStatus.RegistrationFailed("TIMEOUT"),
            statusOf(health(outcomes = listOf(1L to GeofenceRegistration.TimedOut)), backgroundRestricted = true),
        )
    }

    @Test
    fun `all fences registered with nothing restricted reads healthy`() {
        val status = statusOf(health())
        assertEquals(LocationTrackingStatus.Healthy, status)
        assertFalse(status.isFault)
    }

    @Test
    fun `every fault has its own plain-language advice rather than a status code`() {
        val faults = listOf(
            LocationTrackingStatus.BackgroundLocationMissing,
            LocationTrackingStatus.LocationSettingsOff,
            LocationTrackingStatus.BatteryRestricted,
        )
        assertTrue(faults.all { it.isFault })
        assertEquals(faults.size, faults.map { it.label }.toSet().size)
        assertEquals(faults.size, faults.map { it.advice }.toSet().size)
        assertTrue(faults.none { Regex("""\(\d+\)""").containsMatchIn(it.label + it.advice) })
    }
}
