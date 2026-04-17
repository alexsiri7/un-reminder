package com.alexsiri7.unreminder.ui.location

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexsiri7.unreminder.data.db.LocationEntity
import com.alexsiri7.unreminder.data.repository.LocationRepository
import com.alexsiri7.unreminder.service.geofence.GeofenceManager
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

    val locations: StateFlow<List<LocationEntity>> = locationRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(label: String) {
        viewModelScope.launch {
            try {
                locationRepository.deleteByLabel(label)
                geofenceManager.removeGeofence(label)
            } catch (e: Exception) {
                Log.e("LocationViewModel", "Failed to delete location label=$label", e)
            }
        }
    }
}
