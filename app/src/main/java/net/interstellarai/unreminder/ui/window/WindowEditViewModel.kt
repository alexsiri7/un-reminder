package net.interstellarai.unreminder.ui.window

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.interstellarai.unreminder.data.db.WindowEntity
import net.interstellarai.unreminder.data.repository.WindowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

data class WindowEditUiState(
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val endHour: Int = 17,
    val endMinute: Int = 0,
    val daysOfWeekBitmask: Int = 0b1111111, // all days
    val frequencyPerDay: Int = 1,
    val active: Boolean = true,
    val isSaved: Boolean = false
)

@HiltViewModel
class WindowEditViewModel @Inject constructor(
    private val windowRepository: WindowRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WindowEditUiState())
    val uiState: StateFlow<WindowEditUiState> = _uiState.asStateFlow()

    private var existingWindow: WindowEntity? = null

    fun loadWindow(id: Long) {
        viewModelScope.launch {
            val window = windowRepository.getById(id).first()
            if (window != null) {
                existingWindow = window
                _uiState.value = WindowEditUiState(
                    startHour = window.startTime.hour,
                    startMinute = window.startTime.minute,
                    endHour = window.endTime.hour,
                    endMinute = window.endTime.minute,
                    daysOfWeekBitmask = window.daysOfWeekBitmask,
                    frequencyPerDay = window.frequencyPerDay,
                    active = window.active
                )
            }
        }
    }

    fun updateStartTime(hour: Int, minute: Int) {
        _uiState.value = _uiState.value.copy(startHour = hour, startMinute = minute)
    }

    fun updateEndTime(hour: Int, minute: Int) {
        _uiState.value = _uiState.value.copy(endHour = hour, endMinute = minute)
    }

    fun toggleDay(dayBit: Int) {
        _uiState.value = _uiState.value.copy(
            daysOfWeekBitmask = _uiState.value.daysOfWeekBitmask xor dayBit
        )
    }

    fun updateFrequency(freq: Int) {
        _uiState.value = _uiState.value.copy(frequencyPerDay = freq.coerceIn(1, 3))
    }

    fun updateActive(active: Boolean) {
        _uiState.value = _uiState.value.copy(active = active)
    }

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            val startTime = LocalTime.of(state.startHour, state.startMinute)
            val endTime = LocalTime.of(state.endHour, state.endMinute)

            val existing = existingWindow
            if (existing != null) {
                windowRepository.update(
                    existing.copy(
                        startTime = startTime,
                        endTime = endTime,
                        daysOfWeekBitmask = state.daysOfWeekBitmask,
                        frequencyPerDay = state.frequencyPerDay,
                        active = state.active
                    )
                )
            } else {
                windowRepository.insert(
                    WindowEntity(
                        startTime = startTime,
                        endTime = endTime,
                        daysOfWeekBitmask = state.daysOfWeekBitmask,
                        frequencyPerDay = state.frequencyPerDay,
                        active = state.active
                    )
                )
            }
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }
}
