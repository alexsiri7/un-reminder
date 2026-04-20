package net.interstellarai.unreminder.ui.habit

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.db.LocationEntity
import net.interstellarai.unreminder.data.repository.FeatureFlagsRepository
import net.interstellarai.unreminder.data.repository.HabitLevelDescriptionRepository
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.LocationRepository
import net.interstellarai.unreminder.data.repository.VariationRepository
import net.interstellarai.unreminder.data.repository.WorkerSettingsRepository
import net.interstellarai.unreminder.service.llm.AiStatus
import net.interstellarai.unreminder.service.llm.PromptGenerator
import net.interstellarai.unreminder.service.worker.RefillScheduler
import net.interstellarai.unreminder.service.worker.RequestyProxyClient
import net.interstellarai.unreminder.service.worker.SpendCapExceededException
import net.interstellarai.unreminder.service.worker.WorkerAuthException
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sentry.Sentry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HabitEditUiState(
    val name: String = "",
    val levelDescriptions: List<String> = List(6) { "" },
    val dedicationLevel: Int = 0,
    val autoAdjustLevel: Boolean = true,
    val selectedLocationIds: Set<Long> = emptySet(),
    val active: Boolean = true,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val isGeneratingFields: Boolean = false,
    val fieldsFlashing: Boolean = false,
    val previewNotification: String? = null,
    val showPreviewDialog: Boolean = false,
    val errorMessage: String? = null,
    val showSpendCapLink: Boolean = false
)

