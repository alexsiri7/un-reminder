package net.interstellarai.unreminder.service.geofence

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import net.interstellarai.unreminder.data.repository.LocationRepository
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofenceManager @Inject constructor(
    private val context: Context,
    private val locationRepository: LocationRepository
) {
    companion object {
        private const val TAG = "GeofenceManager"
        private const val PREFS_NAME = "geofence_prefs"
        private const val KEY_LOCATION_IDS = "current_location_ids"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val locationIdLock = Any()

    private val _currentLocationIds = MutableStateFlow<Set<Long>>(restoreLocationIds())
    val currentLocationIds: StateFlow<Set<Long>> = _currentLocationIds.asStateFlow()

    private val geofencingClient = LocationServices.getGeofencingClient(context)

    private fun restoreLocationIds(): Set<Long> {
        val stored = prefs.getStringSet(KEY_LOCATION_IDS, emptySet()) ?: emptySet()
        return stored.mapNotNull { it.toLongOrNull() }.toSet()
    }

    private fun persistLocationIds(ids: Set<Long>) {
        prefs.edit().putStringSet(KEY_LOCATION_IDS, ids.map { it.toString() }.toSet()).apply()
    }

    fun addLocationId(id: Long) = synchronized(locationIdLock) {
        val updated = _currentLocationIds.value + id
        _currentLocationIds.value = updated
        persistLocationIds(updated)
    }

    fun removeLocationId(id: Long) = synchronized(locationIdLock) {
        val updated = _currentLocationIds.value - id
        _currentLocationIds.value = updated
        persistLocationIds(updated)
    }

    fun registerGeofence(id: Long, name: String, lat: Double, lng: Double, radiusM: Float) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Missing location permission, cannot register geofence")
            return
        }

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

        geofencingClient.addGeofences(request, GeofenceBroadcastReceiver.getPendingIntent(context))
            .addOnSuccessListener { Log.d(TAG, "Geofence registered: id=$id name=$name") }
            .addOnFailureListener { Log.e(TAG, "Geofence registration failed: id=$id", it) }
    }

    fun removeGeofence(id: Long) {
        geofencingClient.removeGeofences(listOf(id.toString()))
    }

    suspend fun registerAllFromDb() {
        for (loc in locationRepository.getAllList()) {
            registerGeofence(loc.id, loc.name, loc.lat, loc.lng, loc.radiusM)
        }
    }
}
