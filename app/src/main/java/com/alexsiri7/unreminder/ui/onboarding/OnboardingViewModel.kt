package com.alexsiri7.unreminder.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexsiri7.unreminder.data.db.HabitEntity
import com.alexsiri7.unreminder.data.db.WindowEntity
import com.alexsiri7.unreminder.data.repository.HabitRepository
import com.alexsiri7.unreminder.data.repository.OnboardingRepository
import com.alexsiri7.unreminder.data.repository.WindowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

data class OnboardingUiState(
    val step: Int = 0,
    val hasNotificationPermission: Boolean = false,
    val hasFineLocationPermission: Boolean = false,
    val habitName: String = "",
    val windowStartTime: LocalTime = LocalTime.of(9, 0),
    val windowEndTime: LocalTime = LocalTime.of(17, 0),
    val isCompleted: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val onboardingRepository: OnboardingRepository,
    private val habitRepository: HabitRepository,
    private val windowRepository: WindowRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        refreshPermissions()
    }

    fun refreshPermissions() {
        _uiState.value = _uiState.value.copy(
            hasNotificationPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED,
            hasFineLocationPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    fun advanceToStep(step: Int) {
        _uiState.value = _uiState.value.copy(step = step)
    }

    fun updateHabitName(name: String) {
        _uiState.value = _uiState.value.copy(habitName = name)
    }

    fun updateWindowStartTime(time: LocalTime) {
        _uiState.value = _uiState.value.copy(windowStartTime = time)
    }

    fun updateWindowEndTime(time: LocalTime) {
        _uiState.value = _uiState.value.copy(windowEndTime = time)
    }

    fun completeOnboarding(saveHabit: Boolean, saveWindow: Boolean) {
        viewModelScope.launch {
            if (saveHabit && _uiState.value.habitName.isNotBlank()) {
                habitRepository.insert(
                    HabitEntity(
                        name = _uiState.value.habitName,
                        fullDescription = "",
                        lowFloorDescription = "",
                        active = true
                    )
                )
            }
            if (saveWindow) {
                windowRepository.insert(
                    WindowEntity(
                        startTime = _uiState.value.windowStartTime,
                        endTime = _uiState.value.windowEndTime,
                        daysOfWeekBitmask = 0b0011111, // Mon-Fri
                        frequencyPerDay = 1,
                        active = true
                    )
                )
            }
            onboardingRepository.markOnboardingCompleted()
            _uiState.value = _uiState.value.copy(isCompleted = true)
        }
    }

    fun skip() {
        viewModelScope.launch {
            onboardingRepository.markOnboardingCompleted()
            _uiState.value = _uiState.value.copy(isCompleted = true)
        }
    }
}
