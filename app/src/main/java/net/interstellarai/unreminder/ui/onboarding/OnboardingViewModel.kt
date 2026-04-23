package net.interstellarai.unreminder.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.db.WindowEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.OnboardingRepository
import net.interstellarai.unreminder.data.repository.WindowRepository
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
    val isCompleted: Boolean = false,
    val errorMessage: String? = null
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
            try {
                val state = _uiState.value
                if (saveHabit && state.habitName.isNotBlank()) {
                    habitRepository.insert(
                        HabitEntity(
                            name = state.habitName,
                            active = true
                        )
                    )
                }
                if (saveWindow) {
                    windowRepository.insert(
                        WindowEntity(
                            startTime = state.windowStartTime,
                            endTime = state.windowEndTime,
                            // Mon–Fri: bit 0 = Monday, bit 4 = Friday (matches DailySchedulerWorkerTest convention)
                            daysOfWeekBitmask = 0b0011111,
                            frequencyPerDay = 1,
                            active = true
                        )
                    )
                }
                onboardingRepository.markOnboardingCompleted()
                _uiState.value = _uiState.value.copy(isCompleted = true)
            } catch (e: Exception) {
                Log.e(TAG, "completeOnboarding failed", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Something went wrong. Please try again."
                )
            }
        }
    }

    fun skip() {
        viewModelScope.launch {
            try {
                onboardingRepository.markOnboardingCompleted()
            } catch (e: Exception) {
                Log.e(TAG, "skip: failed to mark onboarding completed, proceeding anyway", e)
            }
            _uiState.value = _uiState.value.copy(isCompleted = true)
        }
    }

    companion object {
        private const val TAG = "OnboardingViewModel"
    }
}
