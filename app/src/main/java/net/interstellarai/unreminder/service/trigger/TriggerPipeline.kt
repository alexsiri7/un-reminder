package net.interstellarai.unreminder.service.trigger

import android.util.Log
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.repository.HabitLevelDescriptionRepository
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.LocationRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.data.repository.VariationRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import net.interstellarai.unreminder.service.notification.NotificationHelper
import net.interstellarai.unreminder.service.worker.RefillScheduler
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class TriggerPipeline @Inject constructor(
    private val habitRepository: HabitRepository,
    private val triggerRepository: TriggerRepository,
    private val locationRepository: LocationRepository,
    private val geofenceManager: GeofenceManager,
    private val notificationHelper: NotificationHelper,
    private val variationRepository: VariationRepository,
    private val refillScheduler: RefillScheduler,
    private val levelDescriptionRepository: HabitLevelDescriptionRepository,
) {
    companion object {
        private const val TAG = "TriggerPipeline"
        internal const val CAP_MINUTES = 1440L
        internal const val MINUTES_PER_WEIGHT_UNIT = 120L // weight increases by 1 per 2 hours
        internal val MAX_WEIGHT = 1.0 + CAP_MINUTES / MINUTES_PER_WEIGHT_UNIT.toDouble()

        internal fun computeWeight(lastFiredMillis: Long?, nowMillis: Long): Double {
            lastFiredMillis ?: return MAX_WEIGHT
            val minutesSince = maxOf(0L, (nowMillis - lastFiredMillis) / 60_000L)
            return 1.0 + minOf(minutesSince, CAP_MINUTES) / MINUTES_PER_WEIGHT_UNIT.toDouble()
        }

        internal fun pickWeighted(
            habits: List<HabitEntity>,
            lastFiredMillisById: Map<Long, Long?>,
            nowMillis: Long
        ): HabitEntity {
            val weights = habits.map { computeWeight(lastFiredMillisById[it.id], nowMillis) }
            val total = weights.sum()
            var r = Random.nextDouble() * total
            for ((habit, weight) in habits.zip(weights)) {
                r -= weight
                if (r <= 0.0) return habit
            }
            return habits.last() // floating-point safety: loop always exits above for positive weights
        }
    }

    suspend fun execute(triggerId: Long) {
        val trigger = triggerRepository.getById(triggerId) ?: run {
            Log.w(TAG, "Trigger $triggerId not found, skipping")
            return
        }
        if (trigger.status != TriggerStatus.SCHEDULED) return

        val locationIds = geofenceManager.currentLocationIds

        try {
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
            val timeOfDay = resolveTimeOfDay()
            val locationName = resolveLocationName(locationIds)
            val prompt = resolvePrompt(habit, locationName, timeOfDay)

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
            if (e is CancellationException) throw e
            Log.e(TAG, "Trigger pipeline failed for trigger=$triggerId", e)
            triggerRepository.updateOutcome(triggerId, TriggerStatus.DISMISSED)
        }
    }

    private suspend fun resolvePrompt(habit: HabitEntity, locationName: String, timeOfDay: String): String {
        val variation = try {
            variationRepository.pickRandomUnused(habit.id)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "variationRepository.pickRandomUnused failed — falling back to habit.name", e)
            null
        }

        if (variation != null) {
            try {
                if (variationRepository.needsRefill(habit.id)) {
                    refillScheduler.enqueueForHabit(habit.id)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "needsRefill/enqueue failed — non-fatal, continuing", e)
            }
            return variation.text
        }

        Log.w(TAG, "pool empty for habit ${habit.id} — falling back to level description")
        Sentry.captureMessage("pool empty for habit ${habit.id}") { scope ->
            scope.setTag("component", "pool-empty")
        }
        try {
            refillScheduler.enqueueForHabit(habit.id)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "refill enqueue failed for habit=${habit.id} — non-fatal", e)
        }
        val levelDesc = try {
            levelDescriptionRepository.getDescriptionForLevel(habit.id, habit.dedicationLevel)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
        return levelDesc?.takeIf { it.isNotBlank() } ?: habit.name
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
