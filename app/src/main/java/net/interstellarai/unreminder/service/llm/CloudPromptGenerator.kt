package net.interstellarai.unreminder.service.llm

import android.util.Log
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.repository.WorkerSettingsRepository
import net.interstellarai.unreminder.domain.model.AiHabitFields
import net.interstellarai.unreminder.service.worker.RequestyProxyClient
import net.interstellarai.unreminder.service.worker.WorkerAuthException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudPromptGenerator @Inject constructor(
    private val requestyProxyClient: RequestyProxyClient,
    private val workerSettingsRepository: WorkerSettingsRepository,
) : PromptGenerator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val aiStatus: StateFlow<AiStatus> = combine(
        workerSettingsRepository.effectiveWorkerUrl,
        workerSettingsRepository.effectiveWorkerSecret,
    ) { url, secret ->
        if (url.isBlank() || secret.isBlank()) AiStatus.Unavailable else AiStatus.Ready
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), AiStatus.Unavailable)

    override suspend fun generate(habit: HabitEntity, locationName: String, timeOfDay: String): String {
        // generate() is dead post-Phase-5 — TriggerPipeline reads from VariationRepository
        // directly. Remove this method when cleaning up PromptGenerator interface.
        Log.w(TAG, "generate() stub called — method is dead post-Phase-5, returning habit.name")
        return habit.name
    }

    override suspend fun generateHabitFields(title: String): AiHabitFields {
        val url = workerSettingsRepository.effectiveWorkerUrl.first()
        val secret = workerSettingsRepository.effectiveWorkerSecret.first()
        if (url.isBlank() || secret.isBlank()) throw WorkerAuthException()
        return requestyProxyClient.habitFields(title, url, secret)
    }

    override suspend fun previewHabitNotification(habit: HabitEntity, locationName: String): String {
        val url = workerSettingsRepository.effectiveWorkerUrl.first()
        val secret = workerSettingsRepository.effectiveWorkerSecret.first()
        if (url.isBlank() || secret.isBlank()) throw WorkerAuthException()
        return requestyProxyClient.preview(habit, locationName, url, secret)
    }

    companion object {
        private const val TAG = "CloudPromptGenerator"
    }
}
