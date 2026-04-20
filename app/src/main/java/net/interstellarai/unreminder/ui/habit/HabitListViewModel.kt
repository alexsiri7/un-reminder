package net.interstellarai.unreminder.ui.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.service.llm.AiStatus
import net.interstellarai.unreminder.service.llm.PromptGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HabitListViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val promptGenerator: PromptGenerator,
) : ViewModel() {

    val habits: StateFlow<List<HabitEntity>> = habitRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aiStatus: StateFlow<AiStatus> = promptGenerator.aiStatus

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
