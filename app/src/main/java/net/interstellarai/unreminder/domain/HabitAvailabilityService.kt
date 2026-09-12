package net.interstellarai.unreminder.domain

import android.util.Log
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.data.repository.WindowRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

sealed class AvailabilityStatus {
    object Available : AvailabilityStatus()
    object NewHabit : AvailabilityStatus()
    data class Unavailable(val reasons: List<UnavailableReason>) : AvailabilityStatus()
}

enum class UnavailableReason { INACTIVE, LOCATION, TIME_WINDOW, COMPLETED, COOLDOWN, DAILY_LIMIT }

/**
 * Whether a notification may fire for a habit with this status. This is the trigger gate and
 * stays strict; what the Now menu and widget offer is decided by [DisplayTier] instead.
 */
val AvailabilityStatus.isDoableNow: Boolean
    get() = when (this) {
        is AvailabilityStatus.Available, is AvailabilityStatus.NewHabit -> true
        is AvailabilityStatus.Unavailable -> false
    }

/**
 * Where a habit sits on the Now menu and the widget, best first. Availability decides whether
 * to interrupt the user; once they have come looking there is nothing to protect them from, so
 * an unavailable habit is ranked lower rather than hidden. The last four values are the blocked
 * tier, ordered by how actionable the block is: pacing the user set themselves, then the wrong
 * time, then the wrong place, then already done today.
 */
enum class DisplayTier { DOABLE, RECENTLY_DISMISSED, PACED, OUT_OF_HOURS, ELSEWHERE, DONE_TODAY }

@Singleton
class HabitAvailabilityService @Inject constructor(
    private val habitRepository: HabitRepository,
    private val windowRepository: WindowRepository,
    private val triggerRepository: TriggerRepository,
    private val geofenceManager: GeofenceManager,
) {
    companion object {
        private const val TAG = "HabitAvailabilityService"

        // Availability is recomputed on every menu render and widget refresh; recording
        // each location denial would push everything else out of the breadcrumb ring.
        private const val LOCATION_DENIAL_SAMPLE_EVERY = 20L
    }

    private val locationDenials = AtomicLong()

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
     * The display tier of every active habit, keyed by id. Paused habits get no entry: switching
     * a habit off is the user's decision, not a contextual block, so it is never surfaced.
     *
     * A habit whose most recent trigger went unanswered — dismissed outright, or expired when a
     * later nudge superseded it — ranks as [DisplayTier.RECENTLY_DISMISSED] whatever else blocks
     * it; passing on it is the more telling reason. A habit blocked for several reasons takes the
     * least actionable of them.
     */
    suspend fun computeDisplayTiers(habits: List<HabitEntity>): Map<Long, DisplayTier> {
        val availability = computeForAll(habits)
        return buildMap {
            for (habit in habits) {
                val blockedBy = (availability.getValue(habit.id) as? AvailabilityStatus.Unavailable)?.reasons.orEmpty()
                if (UnavailableReason.INACTIVE in blockedBy) continue
                put(
                    habit.id,
                    if (lastTriggerWentUnanswered(habit.id)) DisplayTier.RECENTLY_DISMISSED
                    else blockedBy.maxOfOrNull { blockedTier(it) } ?: DisplayTier.DOABLE,
                )
            }
        }
    }

    private suspend fun lastTriggerWentUnanswered(habitId: Long): Boolean {
        val status = triggerRepository.getLastNForHabit(habitId, 1).firstOrNull()?.status
        return status == TriggerStatus.DISMISSED || status == TriggerStatus.EXPIRED
    }

    private fun blockedTier(reason: UnavailableReason): DisplayTier = when (reason) {
        UnavailableReason.COOLDOWN, UnavailableReason.DAILY_LIMIT -> DisplayTier.PACED
        UnavailableReason.TIME_WINDOW -> DisplayTier.OUT_OF_HOURS
        UnavailableReason.LOCATION -> DisplayTier.ELSEWHERE
        UnavailableReason.COMPLETED -> DisplayTier.DONE_TODAY
        UnavailableReason.INACTIVE -> throw IllegalArgumentException("paused habits are never ranked for display")
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
                recordLocationDenial(habit.id, currentIds)
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
        // DISMISSED, FIRED or EXPIRED within cooldown_minutes (mirrors SQL; 0 cooldown = no restriction).
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

    private fun recordLocationDenial(habitId: Long, currentIds: Set<Long>) {
        if (locationDenials.getAndIncrement() % LOCATION_DENIAL_SAMPLE_EVERY != 0L) return
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "geofence"
            message = "Habit unavailable: not at a required location"
            level = SentryLevel.INFO
            setData("habit_id", habitId.toString())
            setData("current_ids", currentIds.sorted().toString())
        })
    }
}
