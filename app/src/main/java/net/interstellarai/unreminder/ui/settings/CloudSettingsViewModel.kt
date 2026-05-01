package net.interstellarai.unreminder.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.VariationRepository
import net.interstellarai.unreminder.service.worker.RefillScheduler
import javax.inject.Inject

data class CloudSettingsUiState(
    val errorMessage: String? = null,
    val isRegenerating: Boolean = false,
)

@HiltViewModel
class CloudSettingsViewModel @Inject constructor(
    private val variationRepository: VariationRepository,
    private val refillScheduler: RefillScheduler,
    private val habitRepository: HabitRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "CloudSettingsVM"
    }

    private val _uiState = MutableStateFlow(CloudSettingsUiState())
    val uiState: StateFlow<CloudSettingsUiState> = _uiState.asStateFlow()

    fun regenerateAll() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRegenerating = true)
            try {
                val habits = habitRepository.getAllActive().first()
                var failCount = 0
                for (habit in habits) {
                    try {
                        variationRepository.deleteForHabit(habit.id)
                        refillScheduler.enqueueForHabit(habit.id)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e(TAG, "regenerateAll: failed for habit ${habit.id}", e)
                        failCount++
                    }
                }
                if (failCount > 0) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Failed to regenerate $failCount variant(s)."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Queued regeneration for ${habits.size} habit(s)."
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "regenerateAll: failed to load habits", e)
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to regenerate variants.")
            } finally {
                _uiState.value = _uiState.value.copy(isRegenerating = false)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
