package net.interstellarai.unreminder.widget

import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.trigger.DismissalTracker
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Records a "did it" tap on the widget the same way the Now menu records one. */
@Singleton
class WidgetCompletionRecorder @Inject constructor(
    private val triggerRepository: TriggerRepository,
    private val dismissalTracker: DismissalTracker,
) {
    suspend fun complete(habitId: Long) {
        val now = Instant.now()
        val triggerId = triggerRepository.insert(
            TriggerEntity(
                habitId = habitId,
                scheduledAt = now,
                firedAt = now,
                status = TriggerStatus.COMPLETED,
                source = SOURCE_WIDGET,
            )
        )
        dismissalTracker.onCompleted(triggerId)
    }

    companion object {
        const val SOURCE_WIDGET = "widget"
    }
}
