package net.interstellarai.unreminder.widget

import net.interstellarai.unreminder.data.db.WindowEntity
import java.time.LocalDateTime

object WindowBoundaries {
    private const val DAYS_TO_SCAN = 8L

    /**
     * The next instant at which some window opens or closes, or null when no window ever
     * does. A window is open through its end second (see HabitAvailabilityService), so it
     * closes one second later.
     */
    fun next(windows: List<WindowEntity>, now: LocalDateTime): LocalDateTime? {
        val today = now.toLocalDate()
        return (0 until DAYS_TO_SCAN)
            .map { today.plusDays(it) }
            .flatMap { date ->
                val dayBit = 1 shl (date.dayOfWeek.value - 1)
                windows
                    .filter { it.active && (it.daysOfWeekBitmask and dayBit) != 0 }
                    .flatMap { listOf(date.atTime(it.startTime), date.atTime(it.endTime).plusSeconds(1)) }
            }
            .filter { it.isAfter(now) }
            .minOrNull()
    }
}
