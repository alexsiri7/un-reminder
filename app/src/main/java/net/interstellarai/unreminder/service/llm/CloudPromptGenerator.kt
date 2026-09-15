package net.interstellarai.unreminder.service.llm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.repository.WorkerTokenRepository
import net.interstellarai.unreminder.di.ApplicationScope
import net.interstellarai.unreminder.di.WorkerUrl
import net.interstellarai.unreminder.domain.model.AiHabitFields
import net.interstellarai.unreminder.service.worker.RequestyProxyClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudPromptGenerator @Inject constructor(
    private val requestyProxyClient: RequestyProxyClient,
    private val workerTokenRepository: WorkerTokenRepository,
    @WorkerUrl private val workerUrl: String,
    @ApplicationScope applicationScope: CoroutineScope,
) : PromptGenerator {

    override val aiStatus: StateFlow<AiStatus> = workerTokenRepository.token
        .map { token -> if (workerUrl.isBlank() || token.isBlank()) AiStatus.Unavailable else AiStatus.Ready }
        .stateIn(applicationScope, SharingStarted.Eagerly, AiStatus.Unavailable)

    override suspend fun generate(habit: HabitEntity, locationName: String, timeOfDay: String): String {
        // Cloud-pool path: this method is not used post-Phase-5 (TriggerPipeline reads directly
        // from VariationRepository). Kept for interface completeness.
        return habit.name
    }

    override suspend fun generateHabitFields(title: String): AiHabitFields =
        requestyProxyClient.habitFields(title, workerUrl, requireToken())

    private suspend fun requireToken(): String {
        val token = workerTokenRepository.token.first()
        if (workerUrl.isBlank() || token.isBlank()) throw LlmUnavailableException()
        return token
    }
}
