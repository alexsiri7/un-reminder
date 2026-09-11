package net.interstellarai.unreminder.ui.location

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.interstellarai.unreminder.data.db.LocationEntity
import net.interstellarai.unreminder.data.repository.LocationRepository
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import net.interstellarai.unreminder.service.geofence.LocationCheck
import net.interstellarai.unreminder.service.geofence.LocationReconciler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LocationPresence { INSIDE, OUTSIDE, UNKNOWN }

data class LocationRow(
    val location: LocationEntity,
    val check: LocationCheck?
) {
    val presence: LocationPresence
        get() = when {
            check == null -> LocationPresence.UNKNOWN
            check.inside -> LocationPresence.INSIDE
            else -> LocationPresence.OUTSIDE
        }
}

@HiltViewModel
class LocationViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val geofenceManager: GeofenceManager,
    private val locationReconciler: LocationReconciler
) : ViewModel() {

    companion object {
        private const val TAG = "LocationViewModel"
    }

    // The checks are the display authority; joining currentLocationIds in as well would let the
    // screen render a disagreement between the two that cannot otherwise occur.
    val locations: StateFlow<List<LocationRow>> = combine(
        locationRepository.getAll(),
        geofenceManager.locationChecks
    ) { locations, checks ->
        locations.map { LocationRow(it, checks[it.id]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _recalculation = MutableStateFlow<RecalculationState>(RecalculationState.Idle)
    val recalculation: StateFlow<RecalculationState> = _recalculation.asStateFlow()

    fun recalculate() {
        if (_recalculation.value == RecalculationState.Running) return
        _recalculation.value = RecalculationState.Running
        viewModelScope.launch {
            _recalculation.value = RecalculationState.of(locationReconciler.reconcileNow())
        }
    }

    fun deleteLocation(location: LocationEntity) {
        viewModelScope.launch {
            try {
                locationRepository.delete(location)
                geofenceManager.removeGeofence(location.id, location.name)
                geofenceManager.refreshRegistration()
            } catch (e: Exception) {
                Log.e(TAG, "deleteLocation failed for id=${location.id}", e)
            }
        }
    }
}
