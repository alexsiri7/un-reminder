package net.interstellarai.unreminder.ui.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.repository.FeatureFlagsRepository
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.WorkerSettingsRepository
import net.interstellarai.unreminder.service.llm.AiStatus
import net.interstellarai.unreminder.service.llm.PromptGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HabitListViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val promptGenerator: PromptGenerator,
    private val featureFlagsRepository: FeatureFlagsRepository,
    private val workerSettingsRepository: WorkerSettingsRepository,
) : ViewModel() {

    val habits: StateFlow<List<HabitEntity>> = habitRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Fractional 0..1 progress of the on-device model download, or null when not downloading. */
    val downloadProgress: StateFlow<Float?> = promptGenerator.downloadProgress

    /** Cloud-aware AI readiness — drives the banner variant. */
    val aiStatus: StateFlow<AiStatus> = combine(
        featureFlagsRepository.useCloudPool,
        workerSettingsRepository.effectiveWorkerUrl,
        workerSettingsRepository.effectiveWorkerSecret,
    ) { useCloud, url, secret ->
        if (!useCloud) promptGenerator.aiStatus.value
        else if (url.isBlank() && secret.isBlank()) AiStatus.Unavailable
        else AiStatus.Ready
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), promptGenerator.aiStatus.value)

    fun toggleActive(habit: HabitEntity) {
        viewModelScope.launch {
            habitRepository.update(habit.copy(active = !habit.active))
        }
    }

    fun delete(habit: HabitEntity) {
        viewModelScope.launch {
            habitRepository.delete(habit)
        }
    }

    /** Wired to the retry action on the "AI unavailable" banner variant. */
    fun retryModelDownload() {
        promptGenerator.retryModelDownload()
    }
}
