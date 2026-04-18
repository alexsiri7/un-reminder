package com.alexsiri7.unreminder.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.alexsiri7.unreminder.data.db.TriggerEntity
import com.alexsiri7.unreminder.data.repository.TriggerRepository
import com.alexsiri7.unreminder.domain.model.TriggerStatus
import com.alexsiri7.unreminder.service.trigger.TriggerPipeline
import com.alexsiri7.unreminder.worker.DailySchedulerWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class SettingsUiState(
    val hasNotificationPermission: Boolean = false,
    val hasFineLocationPermission: Boolean = false,
    val hasBackgroundLocationPermission: Boolean = false,
    val hasExactAlarmPermission: Boolean = false,
    val testTriggered: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val triggerPipeline: TriggerPipeline,
    private val triggerRepository: TriggerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun refreshPermissions() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        _uiState.value = SettingsUiState(
            hasNotificationPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED,
            hasFineLocationPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED,
            hasBackgroundLocationPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED,
            hasExactAlarmPermission = alarmManager.canScheduleExactAlarms()
        )
    }

    fun testTriggerNow() = executeTrigger(onComplete = {
        _uiState.value = _uiState.value.copy(testTriggered = true)
    })

    fun surpriseMe() = executeTrigger(source = "MANUAL")

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

    fun regenerateTriggers() {
        viewModelScope.launch {
            triggerRepository.deleteAllScheduled()
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<DailySchedulerWorker>().build()
            )
        }
    }
}
