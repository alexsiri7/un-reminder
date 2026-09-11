package net.interstellarai.unreminder.service.geofence

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import io.sentry.Sentry
import net.interstellarai.unreminder.data.db.LocationEntity
import net.interstellarai.unreminder.data.repository.LocationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

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

    /**
     * Replaces the recorded location set with the one the device's position implies. Failure of
     * any kind — no permission, no fix, a stale cache — leaves the existing set untouched;
     * clearing it on a momentarily unavailable GPS would turn an intermittent problem into the
     * permanent one this exists to fix.
     */
    suspend fun reconcile() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return
        // A reconciliation already running covers this caller too, so it skips rather than queues.
        if (!inFlight.tryLock()) return
        try {
            if (System.currentTimeMillis() - lastSuccessAtMillis < DEBOUNCE_MS) return
            val fix = obtainFix() ?: return
            val locations = locationRepository.getAllList()
            geofenceManager.replaceLocationIds(locations.filter { it.contains(fix) }.map { it.id }.toSet())
            lastSuccessAtMillis = System.currentTimeMillis()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Location reconciliation failed", e)
            Sentry.captureException(e) { scope -> scope.setTag("component", "location-reconciler") }
        } finally {
            inFlight.unlock()
        }
    }

    private suspend fun obtainFix(): Location? =
        currentFix() ?: lastKnownFix()?.takeIf { System.currentTimeMillis() - it.time <= LAST_LOCATION_MAX_AGE_MS }

    private suspend fun currentFix(): Location? {
        val cancellation = CancellationTokenSource()
        return try {
            withTimeoutOrNull(FIX_TIMEOUT_MS) {
                @Suppress("MissingPermission")
                fusedLocationClient
                    .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                    .await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Current location request failed", e)
            null
        } finally {
            // Stops Play Services hunting for a fix nobody is waiting for any more.
            cancellation.cancel()
        }
    }

    private suspend fun lastKnownFix(): Location? =
        try {
            withTimeoutOrNull(FIX_TIMEOUT_MS) {
                @Suppress("MissingPermission")
                fusedLocationClient.lastLocation.await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Last location lookup failed", e)
            null
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
