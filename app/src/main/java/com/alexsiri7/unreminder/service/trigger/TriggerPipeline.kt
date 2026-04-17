package com.alexsiri7.unreminder.service.trigger

import com.alexsiri7.unreminder.data.repository.HabitRepository
import com.alexsiri7.unreminder.data.repository.TriggerRepository
import com.alexsiri7.unreminder.domain.model.LocationTag
import com.alexsiri7.unreminder.domain.model.TriggerStatus
import com.alexsiri7.unreminder.service.geofence.GeofenceManager
import com.alexsiri7.unreminder.service.llm.PromptGenerator
import com.alexsiri7.unreminder.service.notification.NotificationHelper
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TriggerPipeline @Inject constructor(
    private val habitRepository: HabitRepository,
    private val triggerRepository: TriggerRepository,
    private val geofenceManager: GeofenceManager,
    private val promptGenerator: PromptGenerator,
    private val notificationHelper: NotificationHelper
) {
    suspend fun execute(triggerId: Long) {
        val trigger = triggerRepository.getById(triggerId) ?: return
        if (trigger.status != TriggerStatus.SCHEDULED) return

        val locationTag = geofenceManager.currentLocationTag

        val eligibleHabits = habitRepository.getEligibleHabits(locationTag)
        if (eligibleHabits.isEmpty()) return

        val habit = eligibleHabits.random()
        val timeOfDay = resolveTimeOfDay()
        val prompt = promptGenerator.generate(habit, locationTag, timeOfDay)

        triggerRepository.updateFired(
            id = triggerId,
            habitId = habit.id,
            prompt = prompt
        )

        notificationHelper.postTriggerNotification(
            triggerId = triggerId,
            promptText = prompt,
            habitName = habit.name
        )
    }

    private fun resolveTimeOfDay(): String {
        val hour = LocalTime.now().hour
        return when (hour) {
            in 5..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..20 -> "evening"
            else -> "night"
        }
    }
}
