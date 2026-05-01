package net.interstellarai.unreminder.ui.recent

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_ONLY = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_TIME = DateTimeFormatter.ofPattern("MMM d HH:mm")

fun formatNextTrigger(
    state: NextTriggerState,
    now: Instant,
    zone: ZoneId,
): String = when (state) {
    NextTriggerState.NotScheduled -> "not scheduled"
    is NextTriggerState.Scheduled -> {
        val today = now.atZone(zone).toLocalDate()
        val target = state.at.atZone(zone)
        val targetDate = target.toLocalDate()
        when {
            targetDate == today -> "next: ${target.format(TIME_ONLY)}"
            targetDate == today.plusDays(1) -> "next: tomorrow ${target.format(TIME_ONLY)}"
            else -> "next: ${target.format(DATE_TIME)}"
        }
    }
}
