package com.alexsiri7.unreminder.ui.habit

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexsiri7.unreminder.data.db.HabitEntity
import com.alexsiri7.unreminder.data.db.LocationEntity
import com.alexsiri7.unreminder.data.repository.HabitRepository
import com.alexsiri7.unreminder.data.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HabitEditUiState(
    val name: String = "",
    val fullDescription: String = "",
    val lowFloorDescription: String = "",
    val selectedLocationIds: Set<Long> = emptySet(),
    val active: Boolean = true,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false
)

@HiltViewModel
class HabitEditViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitEditUiState())
    val uiState: StateFlow<HabitEditUiState> = _uiState.asStateFlow()

    val allLocations: StateFlow<List<LocationEntity>> = locationRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        private const val TAG = "HabitEditViewModel"
    }

    private var existingHabit: HabitEntity? = null

    fun loadHabit(id: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val habit = habitRepository.getById(id).first()
                if (habit == null) {
                    Log.w(TAG, "loadHabit: habit $id not found")
                    return@launch
                }
                val locationIds = habitRepository.getLocationIds(id).toSet()
                existingHabit = habit
                _uiState.value = HabitEditUiState(
                    name = habit.name,
                    fullDescription = habit.fullDescription,
                    lowFloorDescription = habit.lowFloorDescription,
                    selectedLocationIds = locationIds,
                    active = habit.active
                )
            } catch (e: Exception) {
                Log.e(TAG, "loadHabit: failed to load habit $id", e)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun updateName(name: String) { _uiState.value = _uiState.value.copy(name = name) }
    fun updateFullDescription(desc: String) { _uiState.value = _uiState.value.copy(fullDescription = desc) }
    fun updateLowFloorDescription(desc: String) { _uiState.value = _uiState.value.copy(lowFloorDescription = desc) }

    fun toggleLocation(locationId: Long) {
        val current = _uiState.value.selectedLocationIds
        val updated = if (locationId in current) current - locationId else current + locationId
        _uiState.value = _uiState.value.copy(selectedLocationIds = updated)
    }

    fun setAnywhere() {
        _uiState.value = _uiState.value.copy(selectedLocationIds = emptySet())
    }

    fun updateActive(active: Boolean) { _uiState.value = _uiState.value.copy(active = active) }

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.name.isBlank()) return@launch
            try {
                val existing = existingHabit
                val habitId = if (existing != null) {
                    habitRepository.update(
                        existing.copy(
                            name = state.name,
                            fullDescription = state.fullDescription,
                            lowFloorDescription = state.lowFloorDescription,
                            active = state.active
                        )
                    )
                    existing.id
                } else {
                    habitRepository.insert(
                        HabitEntity(name = state.name, fullDescription = state.fullDescription,
                            lowFloorDescription = state.lowFloorDescription, active = state.active)
                    )
                }
                habitRepository.setLocations(habitId, state.selectedLocationIds)
                _uiState.value = _uiState.value.copy(isSaved = true)
            } catch (e: Exception) {
                Log.e(TAG, "save failed", e)
            }
        }
    }
}
