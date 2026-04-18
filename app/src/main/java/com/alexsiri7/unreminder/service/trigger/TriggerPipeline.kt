package com.alexsiri7.unreminder.service.trigger

import android.util.Log
import com.alexsiri7.unreminder.data.db.HabitEntity
import com.alexsiri7.unreminder.data.repository.HabitRepository
import com.alexsiri7.unreminder.data.repository.LocationRepository
import com.alexsiri7.unreminder.data.repository.TriggerRepository
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
    private val locationRepository: LocationRepository,
    private val geofenceManager: GeofenceManager,
    private val promptGenerator: PromptGenerator,
    private val notificationHelper: NotificationHelper
) {
    companion object {
        private const val TAG = "TriggerPipeline"
        internal const val CAP_MINUTES = 1440L
        internal val MAX_WEIGHT = 1.0 + CAP_MINUTES / 120.0

        internal fun computeWeight(lastFiredMillis: Long?, nowMillis: Long): Double {
            lastFiredMillis ?: return MAX_WEIGHT
            val minutesSince = (nowMillis - lastFiredMillis) / 60_000L
            return 1.0 + minOf(minutesSince, CAP_MINUTES) / 120.0
        }

        internal fun pickWeighted(
            habits: List<HabitEntity>,
            lastFiredMillisById: Map<Long, Long?>,
            nowMillis: Long
        ): HabitEntity {
            val weights = habits.map { computeWeight(lastFiredMillisById[it.id], nowMillis) }
            val total = weights.sum()
            var r = Math.random() * total
            for ((habit, weight) in habits.zip(weights)) {
                r -= weight
                if (r <= 0.0) return habit
            }
            return habits.last()
        }
    }

    suspend fun execute(triggerId: Long) {
        val trigger = triggerRepository.getById(triggerId) ?: run {
            Log.w(TAG, "Trigger $triggerId not found, skipping")
            return
        }
        if (trigger.status != TriggerStatus.SCHEDULED) return

        val locationIds = geofenceManager.currentLocationIds

        val eligibleHabits = habitRepository.getEligibleHabits(locationIds)
        if (eligibleHabits.isEmpty()) {
            Log.d(TAG, "No eligible habits for locationIds=$locationIds, skipping trigger $triggerId")
            triggerRepository.updateOutcome(triggerId, TriggerStatus.DISMISSED)
            return
        }

        val nowMillis = System.currentTimeMillis()
        val lastFiredMap = eligibleHabits.associate { h ->
            h.id to triggerRepository.getLastFiredForHabit(h.id)
        }
        val habit = pickWeighted(eligibleHabits, lastFiredMap, nowMillis)
        try {
            val timeOfDay = resolveTimeOfDay()
            val locationName = resolveLocationName(locationIds)
            val prompt = promptGenerator.generate(habit, locationName, timeOfDay)

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
        } catch (e: Exception) {
            Log.e(TAG, "Trigger pipeline failed for trigger=$triggerId habit=${habit.name}", e)
            triggerRepository.updateOutcome(triggerId, TriggerStatus.DISMISSED)
        }
    }

    private suspend fun resolveLocationName(locationIds: Set<Long>): String {
        if (locationIds.isEmpty()) return "any location"
        return locationRepository.getByIds(locationIds)
            .joinToString(", ") { it.name }
            .ifBlank { "any location" }
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