@HiltViewModel
class HabitEditViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val locationRepository: LocationRepository,
    private val levelDescRepo: HabitLevelDescriptionRepository,
    private val promptGenerator: PromptGenerator,
    private val requestyProxyClient: RequestyProxyClient,
    private val workerSettingsRepository: WorkerSettingsRepository,
    private val refillScheduler: RefillScheduler,
    private val variationRepository: VariationRepository,
    private val featureFlagsRepository: FeatureFlagsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitEditUiState())
    val uiState: StateFlow<HabitEditUiState> = _uiState.asStateFlow()

    val allLocations: StateFlow<List<LocationEntity>> = locationRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Pass-through of the model download fraction (0..1), or null when idle. */
    val downloadProgress: StateFlow<Float?> = promptGenerator.downloadProgress

    /** Cloud-aware AI status: when use_cloud_pool is ON, reflects worker configuration. */
    val aiStatus: StateFlow<AiStatus> = combine(
        featureFlagsRepository.useCloudPool,
        workerSettingsRepository.effectiveWorkerUrl,
        workerSettingsRepository.effectiveWorkerSecret,
        promptGenerator.aiStatus,
    ) { useCloud, url, secret, onDeviceStatus ->
        if (!useCloud) onDeviceStatus
        else if (url.isBlank() || secret.isBlank()) AiStatus.Unavailable
        // TODO: add pool-empty signal here once VariationRepository exposes
        // a reactive count flow. AiStatus.Empty UI branches are forward-compatible
        // scaffolding — they will never be reached until this is wired.
        else AiStatus.Ready
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), promptGenerator.aiStatus.value)

    companion object {
        private const val TAG = "HabitEditViewModel"
    }

    private var existingHabit: HabitEntity? = null

    fun loadHabit(id: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val habit = habitRepository.getById(id).first()
                if (habit == null) {
                    Log.w(TAG, "loadHabit: habit $id not found")
                    return@launch
                }
                val locationIds = habitRepository.getLocationIds(id).toSet()
                val descriptions = levelDescRepo.getDescriptionsForHabit(id)
                val descList = MutableList(6) { level -> descriptions.find { it.level == level }?.description ?: "" }
                existingHabit = habit
                _uiState.value = HabitEditUiState(
                    name = habit.name,
                    levelDescriptions = descList,
                    dedicationLevel = habit.dedicationLevel,
                    autoAdjustLevel = habit.autoAdjustLevel,
                    selectedLocationIds = locationIds,
                    active = habit.active
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "loadHabit: failed to load habit $id", e)
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to load habit.")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun updateName(name: String) { _uiState.value = _uiState.value.copy(name = name) }
    fun updateLevelDescription(level: Int, text: String) {
        val updated = _uiState.value.levelDescriptions.toMutableList().also { it[level] = text }
        _uiState.value = _uiState.value.copy(levelDescriptions = updated)
    }
    fun updateDedicationLevel(level: Int) {
        _uiState.value = _uiState.value.copy(dedicationLevel = level.coerceIn(0, 5))
    }
    fun updateAutoAdjustLevel(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoAdjustLevel = enabled)
    }

    fun toggleLocation(locationId: Long) {
        val current = _uiState.value.selectedLocationIds
        val updated = if (locationId in current) current - locationId else current + locationId
        _uiState.value = _uiState.value.copy(selectedLocationIds = updated)
    }

    fun setAnywhere() {
        _uiState.value = _uiState.value.copy(selectedLocationIds = emptySet())
    }

    fun updateActive(active: Boolean) { _uiState.value = _uiState.value.copy(active = active) }

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.name.isBlank()) return@launch
            val existing = existingHabit
            val habitId: Long
            try {
                habitId = if (existing != null) {
                    habitRepository.update(
                        existing.copy(
                            name = state.name,
                            fullDescription = state.levelDescriptions.getOrElse(5) { "" },
                            lowFloorDescription = state.levelDescriptions.getOrElse(0) { "" },
                            dedicationLevel = state.dedicationLevel,
                            autoAdjustLevel = state.autoAdjustLevel,
                            active = state.active
                        )
                    )
                    existing.id
                } else {
                    habitRepository.insert(
                        HabitEntity(
                            name = state.name,
                            fullDescription = state.levelDescriptions.getOrElse(5) { "" },
                            lowFloorDescription = state.levelDescriptions.getOrElse(0) { "" },
                            dedicationLevel = state.dedicationLevel,
                            autoAdjustLevel = state.autoAdjustLevel,
                            active = state.active
                        )
                    )
                }
                habitRepository.setLocations(habitId, state.selectedLocationIds)
                levelDescRepo.replaceForHabit(habitId, state.levelDescriptions)
                _uiState.value = _uiState.value.copy(isSaved = true)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "save failed", e)
                _uiState.value = _uiState.value.copy(errorMessage = "Save failed — try again.")
                return@launch
            }

            // Post-save refill scheduling — best-effort, does not affect isSaved
            try {
                if (existing != null) {
                    val promptChanged = existing.name != state.name ||
                        existing.fullDescription != state.levelDescriptions.getOrElse(5) { "" } ||
                        existing.lowFloorDescription != state.levelDescriptions.getOrElse(0) { "" }
                    if (promptChanged) {
                        variationRepository.deleteForHabit(habitId)
                        refillScheduler.enqueueForHabit(habitId)
                    }
                } else {
                    refillScheduler.enqueueForHabit(habitId)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "refill scheduling after save failed — variants may be stale", e)
            }
        }
    }

    private fun launchWithAi(errorMsg: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingFields = true, errorMessage = null)
            try {
                block()
            } catch (e: WorkerAuthException) {
                _uiState.value = _uiState.value.copy(
                    isGeneratingFields = false,
                    errorMessage = "Wrong worker secret \u2014 check Settings.",
                )
            } catch (e: SpendCapExceededException) {
                _uiState.value = _uiState.value.copy(
                    isGeneratingFields = false,
                    // errorMessage intentionally omitted — showSpendCapLink snackbar carries the full message + action
                    showSpendCapLink = true,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "launchWithAi failed", e)
                Sentry.captureException(e) { scope ->
                    scope.setTag("component", "ai-ui")
                }
                _uiState.value = _uiState.value.copy(isGeneratingFields = false, errorMessage = errorMsg)
            }
        }
    }

    fun autofillWithAi() = launchWithAi("AI unavailable — fill in manually.") {
        val url = workerSettingsRepository.effectiveWorkerUrl.first()
        val secret = workerSettingsRepository.effectiveWorkerSecret.first()
        val fields = if (url.isNotBlank() && secret.isNotBlank()) {
            requestyProxyClient.habitFields(_uiState.value.name, url, secret)
        } else {
            promptGenerator.generateHabitFields(_uiState.value.name)
        }
        _uiState.value = _uiState.value.copy(
            levelDescriptions = fields.levelDescriptions,
            isGeneratingFields = false,
            fieldsFlashing = true
        )
    }

    fun previewNotification() = launchWithAi("AI unavailable — preview not available.") {
        val state = _uiState.value
        val tempHabit = HabitEntity(
            name = state.name,
            fullDescription = state.levelDescriptions.getOrElse(5) { "" },
            lowFloorDescription = state.levelDescriptions.getOrElse(0) { "" },
            dedicationLevel = state.dedicationLevel,
            autoAdjustLevel = state.autoAdjustLevel
        )
        val locationName = if (state.selectedLocationIds.isEmpty()) {
            "Anywhere"
        } else {
            locationRepository.getByIds(state.selectedLocationIds)
                .joinToString(", ") { it.name }
                .ifBlank { "Anywhere" }
        }
        val url = workerSettingsRepository.effectiveWorkerUrl.first()
        val secret = workerSettingsRepository.effectiveWorkerSecret.first()
        val text = if (url.isNotBlank() && secret.isNotBlank()) {
            requestyProxyClient.preview(tempHabit, locationName, url, secret)
        } else {
            promptGenerator.previewHabitNotification(tempHabit, locationName)
        }
        _uiState.value = _uiState.value.copy(
            isGeneratingFields = false,
            previewNotification = text,
            showPreviewDialog = true
        )
    }

    fun dismissPreviewDialog() {
        _uiState.value = _uiState.value.copy(showPreviewDialog = false, previewNotification = null)
    }
    fun clearError() { _uiState.value = _uiState.value.copy(errorMessage = null) }
    fun clearFieldsFlash() { _uiState.value = _uiState.value.copy(fieldsFlashing = false) }
    fun clearSpendCapLink() { _uiState.value = _uiState.value.copy(showSpendCapLink = false) }
}
