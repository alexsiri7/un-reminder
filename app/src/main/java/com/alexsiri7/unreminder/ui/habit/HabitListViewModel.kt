package com.alexsiri7.unreminder.ui.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexsiri7.unreminder.data.db.HabitEntity
import com.alexsiri7.unreminder.data.repository.HabitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HabitListViewModel @Inject constructor(
    private val habitRepository: HabitRepository
) : ViewModel() {

    val habits: StateFlow<List<HabitEntity>> = habitRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
