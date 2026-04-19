package net.interstellarai.unreminder.ui.location

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.interstellarai.unreminder.data.db.LocationEntity
import net.interstellarai.unreminder.data.repository.LocationRepository
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocationViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val geofenceManager: GeofenceManager
) : ViewModel() {

    companion object {
        private const val TAG = "LocationViewModel"
    }

    val locations: StateFlow<List<LocationEntity>> = locationRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
