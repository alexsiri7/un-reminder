package com.alexsiri7.unreminder.ui.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexsiri7.unreminder.data.db.LocationEntity
import com.alexsiri7.unreminder.data.repository.LocationRepository
import com.alexsiri7.unreminder.service.geofence.GeofenceManager
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class LocationViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val geofenceManager: GeofenceManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val locations: StateFlow<List<LocationEntity>> = locationRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @Suppress("MissingPermission")
    fun setCurrentLocation(label: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return

        viewModelScope.launch {
            try {
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                val location = fusedClient.lastLocation.await() ?: return@launch
                val lat = location.latitude
                val lng = location.longitude
                locationRepository.upsertLocation(label, lat, lng)
                geofenceManager.registerGeofence(label, lat, lng, 100f)
            } catch (_: Exception) {
                // Location unavailable
            }
        }
    }
}
