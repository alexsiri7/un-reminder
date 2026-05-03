package net.interstellarai.unreminder.service.llm

import net.interstellarai.unreminder.BuildConfig
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.domain.model.AiHabitFields
import net.interstellarai.unreminder.service.worker.RequestyProxyClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudPromptGenerator @Inject constructor(
    private val requestyProxyClient: RequestyProxyClient,
) : PromptGenerator {

    override val aiStatus: StateFlow<AiStatus> = MutableStateFlow(
        if (BuildConfig.WORKER_URL.isBlank() || BuildConfig.WORKER_SECRET.isBlank()) {
            AiStatus.Unavailable
        } else {
            AiStatus.Ready
        }
    )

    override suspend fun generate(habit: HabitEntity, locationName: String, timeOfDay: String): String {
        // Cloud-pool path: this method is not used post-Phase-5 (TriggerPipeline reads directly
        // from VariationRepository). Kept for interface completeness.
        return habit.name
    }

    override suspend fun generateHabitFields(title: String): AiHabitFields {
        val (url, secret) = requireCredentials()
        return requestyProxyClient.habitFields(title, url, secret)
    }

    private fun requireCredentials(): Pair<String, String> {
        val url = BuildConfig.WORKER_URL
        val secret = BuildConfig.WORKER_SECRET
        if (url.isBlank() || secret.isBlank()) throw LlmUnavailableException()
        return url to secret
    }
}
