package net.interstellarai.unreminder.widget

import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.data.repository.VariationRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.trigger.DismissalTracker
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records a pull-sourced completion — a "did it" on the widget, or on the variant view opened
 * from the Now page or widget — the same way the Now menu records one.
 */
@Singleton
class PullCompletionRecorder @Inject constructor(
    private val triggerRepository: TriggerRepository,
    private val variationRepository: VariationRepository,
    private val dismissalTracker: DismissalTracker,
) {
    /**
     * [variationId] is the variant that was showing, so it is consumed and not shown again;
     * [source] is stamped on the trigger as [TriggerEntity.source].
     */
    suspend fun complete(habitId: Long, variationId: Long?, source: String) {
        val now = Instant.now()
        val triggerId = triggerRepository.insert(
            TriggerEntity(
                habitId = habitId,
                scheduledAt = now,
                firedAt = now,
                status = TriggerStatus.COMPLETED,
                source = source,
            )
        )
        dismissalTracker.onCompleted(triggerId)
        variationId?.let { variationRepository.markConsumed(it) }
    }

    companion object {
        const val SOURCE_WIDGET = "widget"
    }
}
