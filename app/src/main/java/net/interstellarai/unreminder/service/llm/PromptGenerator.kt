package net.interstellarai.unreminder.service.llm

import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.domain.model.AiHabitFields

interface PromptGenerator {
    suspend fun initialize()
    suspend fun generate(habit: HabitEntity, locationName: String, timeOfDay: String): String
    suspend fun generateHabitFields(title: String): AiHabitFields
    suspend fun previewHabitNotification(habit: HabitEntity, locationName: String = "Anywhere"): String
}
