package net.interstellarai.unreminder.ui.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.domain.AvailabilityStatus
import net.interstellarai.unreminder.domain.HabitAvailabilityService
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import net.interstellarai.unreminder.service.llm.AiStatus
import net.interstellarai.unreminder.service.llm.PromptGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HabitListViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val promptGenerator: PromptGenerator,
    private val availabilityService: HabitAvailabilityService,
    private val geofenceManager: GeofenceManager,
) : ViewModel() {

    val habits: StateFlow<List<HabitEntity>> = habitRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aiStatus: StateFlow<AiStatus> = promptGenerator.aiStatus

    private val _habitAvailability = MutableStateFlow<Map<Long, AvailabilityStatus>>(emptyMap())
    val habitAvailability: StateFlow<Map<Long, AvailabilityStatus>> = _habitAvailability.asStateFlow()

    init {
        viewModelScope.launch {
            combine(habits, geofenceManager.currentLocationIds) { habits, _ -> habits }
                .collectLatest { habits ->
                    _habitAvailability.value = availabilityService.computeForAll(habits)
                }
        }
    }

    fun toggleActive(habit: HabitEntity) {
        viewModelScope.launch {
            habitRepository.update(habit.copy(active = !habit.active))
        }
    }

    fun delete(habit: HabitEntity) {
        viewModelScope.launch {
            habitRepository.delete(habit)
        }
    }
}
