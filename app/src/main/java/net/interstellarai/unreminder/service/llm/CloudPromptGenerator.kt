package net.interstellarai.unreminder.service.llm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.interstellarai.unreminder.BuildConfig
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.repository.WorkerTokenRepository
import net.interstellarai.unreminder.domain.model.AiHabitFields
import net.interstellarai.unreminder.service.worker.RequestyProxyClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudPromptGenerator @Inject constructor(
    private val requestyProxyClient: RequestyProxyClient,
    private val workerTokenRepository: WorkerTokenRepository,
) : PromptGenerator {

    override val aiStatus: StateFlow<AiStatus> = workerTokenRepository.token
        .map { token -> if (BuildConfig.WORKER_URL.isBlank() || token.isBlank()) AiStatus.Unavailable else AiStatus.Ready }
        .stateIn(CoroutineScope(SupervisorJob() + Dispatchers.IO), SharingStarted.Eagerly, AiStatus.Unavailable)

    override suspend fun generate(habit: HabitEntity, locationName: String, timeOfDay: String): String {
        // Cloud-pool path: this method is not used post-Phase-5 (TriggerPipeline reads directly
        // from VariationRepository). Kept for interface completeness.
        return habit.name
    }

    override suspend fun generateHabitFields(title: String): AiHabitFields {
        val (url, token) = requireCredentials()
        return requestyProxyClient.habitFields(title, url, token)
    }

    private suspend fun requireCredentials(): Pair<String, String> {
        val url = BuildConfig.WORKER_URL
        val token = workerTokenRepository.token.first()
        if (url.isBlank() || token.isBlank()) throw LlmUnavailableException()
        return url to token
    }
}
