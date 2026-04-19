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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofenceManager @Inject constructor(
    private val context: Context,
    private val locationRepository: LocationRepository
) {
    companion object {
        private const val TAG = "GeofenceManager"
    }

    private val locationIdLock = Any()

    var currentLocationIds: Set<Long> = emptySet()
        get() = synchronized(locationIdLock) { field }
        private set

    private val geofencingClient = LocationServices.getGeofencingClient(context)

    fun addLocationId(id: Long) = synchronized(locationIdLock) { currentLocationIds = currentLocationIds + id }
    fun removeLocationId(id: Long) = synchronized(locationIdLock) { currentLocationIds = currentLocationIds - id }

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
