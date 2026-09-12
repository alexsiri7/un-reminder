package net.interstellarai.unreminder.service.geofence

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import net.interstellarai.unreminder.data.db.LocationEntity
import net.interstellarai.unreminder.data.repository.LocationRepository
import net.interstellarai.unreminder.di.ApplicationScope
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Why [GeofenceManager.currentLocationIds] changed; recorded on every mutation's breadcrumb. */
enum class LocationSetChangeCause { ENTER, EXIT, RESTORE, MANUAL, RECONCILE }

/** When and how a single location's inside/outside answer was last established. */
data class LocationCheck(
    val inside: Boolean,
    val at: Instant,
    val via: LocationSetChangeCause,
)

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
            is Failed -> LocationFaultLabel.of(cause)
        }
}

/** Outcome of one `removeGeofences` call; anything but [GeofenceRemoval.Removed] leaves the fence live. */
sealed interface GeofenceRemoval {
    data object Removed : GeofenceRemoval
    data object TimedOut : GeofenceRemoval
    data class Rejected(val statusCode: Int) : GeofenceRemoval
    data class Failed(val cause: Throwable) : GeofenceRemoval

    val statusLabel: String
        get() = when (this) {
            Removed -> "REMOVED"
            TimedOut -> "TIMEOUT"
            is Rejected -> "${GeofenceStatusCodes.getStatusCodeString(statusCode)}($statusCode)"
            is Failed -> LocationFaultLabel.of(cause)
        }
}

/** Result of the balanced-power settings check that accompanies every full registration. */
sealed interface LocationSettingsCheck {
    data object Available : LocationSettingsCheck
    data object TimedOut : LocationSettingsCheck
    data class Unavailable(val statusCode: Int) : LocationSettingsCheck
    data class Failed(val cause: Throwable) : LocationSettingsCheck

    val statusLabel: String
        get() = when (this) {
            Available -> statusLabelFor(LocationSettingsStatusCodes.SUCCESS)
            TimedOut -> "TIMEOUT"
            is Unavailable -> statusLabelFor(statusCode)
            is Failed -> LocationFaultLabel.of(cause)
        }

    companion object {
        // LocationSettingsStatusCodes.getStatusCodeString reports 8502 as "unknown status code",
        // and that is the one code this check exists to detect.
        private fun statusLabelFor(statusCode: Int): String {
            val name = when (statusCode) {
                LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> "SETTINGS_CHANGE_UNAVAILABLE"
                else -> LocationSettingsStatusCodes.getStatusCodeString(statusCode)
            }
            return "$name($statusCode)"
        }
    }
}

/**
 * Outcome of the last full registration ([GeofenceManager.registerAllFromDb] or
 * [GeofenceManager.refreshRegistration]). The Sentry summary and the Settings screen both read
 * this, so neither computes health on its own.
 */
data class RegistrationHealth(
    val outcomes: List<Pair<Long, GeofenceRegistration>>,
    val fineLocationGranted: Boolean,
    val backgroundLocationGranted: Boolean,
    val locationEnabled: Boolean?,
    val locationSettings: LocationSettingsCheck,
    val checkedAt: Instant,
) {
    val savedCount: Int get() = outcomes.size
    val failures: List<Pair<Long, GeofenceRegistration>>
        get() = outcomes.filter { (_, outcome) -> outcome != GeofenceRegistration.Registered }
    val registeredCount: Int get() = savedCount - failures.size
    val lastFailure: GeofenceRegistration? get() = failures.lastOrNull()?.second
}

