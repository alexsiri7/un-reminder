package net.interstellarai.unreminder.ui.habit

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.db.LocationEntity
import net.interstellarai.unreminder.data.db.VariationEntity
import net.interstellarai.unreminder.data.db.WindowEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.LocationRepository
import net.interstellarai.unreminder.data.repository.VariationRepository
import net.interstellarai.unreminder.data.repository.WindowRepository
import net.interstellarai.unreminder.service.llm.AiStatus
import net.interstellarai.unreminder.service.llm.PromptGenerator
import net.interstellarai.unreminder.service.worker.RefillScheduler
import net.interstellarai.unreminder.service.worker.SpendCapExceededException
import net.interstellarai.unreminder.service.worker.WorkerAuthException
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sentry.Sentry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HabitEditUiState(
    val name: String = "",
    val levelDescriptions: List<String> = List(6) { "" },
    val selectedLocationIds: Set<Long> = emptySet(),
    val selectedWindowIds: Set<Long> = emptySet(),
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HabitEditViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val locationRepository: LocationRepository,
    private val windowRepository: WindowRepository,
    private val promptGenerator: PromptGenerator,
    private val refillScheduler: RefillScheduler,
    private val variationRepository: VariationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitEditUiState())
    val uiState: StateFlow<HabitEditUiState> = _uiState.asStateFlow()

    val allLocations: StateFlow<List<LocationEntity>> = locationRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWindows: StateFlow<List<WindowEntity>> = windowRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aiStatus: StateFlow<AiStatus> = promptGenerator.aiStatus

    private val _habitId = MutableStateFlow<Long?>(null)

    val unusedVariations: StateFlow<List<VariationEntity>> = _habitId
        .filterNotNull()
        .flatMapLatest { variationRepository.unusedVariationsFlow(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentlyUsedVariations: StateFlow<List<VariationEntity>> = _habitId
        .filterNotNull()
        .flatMapLatest { variationRepository.recentlyUsedFlow(it, RECENTLY_USED_LIMIT) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalVariationCount: StateFlow<Int> = _habitId
        .filterNotNull()
        .flatMapLatest { variationRepository.countTotalFlow(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    companion object {
        private const val TAG = "HabitEditViewModel"
        private const val RECENTLY_USED_LIMIT = 10
    }

    private var existingHabit: HabitEntity? = null

    fun loadHabit(id: Long) {
        viewModelScope.launch {
            _habitId.value = id
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val habit = habitRepository.getById(id).first()
                if (habit == null) {
                    Log.w(TAG, "loadHabit: habit $id not found")
                    return@launch
                }
                val locationIds = habitRepository.getLocationIds(id).toSet()
                val windowIds = habitRepository.getWindowIds(id).toSet()
                existingHabit = habit
                _uiState.value = HabitEditUiState(
                    name = habit.name,
                    levelDescriptions = habit.levelDescriptions,
                    selectedLocationIds = locationIds,
                    selectedWindowIds = windowIds,
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

    fun updateLevelDescription(index: Int, desc: String) {
        val updated = _uiState.value.levelDescriptions.toMutableList()
        updated[index] = desc
        _uiState.value = _uiState.value.copy(levelDescriptions = updated)
    }

    fun toggleLocation(locationId: Long) {
        val current = _uiState.value.selectedLocationIds
        val updated = if (locationId in current) current - locationId else current + locationId
        _uiState.value = _uiState.value.copy(selectedLocationIds = updated)
    }

    fun setAnywhere() {
        _uiState.value = _uiState.value.copy(selectedLocationIds = emptySet())
    }

    fun toggleWindow(windowId: Long) {
        val current = _uiState.value.selectedWindowIds
        val updated = if (windowId in current) current - windowId else current + windowId
        _uiState.value = _uiState.value.copy(selectedWindowIds = updated)
    }

    fun setAnyTime() {
        _uiState.value = _uiState.value.copy(selectedWindowIds = emptySet())
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
                            levelDescriptions = state.levelDescriptions,
                            active = state.active
                        )
                    )
                    existing.id
                } else {
                    habitRepository.insert(
                        HabitEntity(
                            name = state.name,
                            levelDescriptions = state.levelDescriptions,
                            active = state.active
                        )
                    )
                }
                habitRepository.setLocations(habitId, state.selectedLocationIds)
                habitRepository.setWindows(habitId, state.selectedWindowIds)
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
                        existing.levelDescriptions != state.levelDescriptions
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
        val fields = promptGenerator.generateHabitFields(_uiState.value.name)
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
            levelDescriptions = state.levelDescriptions
        )
        val locationName = if (state.selectedLocationIds.isEmpty()) {
            "Anywhere"
        } else {
            locationRepository.getByIds(state.selectedLocationIds)
                .joinToString(", ") { it.name }
                .ifBlank { "Anywhere" }
        }
        val text = promptGenerator.previewHabitNotification(tempHabit, locationName)
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

    fun deleteVariation(id: Long) {
        viewModelScope.launch {
            try {
                variationRepository.deleteById(id)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "deleteVariation: failed to delete variation $id", e)
            }
        }
    }
}
