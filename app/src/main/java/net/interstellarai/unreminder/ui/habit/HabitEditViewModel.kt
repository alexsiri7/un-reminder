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
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.data.repository.VariationRepository
import net.interstellarai.unreminder.data.repository.WindowRepository
import net.interstellarai.unreminder.domain.AvailabilityStatus
import net.interstellarai.unreminder.domain.HabitAvailabilityService
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import net.interstellarai.unreminder.service.llm.AiStatus
import net.interstellarai.unreminder.service.llm.LlmUnavailableException
import net.interstellarai.unreminder.service.llm.PromptGenerator
import net.interstellarai.unreminder.service.worker.RefillScheduler
import net.interstellarai.unreminder.service.worker.SpendCapExceededException
import net.interstellarai.unreminder.service.worker.WorkerAuthException
import net.interstellarai.unreminder.service.worker.WorkerError
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
import kotlinx.coroutines.flow.collect
import java.io.IOException
import java.time.Instant
import javax.inject.Inject

data class HabitEditUiState(
    val name: String = "",
    val descriptionLadder: List<String> = List(6) { "" },
    val dedicationLevel: Int = 2,
    val autoAdjustLevel: Boolean = true,
    val dailyLimit: Int = 1,
    val cooldownMinutes: Int = 180,
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
    val showSpendCapLink: Boolean = false,
    val availabilityStatus: AvailabilityStatus = AvailabilityStatus.NewHabit
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
    private val geofenceManager: GeofenceManager,
    private val triggerRepository: TriggerRepository,
    private val availabilityService: HabitAvailabilityService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitEditUiState())
    val uiState: StateFlow<HabitEditUiState> = _uiState.asStateFlow()

    val allLocations: StateFlow<List<LocationEntity>> = locationRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allWindows: StateFlow<List<WindowEntity>> = windowRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    // Holds the most-recently-loaded habit for reactive availability recomputation.
    private val _loadedHabit = MutableStateFlow<HabitEntity?>(null)

    init {
        // Reactively recompute availability whenever the loaded habit OR the current geofence set changes.
        viewModelScope.launch {
            geofenceManager.currentLocationIds.collect { _ ->
                val habit = _loadedHabit.value ?: return@collect
                val availability = try {
                    availabilityService.computeAvailability(habit)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "reactive availability recompute failed for habit ${habit.id} — hiding badge", e)
                    AvailabilityStatus.NewHabit
                }
                _uiState.value = _uiState.value.copy(availabilityStatus = availability)
            }
        }
    }

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
                // Availability is informational; don't let its failure block the edit screen.
                val availability = try {
                    availabilityService.computeAvailability(habit, locationIds, windowIds)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "loadHabit: availability computation failed for habit $id — hiding badge", e)
                    AvailabilityStatus.NewHabit
                }
                _uiState.value = HabitEditUiState(
                    name = habit.name,
                    descriptionLadder = habit.descriptionLadder,
                    dedicationLevel = habit.dedicationLevel,
                    autoAdjustLevel = habit.autoAdjustLevel,
                    dailyLimit = habit.dailyLimit,
                    cooldownMinutes = habit.cooldownMinutes,
                    selectedLocationIds = locationIds,
                    selectedWindowIds = windowIds,
                    active = habit.active,
                    availabilityStatus = availability
                )
                // Publish to the reactive collector AFTER state is settled so a mid-load
                // geofence emission cannot be clobbered by this assignment.
                _loadedHabit.value = habit
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
    fun updateDescriptionAtLevel(level: Int, text: String) {
        val ladder = _uiState.value.descriptionLadder.toMutableList()
        if (level in ladder.indices) ladder[level] = text
        _uiState.value = _uiState.value.copy(descriptionLadder = ladder)
    }
    fun updateDedicationLevel(level: Int) { _uiState.value = _uiState.value.copy(dedicationLevel = level) }
    fun updateAutoAdjustLevel(enabled: Boolean) { _uiState.value = _uiState.value.copy(autoAdjustLevel = enabled) }
    fun updateDailyLimit(limit: Int) { _uiState.value = _uiState.value.copy(dailyLimit = limit) }
    fun updateCooldownMinutes(minutes: Int) { _uiState.value = _uiState.value.copy(cooldownMinutes = minutes) }

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
                            descriptionLadder = state.descriptionLadder,
                            dedicationLevel = state.dedicationLevel,
                            autoAdjustLevel = state.autoAdjustLevel,
                            dailyLimit = state.dailyLimit,
                            cooldownMinutes = state.cooldownMinutes,
                            active = state.active
                        )
                    )
                    existing.id
                } else {
                    habitRepository.insert(
                        HabitEntity(
                            name = state.name,
                            descriptionLadder = state.descriptionLadder,
                            dedicationLevel = state.dedicationLevel,
                            autoAdjustLevel = state.autoAdjustLevel,
                            dailyLimit = state.dailyLimit,
                            cooldownMinutes = state.cooldownMinutes,
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
                        existing.descriptionLadder != state.descriptionLadder
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
                _uiState.value = _uiState.value.copy(errorMessage = "Wrong worker secret — check Settings.")
            } catch (e: SpendCapExceededException) {
                // showSpendCapLink snackbar carries the message+action; errorMessage intentionally not set
                _uiState.value = _uiState.value.copy(showSpendCapLink = true)
            } catch (e: LlmUnavailableException) {
                _uiState.value = _uiState.value.copy(errorMessage = errorMsg)
            } catch (e: WorkerError) {
                if (e.isServerError()) {
                    _uiState.value = _uiState.value.copy(errorMessage = "Service temporarily unavailable — please try again.")
                } else {
                    Sentry.captureException(e) { scope -> scope.setTag("component", "ai-ui") }
                    _uiState.value = _uiState.value.copy(errorMessage = errorMsg)
                }
            } catch (e: IOException) {
                // Transient network failure (offline, DNS, timeout). Show toast, don't alert Sentry.
                Log.w(TAG, "launchWithAi network failure", e)
                _uiState.value = _uiState.value.copy(errorMessage = "AI unavailable — check your connection.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "launchWithAi failed", e)
                Sentry.captureException(e) { scope -> scope.setTag("component", "ai-ui") }
                _uiState.value = _uiState.value.copy(errorMessage = errorMsg)
            } finally {
                _uiState.value = _uiState.value.copy(isGeneratingFields = false)
            }
        }
    }

    fun autofillWithAi() = launchWithAi("AI unavailable — fill in manually.") {
        val fields = promptGenerator.generateHabitFields(_uiState.value.name)
        _uiState.value = _uiState.value.copy(
            descriptionLadder = fields.descriptionLadder,
            fieldsFlashing = true
        )
    }

    fun previewNotification() {
        viewModelScope.launch {
            try {
                val habitId = _habitId.value
                if (habitId == null) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Save the habit first to preview a real notification."
                    )
                    return@launch
                }
                val variation = variationRepository.peekUnused(habitId)
                if (variation == null) {
                    Log.w(TAG, "previewNotification: no variation available for habit $habitId")
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Notifications are still being generated — try again in a moment."
                    )
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    previewNotification = variation,
                    showPreviewDialog = true
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "previewNotification failed", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Preview unavailable — try again."
                )
            }
        }
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
