package net.interstellarai.unreminder.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.di.IoDispatcher
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.notification.DurationParser
import net.interstellarai.unreminder.service.notification.NotificationHelper
import net.interstellarai.unreminder.service.trigger.DismissalTracker
import javax.inject.Inject

data class TimerUiState(
    val promptText: String = "",
    val triggerId: Long = -1L,
    val totalSeconds: Int? = null,
    val remainingSeconds: Int? = null,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false,
    val isDone: Boolean = false,
    val isLoading: Boolean = true,
)

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val triggerRepository: TriggerRepository,
    private val dismissalTracker: DismissalTracker,
    private val notificationHelper: NotificationHelper,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun init(triggerId: Long) {
        viewModelScope.launch(ioDispatcher) {
            val trigger = triggerRepository.getById(triggerId)
            val prompt = trigger?.generatedPrompt ?: ""
            val seconds = DurationParser.parseTotalSeconds(prompt)
            _uiState.value = TimerUiState(
                triggerId = triggerId,
                promptText = prompt,
                totalSeconds = seconds,
                remainingSeconds = seconds,
                isLoading = false,
            )
        }
    }

    fun start() {
        if (_uiState.value.isRunning) return
        val remaining = _uiState.value.remainingSeconds?.takeIf { it > 0 } ?: return
        _uiState.value = _uiState.value.copy(isRunning = true, isFinished = false)
        timerJob = viewModelScope.launch {
            var secs = remaining
            while (secs > 0) {
                delay(1_000L)
                secs--
                _uiState.value = _uiState.value.copy(remainingSeconds = secs)
            }
            _uiState.value = _uiState.value.copy(isRunning = false, isFinished = true)
        }
    }

    fun pause() {
        timerJob?.cancel()
        timerJob = null
        _uiState.value = _uiState.value.copy(isRunning = false)
    }

    fun reset() {
        timerJob?.cancel()
        timerJob = null
        _uiState.value = _uiState.value.copy(
            remainingSeconds = _uiState.value.totalSeconds,
            isRunning = false,
            isFinished = false,
        )
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

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
