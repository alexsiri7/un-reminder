package net.interstellarai.unreminder.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.PersonalContextRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import net.interstellarai.unreminder.service.trigger.TriggerPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class SettingsUiState(
    val hasNotificationPermission: Boolean = false,
    val hasFineLocationPermission: Boolean = false,
    val hasBackgroundLocationPermission: Boolean = false,
    val testTriggered: Boolean = false,
    val testTriggeredEmpty: Boolean = false,
    val errorMessage: String? = null,
    val personalContext: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val triggerPipeline: TriggerPipeline,
    private val triggerRepository: TriggerRepository,
    private val habitRepository: HabitRepository,
    private val geofenceManager: GeofenceManager,
    private val personalContextRepository: PersonalContextRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            personalContextRepository.personalContext.collect { ctx ->
                _uiState.update { it.copy(personalContext = ctx) }
            }
        }
    }

    fun setPersonalContext(value: String) {
        viewModelScope.launch {
            // Backstop: UI also enforces 500, but guard here for programmatic callers
            personalContextRepository.setPersonalContext(value.take(500))
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearTestTriggeredEmpty() {
        _uiState.value = _uiState.value.copy(testTriggeredEmpty = false)
    }

    fun clearTestTriggered() {
        _uiState.value = _uiState.value.copy(testTriggered = false)
    }

    fun refreshPermissions() {
        _uiState.update {
            it.copy(
                hasNotificationPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED,
                hasFineLocationPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED,
                hasBackgroundLocationPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED,
            )
        }
    }

    fun testTriggerNow() {
        viewModelScope.launch {
            val locationIds = geofenceManager.currentLocationIds.value
            val eligible = habitRepository.getEligibleHabits(locationIds)
            if (eligible.isEmpty()) {
                _uiState.value = _uiState.value.copy(testTriggeredEmpty = true)
                return@launch
            }
            executeTrigger(onComplete = {
                _uiState.value = _uiState.value.copy(testTriggered = true)
            })
        }
    }

    private fun executeTrigger(source: String? = null, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val trigger = TriggerEntity(
                scheduledAt = Instant.now(),
                status = TriggerStatus.SCHEDULED,
                source = source
            )
            val id = triggerRepository.insert(trigger)
            triggerPipeline.execute(id)
            onComplete?.invoke()
        }
    }
}
