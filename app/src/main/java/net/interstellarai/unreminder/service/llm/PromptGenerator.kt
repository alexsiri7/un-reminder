package net.interstellarai.unreminder.service.llm

import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.domain.model.AiHabitFields
import kotlinx.coroutines.flow.StateFlow

interface PromptGenerator {
    /**
     * Fractional progress (0.0..1.0) of the on-device model download, or `null`
     * when no download is in flight. UI layers observe this to show a banner /
     * disable Autofill while the ~2 GB model is streaming down.
     */
    val downloadProgress: StateFlow<Float?>

    /**
     * Current high-level AI readiness — distinguishes "still downloading" from
     * "built without a model URL" from "ready to generate". The habit editor
     * uses this to pick the right Autofill-button helper copy.
     */
    val aiStatus: StateFlow<AiStatus>

    suspend fun initialize()
    suspend fun generate(habit: HabitEntity, locationName: String, timeOfDay: String): String
    suspend fun generateHabitFields(title: String): AiHabitFields
    suspend fun previewHabitNotification(habit: HabitEntity, locationName: String = "Anywhere"): String

    /**
     * Re-enqueue the model download worker (idempotent via unique work).
     * Called from the "AI unavailable — tap to retry" banner action.
     */
    fun retryModelDownload()
}

/**
 * Coarse readiness states surfaced to UI. `Downloading` carries the current
 * fraction so callers that want a single StateFlow for the button state can
 * read it here too; `downloadProgress` remains the canonical progress source.
 */
sealed interface AiStatus {
    /** Build was shipped without a real MODEL_CDN_URL — AI permanently off. */
    object Unavailable : AiStatus

    /** Model not present and download is in flight. */
    data class Downloading(val fraction: Float) : AiStatus

    /** Model present, engine initialised — generation calls will work. */
    object Ready : AiStatus

    /**
     * Model file present (or download reached SUCCEEDED) but engine init failed.
     * User should see a retry-style affordance.
     */
    object Failed : AiStatus

    /**
     * Cloud pool mode is on but the pool for the current habit has no unused
     * variants — `TriggerPipeline` fell back to `habit.title`. A refill has
     * been enqueued; the next trigger should find variants ready.
     */
    object Empty : AiStatus

    /** Initial / not-yet-started state. */
    object Idle : AiStatus
}
