package com.alexsiri7.unreminder.ui.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
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

    companion object {
        private const val TAG = "LocationViewModel"
    }

    val locations: StateFlow<List<LocationEntity>> = locationRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @Suppress("MissingPermission")
    fun addCurrentLocation(name: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return
        if (name.isBlank()) return

        viewModelScope.launch {
            try {
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                val location = fusedClient.lastLocation.await() ?: return@launch
                val trimmedName = name.trim()
                val id = locationRepository.insert(trimmedName, location.latitude, location.longitude)
                geofenceManager.registerGeofence(id, trimmedName, location.latitude, location.longitude, 100f)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add location name=$name", e)
            }
        }
    }

    fun deleteLocation(location: LocationEntity) {
        viewModelScope.launch {
            try {
                locationRepository.delete(location)
                geofenceManager.removeGeofence(location.id)
            } catch (e: Exception) {
                Log.e(TAG, "deleteLocation failed for id=${location.id}", e)
            }
        }
    }
}
