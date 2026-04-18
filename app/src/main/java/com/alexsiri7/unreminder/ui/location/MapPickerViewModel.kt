package com.alexsiri7.unreminder.ui.location

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexsiri7.unreminder.data.repository.LocationRepository
import com.alexsiri7.unreminder.service.geofence.GeofenceManager
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class MapPickerUiState(
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val radiusM: Float = 100f,
    val isLoading: Boolean = false,
    val initialCenterLat: Double = 51.5074,  // London — fallback when no GPS fix is available
    val initialCenterLng: Double = -0.1278,
    val centerReady: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class MapPickerViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val geofenceManager: GeofenceManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "MapPickerViewModel"
    }

    private val _uiState = MutableStateFlow(MapPickerUiState())
    val uiState: StateFlow<MapPickerUiState> = _uiState.asStateFlow()

    fun initialize(existingLabel: String?) {
        viewModelScope.launch {
            if (existingLabel != null) {
                val loc = locationRepository.getByName(existingLabel)
                if (loc != null) {
                    _uiState.value = _uiState.value.copy(
                        name = loc.name,
                        lat = loc.lat,
                        lng = loc.lng,
                        radiusM = loc.radiusM,
                        initialCenterLat = loc.lat,
                        initialCenterLng = loc.lng,
                        centerReady = true
                    )
                    return@launch
                }
            }
            // Permission may not be granted (no explicit request in this flow); lastLocation
            // returns null gracefully if unavailable — the map falls back to London defaults.
            val loc = try {
                @Suppress("MissingPermission")
                LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
            } catch (e: Exception) {
                Log.w("MapPickerViewModel", "Could not get last known location", e)
                null
            }
            val lat = loc?.latitude ?: _uiState.value.initialCenterLat
            val lng = loc?.longitude ?: _uiState.value.initialCenterLng
            _uiState.value = _uiState.value.copy(
                lat = lat,
                lng = lng,
                initialCenterLat = lat,
                initialCenterLng = lng,
                centerReady = true
            )
        }
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun updatePin(lat: Double, lng: Double) {
        _uiState.value = _uiState.value.copy(lat = lat, lng = lng)
    }

    fun updateRadius(radiusM: Float) {
        _uiState.value = _uiState.value.copy(radiusM = radiusM)
    }

    fun save(onComplete: () -> Unit) {
        val state = _uiState.value
        if (state.name.isBlank()) return
        viewModelScope.launch {
            try {
                val id = locationRepository.upsertLocation(state.name, state.lat, state.lng, state.radiusM)
                geofenceManager.registerGeofence(id, state.name, state.lat, state.lng, state.radiusM)
                onComplete()
            } catch (e: Exception) {
                Log.e("MapPickerViewModel", "Failed to save location for name=${state.name}", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Could not save location. Please try again."
                )
            }
        }
    }
}
