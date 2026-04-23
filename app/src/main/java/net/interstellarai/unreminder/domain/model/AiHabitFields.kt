package net.interstellarai.unreminder.domain.model

data class AiHabitFields(
    val levelDescriptions: List<String> // size = 6, index = level (0..5); may be blank for unset levels
)
