package net.interstellarai.unreminder.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.util.Log
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.repository.ActiveModelRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.llm.AiStatus
import net.interstellarai.unreminder.service.llm.ModelCatalog
import net.interstellarai.unreminder.service.llm.ModelDescriptor
import net.interstellarai.unreminder.service.llm.PromptGenerator
import net.interstellarai.unreminder.service.trigger.TriggerPipeline
import net.interstellarai.unreminder.worker.DailySchedulerWorker
import net.interstellarai.unreminder.worker.ModelDownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import javax.inject.Inject

data class SettingsUiState(
    val hasNotificationPermission: Boolean = false,
    val hasFineLocationPermission: Boolean = false,
    val hasBackgroundLocationPermission: Boolean = false,
    val hasExactAlarmPermission: Boolean = false,
    val testTriggered: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val triggerPipeline: TriggerPipeline,
    private val triggerRepository: TriggerRepository,
    private val activeModelRepository: ActiveModelRepository,
    private val promptGenerator: PromptGenerator,
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Full catalog — rendered as a list in the Settings "AI model" section. */
    val catalog: List<ModelDescriptor> = ModelCatalog.all

    /** Currently-active descriptor, reflected live from DataStore. */
    val activeModel: StateFlow<ModelDescriptor> = activeModelRepository.active
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelCatalog.default)

    /** Coarse AI readiness — drives the status label next to the active model row. */
    val aiStatus: StateFlow<AiStatus> = promptGenerator.aiStatus

    /** 0..1 download fraction, or null when no download is active. */
    val downloadProgress: StateFlow<Float?> = promptGenerator.downloadProgress

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

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

    /**
     * Write the user's new selection + re-enqueue the download worker for
     * the freshly selected model. The previous model's file is NOT deleted
     * here; [deleteModelFile] is the separate action for that so users can
     * confirm in the dialog.
     */
    fun selectModel(id: String) {
        viewModelScope.launch {
            activeModelRepository.setActive(id)
            val desc = ModelCatalog.byId(id) ?: return@launch
            // REPLACE so a currently-running download for a different model
            // gets cancelled — otherwise the unique-work guard would silently
            // drop the new enqueue. WorkManager cancels the old worker; its
            // partial `.tmp` file stays on disk under the old filename (we
            // can clean it up via deleteModelFile()).
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL_ID to desc.id))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ModelDownloadWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }

    /**
     * Delete on-disk files for any catalog model that isn't [keep]. Safe to
     * call any time — missing files are just a no-op. Used by the "Delete now"
     * branch of the confirmation dialog so users can free up ~2 GB when
     * switching between models.
     */
    fun deleteOtherModelFiles(keep: ModelDescriptor) {
        viewModelScope.launch {
            ModelCatalog.all
                .filter { it.id != keep.id }
                .forEach { other ->
                    File(context.filesDir, other.fileName).takeIf { it.exists() }?.delete()
                    File(context.filesDir, "${other.fileName}.tmp").takeIf { it.exists() }?.delete()
                }
        }
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
