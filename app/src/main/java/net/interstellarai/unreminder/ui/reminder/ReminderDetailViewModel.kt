package net.interstellarai.unreminder.ui.reminder

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
import net.interstellarai.unreminder.widget.WidgetRefresher
import javax.inject.Inject

data class ReminderDetailUiState(
    val promptText: String = "",
    val habitName: String = "",
    val dedicationLevel: Int = 0,
    val videoUrl: String? = null,
    val triggerId: Long = -1L,
    val isLoading: Boolean = true,
    val isDone: Boolean = false,
    val isProcessing: Boolean = false,
    val canComplete: Boolean = false,
)

@HiltViewModel
class ReminderDetailViewModel @Inject constructor(
    private val triggerRepository: TriggerRepository,
    private val habitRepository: HabitRepository,
    private val dismissalTracker: DismissalTracker,
    private val notificationHelper: NotificationHelper,
    private val widgetRefresher: WidgetRefresher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReminderDetailUiState())
    val uiState: StateFlow<ReminderDetailUiState> = _uiState.asStateFlow()

    fun init(triggerId: Long) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val trigger = triggerRepository.getById(triggerId)
                val habit = trigger?.habitId?.let { habitRepository.getByIdOnce(it) }
                val canComplete = when (trigger?.status) {
                    TriggerStatus.FIRED -> {
                        // Opened from a live notification. The Open action does not auto-cancel it,
                        // and OPENED never reaches the DismissalTracker (#378). A declined write
                        // means a swipe or Later resolved the trigger first, so Did it must hide.
                        val opened = triggerRepository.recordOutcome(triggerId, TriggerStatus.OPENED)
                        notificationHelper.cancelNotification(triggerId)
                        opened
                    }
                    TriggerStatus.OPENED -> true
                    else -> false
                }
                _uiState.value = ReminderDetailUiState(
                    triggerId = triggerId,
                    promptText = trigger?.generatedPrompt ?: "",
                    habitName = habit?.name ?: "",
                    dedicationLevel = habit?.dedicationLevel ?: 0,
                    videoUrl = trigger?.actionUrl?.takeIf { it.startsWith("https://") },
                    isLoading = false,
                    canComplete = canComplete,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to load trigger $triggerId", e)
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun markCompleted() {
        val triggerId = _uiState.value.triggerId
        if (triggerId == -1L) return
        if (_uiState.value.isProcessing) return
        _uiState.value = _uiState.value.copy(isProcessing = true)
        viewModelScope.launch(ioDispatcher) {
            try {
                val recorded = triggerRepository.recordOutcome(triggerId, TriggerStatus.COMPLETED)
                if (recorded) {
                    dismissalTracker.onCompleted(triggerId)
                    widgetRefresher.refresh()
                }
                notificationHelper.cancelNotification(triggerId)
                // A declined write means a swipe or Later resolved the trigger since the screen
                // loaded: nothing was recorded, so withdraw the chip rather than leave as if
                // the tap had worked.
                _uiState.value = if (recorded) {
                    _uiState.value.copy(isDone = true)
                } else {
                    _uiState.value.copy(canComplete = false)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to record completion for trigger $triggerId", e)
            } finally {
                _uiState.value = _uiState.value.copy(isProcessing = false)
            }
        }
    }

    private companion object {
        private const val TAG = "ReminderDetailVM"
    }
}
