package net.interstellarai.unreminder.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.di.IoDispatcher
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.notification.NotificationHelper
import net.interstellarai.unreminder.service.trigger.DismissalTracker
import javax.inject.Inject

data class ReminderDetailUiState(
    val promptText: String = "",
    val habitName: String = "",
    val dedicationLevel: Int = 0,
    val triggerId: Long = -1L,
    val isLoading: Boolean = true,
    val isDone: Boolean = false,
)

@HiltViewModel
class ReminderDetailViewModel @Inject constructor(
    private val triggerRepository: TriggerRepository,
    private val habitRepository: HabitRepository,
    private val dismissalTracker: DismissalTracker,
    private val notificationHelper: NotificationHelper,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReminderDetailUiState())
    val uiState: StateFlow<ReminderDetailUiState> = _uiState.asStateFlow()

    fun init(triggerId: Long) {
        viewModelScope.launch(ioDispatcher) {
            val trigger = triggerRepository.getById(triggerId)
            val habit = trigger?.habitId?.let { habitRepository.getByIdOnce(it) }
            _uiState.value = ReminderDetailUiState(
                triggerId = triggerId,
                promptText = trigger?.generatedPrompt ?: "",
                habitName = habit?.name ?: "",
                dedicationLevel = habit?.dedicationLevel ?: 0,
                isLoading = false,
            )
        }
    }

    fun markCompleted() = recordOutcome(TriggerStatus.COMPLETED)
    fun markDismissed() = recordOutcome(TriggerStatus.DISMISSED)

    private fun recordOutcome(status: TriggerStatus) {
        val triggerId = _uiState.value.triggerId
        viewModelScope.launch(ioDispatcher) {
            triggerRepository.updateOutcome(triggerId, status)
            when (status) {
                TriggerStatus.COMPLETED -> dismissalTracker.onCompleted(triggerId)
                TriggerStatus.DISMISSED -> dismissalTracker.onDismissed(triggerId)
                else -> {}
            }
            notificationHelper.cancelNotification(triggerId)
            _uiState.value = _uiState.value.copy(isDone = true)
        }
    }
}
