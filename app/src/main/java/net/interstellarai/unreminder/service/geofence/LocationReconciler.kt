package net.interstellarai.unreminder.service.geofence

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import io.sentry.Sentry
import net.interstellarai.unreminder.data.db.LocationEntity
import net.interstellarai.unreminder.data.repository.LocationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of one reconciliation attempt; anything but [Reconciliation.Reconciled] left the recorded state alone. */
sealed interface Reconciliation {
    data object Reconciled : Reconciliation
    data object Skipped : Reconciliation
    data object PermissionMissing : Reconciliation
    data object LocationDisabled : Reconciliation
    data object NoFix : Reconciliation
    data class Failed(val cause: Throwable) : Reconciliation
}

/**
 * Corrects [GeofenceManager.currentLocationIds] against an actual position fix. Geofence
 * transitions remain the cheap fast path, but a transition that is never delivered —
 * registration rejected, no fix inside the fence when it was registered, broadcast dropped —
 * leaves the set permanently wrong. Reconciling against a fix repairs it in both directions.
 */
@Singleton
class LocationReconciler @Inject constructor(
    private val context: Context,
    private val locationRepository: LocationRepository,
    private val geofenceManager: GeofenceManager,
    private val fusedLocationClient: FusedLocationProviderClient,
) {
    companion object {
        private const val TAG = "LocationReconciler"

        // This runs ahead of a trigger firing and of the widget redraw, so a fix that is slow
        // to arrive has to be abandoned rather than delay either.
        private const val FIX_TIMEOUT_MS = 5_000L

        // A cached fix from a previous town is worse than no answer: it would confidently
        // replace a correct set with a wrong one.
        private const val LAST_LOCATION_MAX_AGE_MS = 10 * 60 * 1000L

        // App start, the widget tick and a trigger routinely coincide; one fix serves all three.
        private const val DEBOUNCE_MS = 5 * 60 * 1000L
    }

    private val inFlight = Mutex()

    @Volatile
    private var lastSuccessAtMillis = 0L

    private val _reconciliationFailure = MutableStateFlow<String?>(null)

    /**
     * Status label of the last reconciliation that failed on a real error, or null when the last
     * one succeeded. Not persisted across process death, so a fresh process reads null until its
     * first run — the same contract as [GeofenceManager.registrationHealth].
     */
    val reconciliationFailure: StateFlow<String?> = _reconciliationFailure.asStateFlow()

    /**
     * Replaces the recorded location set with the one the device's position implies. Failure of
     * any kind — no permission, no fix, a stale cache — leaves the existing set untouched;
     * clearing it on a momentarily unavailable GPS would turn an intermittent problem into the
     * permanent one this exists to fix.
     */
    suspend fun reconcile(): Reconciliation = run(force = false)

    /**
     * Reconciles for a user who asked for it, ignoring the debounce window and waiting for any
     * run already in flight. App start reconciles too, so a button that respected the window
     * would do nothing at all for the first five minutes after launch.
     */
    suspend fun reconcileNow(): Reconciliation = run(force = true)

    private suspend fun run(force: Boolean): Reconciliation {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return Reconciliation.PermissionMissing
        if (context.getSystemService(LocationManager::class.java)?.isLocationEnabled == false) {
            return Reconciliation.LocationDisabled
        }
        // A reconciliation already running covers a background caller too, so it skips rather than queues.
        if (force) inFlight.lock() else if (!inFlight.tryLock()) return Reconciliation.Skipped
        try {
            if (!force && System.currentTimeMillis() - lastSuccessAtMillis < DEBOUNCE_MS) {
                return Reconciliation.Skipped
            }
            val attempt = obtainFix()
            val fix = attempt.fix ?: return attempt.error?.let { failure(it) } ?: Reconciliation.NoFix
            val locations = locationRepository.getAllList()
            geofenceManager.recordReconciliation(locations.associate { it.id to it.contains(fix) })
            lastSuccessAtMillis = System.currentTimeMillis()
            _reconciliationFailure.value = null
            return Reconciliation.Reconciled
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return failure(e)
        } finally {
            inFlight.unlock()
        }
    }

    /** One lookup's outcome: the fix it produced, or the exception that stopped it producing one. */
    private class FixAttempt(val fix: Location?, val error: Throwable?)

    /**
     * Both lookups always run: a `getCurrentLocation` error says nothing about whether the cached
     * fix can still answer correctly, so an error only becomes the outcome when neither leg
     * produced a usable fix.
     */
    private suspend fun obtainFix(): FixAttempt {
        val current = currentFix()
        current.fix?.let { return FixAttempt(it, null) }
        val last = lastKnownFix()
        val fresh = last.fix?.takeIf { System.currentTimeMillis() - it.time <= LAST_LOCATION_MAX_AGE_MS }
        if (fresh != null) return FixAttempt(fresh, null)
        // The current-fix error is the primary fault; lastLocation usually fails for the same reason.
        return FixAttempt(null, current.error ?: last.error)
    }

    private suspend fun currentFix(): FixAttempt {
        val cancellation = CancellationTokenSource()
        return try {
            // withTimeoutOrNull absorbs its own cancellation, so a fix that never arrives is a
            // null fix with no error: no answer, not a fault.
            FixAttempt(
                withTimeoutOrNull(FIX_TIMEOUT_MS) {
                    @Suppress("MissingPermission")
                    fusedLocationClient
                        .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                        .await()
                },
                null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Current location request failed", e)
            FixAttempt(null, e)
        } finally {
            // Stops Play Services hunting for a fix nobody is waiting for any more.
            cancellation.cancel()
        }
    }

    private suspend fun lastKnownFix(): FixAttempt =
        try {
            FixAttempt(
                withTimeoutOrNull(FIX_TIMEOUT_MS) {
                    @Suppress("MissingPermission")
                    fusedLocationClient.lastLocation.await()
                },
                null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Last location lookup failed", e)
            FixAttempt(null, e)
        }

    private fun failure(e: Throwable): Reconciliation.Failed {
        val label = statusLabelOf(e)
        Log.e(TAG, "Location reconciliation failed ($label)", e)
        Sentry.captureException(e) { scope ->
            scope.setTag("component", "geofence")
            scope.setExtra("reconciliation_status", label)
        }
        _reconciliationFailure.value = label
        return Reconciliation.Failed(e)
    }

    private fun statusLabelOf(cause: Throwable): String {
        val name = cause::class.simpleName ?: "Exception"
        return if (cause is ApiException) "$name(${cause.statusCode})" else name
    }

    // Rows saved before the radius floor existed are only raised to it when registration next
    // runs; judging them by their stored radius would contradict the fence that is registered
    // and undo an ENTER the platform correctly delivered.
    private fun LocationEntity.contains(fix: Location): Boolean {
        val results = FloatArray(1)
        Location.distanceBetween(fix.latitude, fix.longitude, lat, lng, results)
        return results[0] <= radiusM.coerceAtLeast(GeofenceManager.MIN_RADIUS_M)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
