package com.alexsiri7.unreminder.service.llm

import com.alexsiri7.unreminder.data.db.HabitEntity
import com.alexsiri7.unreminder.domain.model.AiHabitFields

interface PromptGenerator {
    suspend fun initialize()
    suspend fun generate(habit: HabitEntity, locationName: String, timeOfDay: String): String
    suspend fun generateHabitFields(title: String): AiHabitFields
    suspend fun previewHabitNotification(habit: HabitEntity, locationName: String = "Anywhere"): String
}
