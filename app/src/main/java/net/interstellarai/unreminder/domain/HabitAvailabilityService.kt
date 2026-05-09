package net.interstellarai.unreminder.domain

import android.util.Log
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.data.repository.WindowRepository
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

sealed class AvailabilityStatus {
    object Available : AvailabilityStatus()
    object NewHabit : AvailabilityStatus()
    data class Unavailable(val reasons: List<UnavailableReason>) : AvailabilityStatus()
}

enum class UnavailableReason { INACTIVE, LOCATION, TIME_WINDOW, COMPLETED, COOLDOWN, DAILY_LIMIT }

@Singleton
class HabitAvailabilityService @Inject constructor(
    private val habitRepository: HabitRepository,
    private val windowRepository: WindowRepository,
    private val triggerRepository: TriggerRepository,
    private val geofenceManager: GeofenceManager,
) {
    companion object {
        private const val TAG = "HabitAvailabilityService"
    }

    /**
     * Computes the current availability status for an existing habit, checking each
     * ineligibility reason independently (mirroring the SQL in HabitDao.getEligibleHabits).
     *
     * The service fetches its own locationIds and windowIds for the given habit.
     */
    suspend fun computeAvailability(habit: HabitEntity): AvailabilityStatus {
        val locationIds = habitRepository.getLocationIds(habit.id).toSet()
        val windowIds = habitRepository.getWindowIds(habit.id).toSet()
        return computeAvailability(habit, locationIds, windowIds)
    }

    /**
     * Computes availability for a list of habits in one pass, returning a map from
     * habit id to availability status.
     */
    suspend fun computeForAll(habits: List<HabitEntity>): Map<Long, AvailabilityStatus> {
        return habits.associate { habit ->
            habit.id to try {
                computeAvailability(habit)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "computeForAll: availability computation failed for habit ${habit.id}", e)
                AvailabilityStatus.Available
            }
        }
    }

    /**
     * Internal implementation — accepts pre-fetched ids to avoid redundant DB calls
     * when the caller already has them (e.g. HabitEditViewModel which loaded them for
     * the reactive recompute path).
     */
    internal suspend fun computeAvailability(
        habit: HabitEntity,
        locationIds: Set<Long>,
        windowIds: Set<Long>,
    ): AvailabilityStatus {
        val reasons = mutableListOf<UnavailableReason>()

        // --- Active --- (mirrors `h.active = 1` in HabitDao.getEligibleHabits)
        if (!habit.active) reasons += UnavailableReason.INACTIVE

        // --- Location ---
        // Habit has location restrictions AND current location not in them.
        if (locationIds.isNotEmpty()) {
            val currentIds = geofenceManager.currentLocationIds.value
            if (currentIds.none { it in locationIds }) {
                reasons += UnavailableReason.LOCATION
            }
        }

        // --- Time window ---
        // Habit has windows AND current time not in any active window for today.
        if (windowIds.isNotEmpty()) {
            val now = LocalTime.now()
            val currentSecondOfDay = now.toSecondOfDay()
            val dayOfWeekBit = 1 shl (LocalDate.now().dayOfWeek.value - 1)
            val activeWindows = windowRepository.getActiveWindows()
                .filter { it.id in windowIds }
            val inWindow = activeWindows.any { w ->
                w.startTime.toSecondOfDay() <= currentSecondOfDay &&
                    w.endTime.toSecondOfDay() >= currentSecondOfDay &&
                    (w.daysOfWeekBitmask and dayOfWeekBit) != 0
            }
            if (!inWindow) reasons += UnavailableReason.TIME_WINDOW
        }

        // --- Completed today ---
        // status = 'COMPLETED' (exact, matching the SQL) since start of today.
        val completedCutoff = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val completedCount = triggerRepository.countCompletedSince(habit.id, completedCutoff)
        if (completedCount > 0) reasons += UnavailableReason.COMPLETED

        // --- Cooldown ---
        // DISMISSED or FIRED within cooldown_minutes (mirrors SQL; 0 cooldown = no restriction).
        if (habit.cooldownMinutes > 0) {
            val nowEpochMillis = Instant.now().toEpochMilli()
            val cooldownCutoff = nowEpochMillis - habit.cooldownMinutes * 60 * 1000L
            val lastFiredOrDismissed = triggerRepository.getLastFiredOrDismissedForHabit(habit.id)
            if (lastFiredOrDismissed != null && lastFiredOrDismissed > cooldownCutoff) {
                reasons += UnavailableReason.COOLDOWN
            }
        }

        // --- Daily limit --- (mirrors `COUNT(...) < h.daily_limit` in HabitDao.getEligibleHabits;
        // counts only COMPLETED actions since start-of-day; dismissed/fired do not count.)
        val dailyTotal = triggerRepository.countDailyCompletionsSince(habit.id, completedCutoff)
        if (dailyTotal >= habit.dailyLimit) reasons += UnavailableReason.DAILY_LIMIT

        return if (reasons.isEmpty()) AvailabilityStatus.Available
        else AvailabilityStatus.Unavailable(reasons)
    }
}
