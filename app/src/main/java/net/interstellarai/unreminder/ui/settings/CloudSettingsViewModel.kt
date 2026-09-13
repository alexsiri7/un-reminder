package net.interstellarai.unreminder.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.service.worker.RefillScheduler
import java.util.UUID
import javax.inject.Inject

/** Counts of the regenerate-all works enqueued by one button press. */
data class RegenerationProgress(val total: Int, val done: Int, val failed: Int) {
    val inFlight: Int get() = total - done - failed
}

data class CloudSettingsUiState(
    val errorMessage: String? = null,
    /** Non-null while a regeneration started from this screen has work outstanding. */
    val regeneration: RegenerationProgress? = null,
)

@HiltViewModel
class CloudSettingsViewModel @Inject constructor(
    private val refillScheduler: RefillScheduler,
    private val habitRepository: HabitRepository,
    private val workManager: WorkManager,
) : ViewModel() {

    companion object {
        private const val TAG = "CloudSettingsVM"
    }

    private val _uiState = MutableStateFlow(CloudSettingsUiState())
    val uiState: StateFlow<CloudSettingsUiState> = _uiState.asStateFlow()

    private var regenerateJob: Job? = null

    /**
     * Enqueues a replace-mode refill per active habit and never touches the pools itself: each
     * habit keeps firing from its old variants until its new batch lands, and keeps them if the
     * generation fails (#402).
     */
    fun regenerateAll() {
        if (regenerateJob?.isActive == true) return
        regenerateJob = viewModelScope.launch {
            val habits = try {
                habitRepository.getAllActive().first()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "regenerateAll: failed to load habits", e)
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to regenerate variants.")
                return@launch
            }
            if (habits.isEmpty()) {
                _uiState.value = _uiState.value.copy(errorMessage = "No active habits to regenerate.")
                return@launch
            }
            val ids = mutableListOf<UUID>()
            var failCount = 0
            for (habit in habits) {
                try {
                    ids += refillScheduler.enqueueRegenerate(habit.id)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "regenerateAll: failed to enqueue habit ${habit.id}", e)
                    failCount++
                }
            }
            if (failCount > 0) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to queue regeneration for $failCount habit(s)."
                )
            }
            if (ids.isEmpty()) return@launch
            _uiState.value = _uiState.value.copy(
                regeneration = RegenerationProgress(total = ids.size, done = 0, failed = 0)
            )
            observe(ids)
        }
    }

    /**
     * Follows this press's works until every one is terminal, then posts a one-shot summary and
     * stops — never resubscribes, so WorkManager pruning old specs cannot re-fire the snackbar.
     * A retrying work sits ENQUEUED and counts as in flight: nothing has failed and the old pool
     * is intact until it lands.
     */
    private suspend fun observe(ids: List<UUID>) {
        workManager.getWorkInfosFlow(WorkQuery.fromIds(ids))
            .map { infos ->
                RegenerationProgress(
                    total = ids.size,
                    done = infos.count { it.state == WorkInfo.State.SUCCEEDED },
                    failed = infos.count {
                        it.state == WorkInfo.State.FAILED || it.state == WorkInfo.State.CANCELLED
                    },
                )
            }
            .distinctUntilChanged()
            .transformWhile { progress ->
                emit(progress)
                progress.inFlight > 0
            }
            .catch { e ->
                if (e is CancellationException) throw e
                Log.e(TAG, "regenerateAll: lost track of regeneration progress", e)
                _uiState.value = _uiState.value.copy(
                    regeneration = null,
                    errorMessage = "Regeneration is still running in the background.",
                )
            }
            .collect { progress ->
                _uiState.value = if (progress.inFlight > 0) {
                    _uiState.value.copy(regeneration = progress)
                } else {
                    _uiState.value.copy(regeneration = null, errorMessage = summary(progress))
                }
            }
    }

    private fun summary(progress: RegenerationProgress): String =
        if (progress.failed == 0) "Regenerated ${progress.done} habit(s)."
        else "Regenerated ${progress.done} habit(s), ${progress.failed} failed — previous variants kept."

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
