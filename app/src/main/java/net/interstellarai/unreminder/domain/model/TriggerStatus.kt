package net.interstellarai.unreminder.domain.model

enum class TriggerStatus {
    SCHEDULED,
    FIRED,
    COMPLETED,
    COMPLETED_FULL,
    COMPLETED_LOW_FLOOR,
    DISMISSED
}
