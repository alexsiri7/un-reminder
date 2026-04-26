package net.interstellarai.unreminder.service.llm

import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.repository.WorkerSettingsRepository
import net.interstellarai.unreminder.domain.model.AiHabitFields
import net.interstellarai.unreminder.service.worker.RequestyProxyClient
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
        // Cloud-pool path: this method is not used post-Phase-5 (TriggerPipeline reads directly
        // from VariationRepository). Kept for interface completeness.
        return habit.name
    }

    override suspend fun generateHabitFields(title: String): AiHabitFields {
        val (url, secret) = requireCredentials()
        return requestyProxyClient.habitFields(title, url, secret)
    }

    override suspend fun previewHabitNotification(habit: HabitEntity, locationName: String): String {
        val (url, secret) = requireCredentials()
        return requestyProxyClient.preview(habit, locationName, url, secret)
    }

    private suspend fun requireCredentials(): Pair<String, String> {
        val url = workerSettingsRepository.effectiveWorkerUrl.first()
        val secret = workerSettingsRepository.effectiveWorkerSecret.first()
        if (url.isBlank() || secret.isBlank()) throw IllegalStateException("LLM unavailable")
        return url to secret
    }
}
