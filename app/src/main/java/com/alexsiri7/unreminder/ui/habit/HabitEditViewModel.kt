package com.alexsiri7.unreminder.ui.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexsiri7.unreminder.data.db.HabitEntity
import com.alexsiri7.unreminder.data.repository.HabitRepository
import com.alexsiri7.unreminder.domain.model.LocationTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HabitEditUiState(
    val name: String = "",
    val fullDescription: String = "",
    val lowFloorDescription: String = "",
    val locationTag: LocationTag = LocationTag.ANYWHERE,
    val active: Boolean = true,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false
)

@HiltViewModel
class HabitEditViewModel @Inject constructor(
    private val habitRepository: HabitRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitEditUiState())
    val uiState: StateFlow<HabitEditUiState> = _uiState.asStateFlow()

    private var existingHabit: HabitEntity? = null

    fun loadHabit(id: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val habit = habitRepository.getById(id).first()
            if (habit != null) {
                existingHabit = habit
                _uiState.value = HabitEditUiState(
                    name = habit.name,
                    fullDescription = habit.fullDescription,
                    lowFloorDescription = habit.lowFloorDescription,
                    locationTag = habit.locationTag,
                    active = habit.active
                )
            }
        }
    }

    fun updateName(name: String) { _uiState.value = _uiState.value.copy(name = name) }
    fun updateFullDescription(desc: String) { _uiState.value = _uiState.value.copy(fullDescription = desc) }
    fun updateLowFloorDescription(desc: String) { _uiState.value = _uiState.value.copy(lowFloorDescription = desc) }
    fun updateLocationTag(tag: LocationTag) { _uiState.value = _uiState.value.copy(locationTag = tag) }
    fun updateActive(active: Boolean) { _uiState.value = _uiState.value.copy(active = active) }

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.name.isBlank()) return@launch

            val existing = existingHabit
            if (existing != null) {
                habitRepository.update(
                    existing.copy(
                        name = state.name,
                        fullDescription = state.fullDescription,
                        lowFloorDescription = state.lowFloorDescription,
                        locationTag = state.locationTag,
                        active = state.active
                    )
                )
            } else {
                habitRepository.insert(
                    HabitEntity(
                        name = state.name,
                        fullDescription = state.fullDescription,
                        lowFloorDescription = state.lowFloorDescription,
                        locationTag = state.locationTag,
                        active = state.active
                    )
                )
            }
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }
}
