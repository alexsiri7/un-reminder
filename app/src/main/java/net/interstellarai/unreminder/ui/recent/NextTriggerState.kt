package net.interstellarai.unreminder.ui.recent

import java.time.Instant

sealed interface NextTriggerState {
    data object NotScheduled : NextTriggerState
    data class Scheduled(val at: Instant) : NextTriggerState
}
