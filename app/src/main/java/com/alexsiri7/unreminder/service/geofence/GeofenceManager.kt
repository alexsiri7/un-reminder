package com.alexsiri7.unreminder.service.geofence

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.alexsiri7.unreminder.data.repository.LocationRepository
import com.alexsiri7.unreminder.domain.model.LocationTag
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofenceManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "GeofenceManager"
    }

    @Volatile
    var currentLocationTag: LocationTag = LocationTag.ANYWHERE

    private val geofencingClient = LocationServices.getGeofencingClient(context)

    @Inject
    lateinit var locationRepository: LocationRepository

    fun registerGeofence(label: String, lat: Double, lng: Double, radiusM: Float) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Missing location permission, cannot register geofence")
            return
        }

        val geofence = Geofence.Builder()
            .setRequestId(label)
            .setCircularRegion(lat, lng, radiusM)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        val pendingIntent = GeofenceBroadcastReceiver.getPendingIntent(context)

        geofencingClient.addGeofences(request, pendingIntent)
            .addOnSuccessListener { Log.d(TAG, "Geofence registered: $label") }
            .addOnFailureListener { Log.e(TAG, "Geofence registration failed: $label", it) }
    }

    fun removeGeofence(label: String) {
        geofencingClient.removeGeofences(listOf(label))
    }

    suspend fun registerAllFromDb() {
        val locations = locationRepository.getAllList()
        for (loc in locations) {
            registerGeofence(loc.label, loc.lat, loc.lng, loc.radiusM)
        }
    }
}
