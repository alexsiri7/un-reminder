package net.interstellarai.unreminder.domain.model

data class AiHabitFields(
    val levelDescriptions: List<String>,
) {
    val fullDescription: String get() = levelDescriptions.getOrElse(5) { "" }
    val lowFloorDescription: String get() = levelDescriptions.getOrElse(0) { "" }
}
