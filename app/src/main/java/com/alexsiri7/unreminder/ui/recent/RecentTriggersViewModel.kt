package com.alexsiri7.unreminder.ui.recent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexsiri7.unreminder.data.db.HabitEntity
import com.alexsiri7.unreminder.data.db.TriggerEntity
import com.alexsiri7.unreminder.data.repository.HabitRepository
import com.alexsiri7.unreminder.data.repository.TriggerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TriggerWithHabit(
    val trigger: TriggerEntity,
    val habitName: String?
)

@HiltViewModel
class RecentTriggersViewModel @Inject constructor(
    triggerRepository: TriggerRepository,
    habitRepository: HabitRepository
) : ViewModel() {

    val triggers: StateFlow<List<TriggerWithHabit>> = combine(
        triggerRepository.getRecentTriggers(20),
        habitRepository.getAll()
    ) { triggers, habits ->
        val habitMap = habits.associateBy { it.id }
        triggers.map { trigger ->
            TriggerWithHabit(
                trigger = trigger,
                habitName = trigger.habitId?.let { habitMap[it]?.name }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
