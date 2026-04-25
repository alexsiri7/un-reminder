package net.interstellarai.unreminder.service.llm

import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.domain.model.AiHabitFields
import kotlinx.coroutines.flow.StateFlow

interface PromptGenerator {
    /** Current high-level AI readiness. */
    val aiStatus: StateFlow<AiStatus>

    // TODO: generate() is unused post-Phase-5 (TriggerPipeline reads from
    // VariationRepository directly). Remove once confirmed dead project-wide.
    suspend fun generate(habit: HabitEntity, locationName: String, timeOfDay: String): String
    suspend fun generateHabitFields(title: String): AiHabitFields
    suspend fun previewHabitNotification(habit: HabitEntity, locationName: String = "Anywhere"): String
}

sealed interface AiStatus {
    /** Worker URL / secret not configured — AI permanently off. */
    object Unavailable : AiStatus

    /** Cloud worker configured — generation calls will work. */
    object Ready : AiStatus

    /**
     * Cloud pool for the current habit has no unused variants —
     * a refill has been enqueued.
     */
    object Empty : AiStatus
}
