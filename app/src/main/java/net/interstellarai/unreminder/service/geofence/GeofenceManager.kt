package net.interstellarai.unreminder.service.geofence

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import net.interstellarai.unreminder.data.db.LocationEntity
import net.interstellarai.unreminder.data.repository.LocationRepository
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Why [GeofenceManager.currentLocationIds] changed; recorded on every mutation's breadcrumb. */
enum class LocationSetChangeCause { ENTER, EXIT, RESTORE, MANUAL }

/** Outcome of one `addGeofences` call, as reported in the per-launch registration summary. */
sealed interface GeofenceRegistration {
    data object Registered : GeofenceRegistration
    data object PermissionMissing : GeofenceRegistration
    data object TimedOut : GeofenceRegistration
    data class Rejected(val statusCode: Int) : GeofenceRegistration
    data class Failed(val cause: Throwable) : GeofenceRegistration

    val statusLabel: String
        get() = when (this) {
            Registered -> "REGISTERED"
            PermissionMissing -> "PERMISSION_MISSING"
            TimedOut -> "TIMEOUT"
            is Rejected -> "${GeofenceStatusCodes.getStatusCodeString(statusCode)}($statusCode)"
            is Failed -> cause.javaClass.simpleName
        }
}

@Singleton
class GeofenceManager @Inject constructor(
    private val context: Context,
    private val locationRepository: LocationRepository,
    private val geofencingClient: GeofencingClient,
    private val settingsClient: SettingsClient,
) {
    companion object {
        private const val TAG = "GeofenceManager"
        private const val PREFS_NAME = "geofence_prefs"
        private const val KEY_LOCATION_IDS = "current_location_ids"

        // Below ~100 m ordinary GPS drift makes Android either never report an entry or
        // flap enter/exit while the user sits still, so smaller fences look precise but
        // never fire reliably.
        const val MIN_RADIUS_M = 100f

        // Both app start and the boot worker run other scheduling after registration, so a
        // Play Services task that never settles must not be allowed to hold them hostage.
        private const val PLAY_SERVICES_TIMEOUT_MS = 30_000L
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val locationIdLock = Any()

    private val _currentLocationIds = MutableStateFlow<Set<Long>>(restoreLocationIds())
    val currentLocationIds: StateFlow<Set<Long>> = _currentLocationIds.asStateFlow()

    private fun restoreLocationIds(): Set<Long> {
        val stored = prefs.getStringSet(KEY_LOCATION_IDS, emptySet()) ?: emptySet()
        val restored = stored.mapNotNull { it.toLongOrNull() }.toSet()
        recordLocationSetChange(emptySet(), restored, LocationSetChangeCause.RESTORE)
        return restored
    }

    private fun persistLocationIds(ids: Set<Long>) {
        prefs.edit().putStringSet(KEY_LOCATION_IDS, ids.map { it.toString() }.toSet()).apply()
    }

    private fun recordLocationSetChange(old: Set<Long>, new: Set<Long>, cause: LocationSetChangeCause) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "geofence"
            message = "currentLocationIds changed"
            level = SentryLevel.INFO
            setData("cause", cause.name.lowercase())
            setData("old_ids", old.sorted().toString())
            setData("new_ids", new.sorted().toString())
        })
    }

    fun addLocationId(id: Long, cause: LocationSetChangeCause) = synchronized(locationIdLock) {
        val current = _currentLocationIds.value
        if (id in current) return@synchronized
        val updated = current + id
        _currentLocationIds.value = updated
        persistLocationIds(updated)
        recordLocationSetChange(current, updated, cause)
    }

    fun removeLocationId(id: Long, cause: LocationSetChangeCause) = synchronized(locationIdLock) {
        val current = _currentLocationIds.value
        if (id !in current) return@synchronized
        val updated = current - id
        _currentLocationIds.value = updated
        persistLocationIds(updated)
        recordLocationSetChange(current, updated, cause)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasLocationPermissions(): Boolean =
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
            hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    suspend fun registerGeofence(id: Long, name: String, lat: Double, lng: Double, radiusM: Float): GeofenceRegistration {
        val outcome = if (hasLocationPermissions()) {
            addGeofence(id, lat, lng, radiusM)
        } else {
            GeofenceRegistration.PermissionMissing
        }
        when (outcome) {
            GeofenceRegistration.Registered -> Log.d(TAG, "Geofence registered: id=$id name=$name")
            else -> reportRegistrationFailure(id, name, outcome)
        }
        return outcome
    }

    private suspend fun addGeofence(id: Long, lat: Double, lng: Double, radiusM: Float): GeofenceRegistration {
        val geofence = Geofence.Builder()
            .setRequestId(id.toString())
            .setCircularRegion(lat, lng, radiusM)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        return try {
            @Suppress("MissingPermission")
            val task = geofencingClient.addGeofences(request, GeofenceBroadcastReceiver.getPendingIntent(context))
            withTimeoutOrNull(PLAY_SERVICES_TIMEOUT_MS) { task.await(); GeofenceRegistration.Registered }
                ?: GeofenceRegistration.TimedOut
        } catch (e: ApiException) {
            GeofenceRegistration.Rejected(e.statusCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GeofenceRegistration.Failed(e)
        }
    }

    private fun reportRegistrationFailure(id: Long, name: String, outcome: GeofenceRegistration) {
        Log.e(TAG, "Geofence registration failed: id=$id status=${outcome.statusLabel}")
        Sentry.captureMessage("Geofence registration failed") { scope ->
            scope.setTag("component", "geofence")
            scope.setExtra("location_id", id.toString())
            scope.setExtra("location_name", name)
            scope.setExtra("status", outcome.statusLabel)
            scope.level = SentryLevel.WARNING
        }
    }

    fun removeGeofence(id: Long) {
        geofencingClient.removeGeofences(listOf(id.toString()))
        // Keep persisted/in-memory set in sync; Android may not deliver an EXIT for an
        // unregistered fence, leaving stragglers that drift monotonically over time.
        removeLocationId(id, LocationSetChangeCause.MANUAL)
    }

    suspend fun registerAllFromDb() {
        val outcomes = locationRepository.getAllList().map { stored ->
            val loc = raiseToMinimumRadius(stored)
            loc.id to registerGeofence(loc.id, loc.name, loc.lat, loc.lng, loc.radiusM)
        }
        reportRegistrationSummary(outcomes)
    }

    private suspend fun reportRegistrationSummary(outcomes: List<Pair<Long, GeofenceRegistration>>) {
        val failures = outcomes.filter { (_, outcome) -> outcome != GeofenceRegistration.Registered }
        val locationEnabled = context.getSystemService(LocationManager::class.java)?.isLocationEnabled
        val locationSettings = checkLocationSettings()
        Sentry.captureMessage("Geofence registration summary") { scope ->
            scope.setTag("component", "geofence")
            scope.setExtra("saved_count", outcomes.size.toString())
            scope.setExtra("registered_count", (outcomes.size - failures.size).toString())
            scope.setExtra("failed_count", failures.size.toString())
            scope.setExtra(
                "failures",
                failures.joinToString { (id, outcome) -> "id=$id status=${outcome.statusLabel}" }
            )
            scope.setExtra("fine_location_granted", hasPermission(Manifest.permission.ACCESS_FINE_LOCATION).toString())
            scope.setExtra("background_location_granted", hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION).toString())
            scope.setExtra("location_enabled", locationEnabled.toString())
            scope.setExtra("location_settings", locationSettings)
            scope.level = if (failures.isEmpty()) SentryLevel.INFO else SentryLevel.WARNING
        }
    }

    // Google Location Accuracy switched off is invisible to the permission checks but makes
    // every registration fail with GEOFENCE_NOT_AVAILABLE; a balanced-power settings check
    // is the one API that reports it.
    private suspend fun checkLocationSettings(): String {
        val request = LocationSettingsRequest.Builder()
            .addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 0L).build())
            .build()
        val statusCode = try {
            withTimeoutOrNull(PLAY_SERVICES_TIMEOUT_MS) {
                settingsClient.checkLocationSettings(request).await()
                LocationSettingsStatusCodes.SUCCESS
            } ?: return "TIMEOUT"
        } catch (e: ApiException) {
            e.statusCode
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return e.javaClass.simpleName
        }
        return "${settingsStatusName(statusCode)}($statusCode)"
    }

    // LocationSettingsStatusCodes.getStatusCodeString reports 8502 as "unknown status code", and
    // that is the one code this check exists to detect.
    private fun settingsStatusName(statusCode: Int): String = when (statusCode) {
        LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> "SETTINGS_CHANGE_UNAVAILABLE"
        else -> LocationSettingsStatusCodes.getStatusCodeString(statusCode)
    }

    private suspend fun raiseToMinimumRadius(loc: LocationEntity): LocationEntity {
        if (loc.radiusM >= MIN_RADIUS_M) return loc
        val raised = loc.copy(radiusM = MIN_RADIUS_M)
        locationRepository.update(raised)
        Log.i(TAG, "Raised geofence radius to minimum: id=${loc.id} name=${loc.name} from=${loc.radiusM}")
        Sentry.captureMessage("Geofence radius raised to minimum") { scope ->
            scope.setTag("component", "geofence")
            scope.setExtra("location_id", loc.id.toString())
            scope.setExtra("location_name", loc.name)
            scope.setExtra("old_radius_m", loc.radiusM.toString())
            scope.setExtra("new_radius_m", MIN_RADIUS_M.toString())
            scope.level = SentryLevel.INFO
        }
        return raised
    }
}
