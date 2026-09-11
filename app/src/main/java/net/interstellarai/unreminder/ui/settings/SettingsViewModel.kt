package net.interstellarai.unreminder.ui.settings

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.repository.EveningInvitationRepository
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.PersonalContextRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import net.interstellarai.unreminder.service.geofence.RegistrationHealth
import net.interstellarai.unreminder.service.trigger.TriggerPipeline
import net.interstellarai.unreminder.worker.EveningInvitationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject

data class SettingsUiState(
    val hasNotificationPermission: Boolean = false,
    val hasFineLocationPermission: Boolean = false,
    val hasBackgroundLocationPermission: Boolean = false,
    val backgroundRestricted: Boolean = false,
    val registrationHealth: RegistrationHealth? = null,
    val testTriggered: Boolean = false,
    val testTriggeredEmpty: Boolean = false,
    val errorMessage: String? = null,
    val personalContext: String = "",
    val eveningInvitationEnabled: Boolean = true,
    val eveningInvitationTime: LocalTime = EveningInvitationRepository.DEFAULT_TIME,
) {
    val locationTracking: LocationTrackingStatus
        get() = LocationTrackingStatus.of(registrationHealth, backgroundRestricted)
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val triggerPipeline: TriggerPipeline,
    private val triggerRepository: TriggerRepository,
    private val habitRepository: HabitRepository,
    private val geofenceManager: GeofenceManager,
    private val personalContextRepository: PersonalContextRepository,
    private val eveningInvitationRepository: EveningInvitationRepository,
    private val eveningInvitationScheduler: EveningInvitationScheduler,
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
        viewModelScope.launch {
            geofenceManager.registrationHealth.collect { health ->
                _uiState.update { it.copy(registrationHealth = health) }
            }
        }
        viewModelScope.launch {
            eveningInvitationRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        eveningInvitationEnabled = settings.enabled,
                        eveningInvitationTime = settings.time,
                    )
                }
            }
        }
    }

    fun setEveningInvitationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            eveningInvitationRepository.setEnabled(enabled)
            eveningInvitationScheduler.reschedule()
        }
    }

    fun setEveningInvitationTime(time: LocalTime) {
        viewModelScope.launch {
            eveningInvitationRepository.setTime(time)
            eveningInvitationScheduler.reschedule()
        }
    }

    fun setPersonalContext(value: String) {
        viewModelScope.launch {
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
                backgroundRestricted =
                    context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true,
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