@Singleton
class GeofenceManager @Inject constructor(
    private val context: Context,
    private val locationRepository: LocationRepository,
    private val geofencingClient: GeofencingClient,
    private val settingsClient: SettingsClient,
    @ApplicationScope private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "GeofenceManager"
        private const val PREFS_NAME = "geofence_prefs"
        private const val KEY_LOCATION_IDS = "current_location_ids"
        private const val KEY_LOCATION_CHECKS = "location_checks"

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

    private val restored = restoreState()

    private val _currentLocationIds = MutableStateFlow(restored.ids)
    val currentLocationIds: StateFlow<Set<Long>> = _currentLocationIds.asStateFlow()

    /**
     * When and how each location's inside/outside answer was last established. An id absent from
     * the map has never been established, which the locations screen shows as unknown rather than
     * as "outside". Every entry with `inside = true` has its id in [currentLocationIds]; an id in
     * [currentLocationIds] either has such an entry or, on an install upgraded from before checks
     * were persisted, no entry until the first reconciliation.
     */
    private val _locationChecks = MutableStateFlow(restored.checks)
    val locationChecks: StateFlow<Map<Long, LocationCheck>> = _locationChecks.asStateFlow()

    private val _registrationHealth = MutableStateFlow<RegistrationHealth?>(null)
    val registrationHealth: StateFlow<RegistrationHealth?> = _registrationHealth.asStateFlow()

    // Full registrations run in launch order so that a run started before a permission grant,
    // stalled on a Play Services task, cannot outlive a fresher run and overwrite its health
    // with the stale PermissionMissing outcome when it finally times out.
    private val registrationMutex = Mutex()
    private val refreshPending = AtomicBoolean(false)

    private class RestoredState(val ids: Set<Long>, val checks: Map<Long, LocationCheck>)

    /**
     * An install upgraded from before checks were persisted restores its ids with no checks at
     * all: stamping them as checked now would tell the user their locations were confirmed when
     * nothing was confirmed. They read as unknown until the first reconciliation.
     */
    private fun restoreState(): RestoredState {
        val storedIds = prefs.getStringSet(KEY_LOCATION_IDS, emptySet()) ?: emptySet()
        val ids = storedIds.mapNotNull { it.toLongOrNull() }.toSet()
        val storedChecks = prefs.getStringSet(KEY_LOCATION_CHECKS, emptySet()) ?: emptySet()
        val checks = storedChecks.mapNotNull { parseCheck(it) }.toMap()
        recordLocationSetChange(emptySet(), ids, LocationSetChangeCause.RESTORE)
        return RestoredState(ids, checks)
    }

    private fun parseCheck(entry: String): Pair<Long, LocationCheck>? {
        val parts = entry.split('|')
        if (parts.size != 4) return null
        val id = parts[0].toLongOrNull() ?: return null
        val inside = when (parts[1]) {
            "1" -> true
            "0" -> false
            else -> return null
        }
        val at = parts[2].toLongOrNull()?.let(Instant::ofEpochMilli) ?: return null
        val via = LocationSetChangeCause.entries.firstOrNull { it.name == parts[3] } ?: return null
        return id to LocationCheck(inside, at, via)
    }

    /** The only writer of either flow or either key after construction, so the two cannot drift apart. */
    private fun publish(ids: Set<Long>, checks: Map<Long, LocationCheck>) {
        _currentLocationIds.value = ids
        _locationChecks.value = checks
        prefs.edit()
            .putStringSet(KEY_LOCATION_IDS, ids.map { it.toString() }.toSet())
            .putStringSet(
                KEY_LOCATION_CHECKS,
                checks.map { (id, check) ->
                    "$id|${if (check.inside) 1 else 0}|${check.at.toEpochMilli()}|${check.via.name}"
                }.toSet(),
            )
            .apply()
    }

    // Persisted checks carry milliseconds, so a stamp kept at Instant.now()'s finer precision
    // would not survive the round trip unchanged.
    private fun checkedNow(): Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)

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

    fun addLocationId(id: Long, cause: LocationSetChangeCause) =
        recordTransition(id, inside = true, cause = cause)

    fun removeLocationId(id: Long, cause: LocationSetChangeCause) =
        recordTransition(id, inside = false, cause = cause)

    // A repeated ENTER, or an EXIT for a location already believed outside, leaves the set
    // alone but still establishes that location's answer, so the check is stamped either way
    // and only the breadcrumb is gated on the set actually changing.
    private fun recordTransition(id: Long, inside: Boolean, cause: LocationSetChangeCause) =
        synchronized(locationIdLock) {
            val current = _currentLocationIds.value
            val updated = if (inside) current + id else current - id
            publish(updated, _locationChecks.value + (id to LocationCheck(inside, checkedNow(), cause)))
            if (updated != current) recordLocationSetChange(current, updated, cause)
        }

    /**
     * Records a reconciliation against a real position fix in one atomic persist and emission —
     * composing add/remove calls would publish intermediate sets that were never true of the
     * device's position. Every location judged against the fix is stamped, including the ones
     * found outside, and checks for locations no longer judged are dropped. Taking one map of
     * every judgement rather than a set of ids plus a set of evaluated ones makes it impossible
     * to record a location as inside without also recording when that was established.
     */
    fun recordReconciliation(judgements: Map<Long, Boolean>) = synchronized(locationIdLock) {
        val current = _currentLocationIds.value
        val checkedAt = checkedNow()
        val updated = judgements.filterValues { it }.keys
        publish(
            updated,
            judgements.mapValues { (_, inside) ->
                LocationCheck(inside, checkedAt, LocationSetChangeCause.RECONCILE)
            },
        )
        if (updated != current) recordLocationSetChange(current, updated, LocationSetChangeCause.RECONCILE)
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

    /**
     * Waits for Play Services to apply the removal before publishing it, so a caller that
     * re-reads registration health next reports state the platform has actually reached.
     * The id is dropped from the set either way: its location is already gone, Android may
     * not deliver an EXIT for a fence it no longer knows, and nothing else would ever clear it.
     */
    suspend fun removeGeofence(id: Long, name: String) {
        val outcome = try {
            val task = geofencingClient.removeGeofences(listOf(id.toString()))
            withTimeoutOrNull(PLAY_SERVICES_TIMEOUT_MS) { task.await(); GeofenceRemoval.Removed }
                ?: GeofenceRemoval.TimedOut
        } catch (e: ApiException) {
            GeofenceRemoval.Rejected(e.statusCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GeofenceRemoval.Failed(e)
        }
        if (outcome != GeofenceRemoval.Removed) reportRemovalFailure(id, name, outcome)
        removeLocationId(id, LocationSetChangeCause.MANUAL)
    }

    private fun reportRemovalFailure(id: Long, name: String, outcome: GeofenceRemoval) {
        Log.e(TAG, "Geofence removal failed: id=$id status=${outcome.statusLabel}")
        Sentry.captureMessage("Geofence removal failed") { scope ->
            scope.setTag("component", "geofence")
            scope.setExtra("location_id", id.toString())
            scope.setExtra("location_name", name)
            scope.setExtra("status", outcome.statusLabel)
            scope.level = SentryLevel.WARNING
        }
    }

    suspend fun registerAllFromDb() {
        registrationMutex.withLock { reportRegistrationSummary(registerAll()) }
    }

    /**
     * Re-runs full registration off the caller's lifecycle; the Settings health row reads the
     * result. A request made while another is still waiting for the lock is dropped — that
     * queued run will read the same state — but one made during a run schedules one more.
     */
    fun refreshRegistration() {
        if (!refreshPending.compareAndSet(false, true)) return
        scope.launch {
            registrationMutex.withLock {
                refreshPending.set(false)
                try {
                    registerAll()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Geofence refresh failed", e)
                    Sentry.captureException(e)
                }
            }
        }
    }

    private suspend fun registerAll(): RegistrationHealth {
        val outcomes = locationRepository.getAllList().map { stored ->
            val loc = raiseToMinimumRadius(stored)
            loc.id to registerGeofence(loc.id, loc.name, loc.lat, loc.lng, loc.radiusM)
        }
        val health = RegistrationHealth(
            outcomes = outcomes,
            fineLocationGranted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION),
            backgroundLocationGranted = hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            locationEnabled = context.getSystemService(LocationManager::class.java)?.isLocationEnabled,
            locationSettings = checkLocationSettings(),
            checkedAt = Instant.now(),
        )
        _registrationHealth.value = health
        return health
    }

    private fun reportRegistrationSummary(health: RegistrationHealth) {
        Sentry.captureMessage("Geofence registration summary") { scope ->
            scope.setTag("component", "geofence")
            scope.setExtra("saved_count", health.savedCount.toString())
            scope.setExtra("registered_count", health.registeredCount.toString())
            scope.setExtra("failed_count", health.failures.size.toString())
            scope.setExtra(
                "failures",
                health.failures.joinToString { (id, outcome) -> "id=$id status=${outcome.statusLabel}" }
            )
            scope.setExtra("fine_location_granted", health.fineLocationGranted.toString())
            scope.setExtra("background_location_granted", health.backgroundLocationGranted.toString())
            scope.setExtra("location_enabled", health.locationEnabled.toString())
            scope.setExtra("location_settings", health.locationSettings.statusLabel)
            scope.level = if (health.failures.isEmpty()) SentryLevel.INFO else SentryLevel.WARNING
        }
    }

    // Google Location Accuracy switched off is invisible to the permission checks but makes
    // every registration fail with GEOFENCE_NOT_AVAILABLE; a balanced-power settings check
    // is the one API that reports it.
    private suspend fun checkLocationSettings(): LocationSettingsCheck {
        val request = LocationSettingsRequest.Builder()
            .addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 0L).build())
            .build()
        return try {
            withTimeoutOrNull(PLAY_SERVICES_TIMEOUT_MS) {
                settingsClient.checkLocationSettings(request).await()
                LocationSettingsCheck.Available
            } ?: LocationSettingsCheck.TimedOut
        } catch (e: ApiException) {
            LocationSettingsCheck.Unavailable(e.statusCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LocationSettingsCheck.Failed(e)
        }
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
