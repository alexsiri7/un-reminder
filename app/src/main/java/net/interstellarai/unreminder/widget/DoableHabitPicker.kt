package net.interstellarai.unreminder.widget

import kotlinx.coroutines.flow.first
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.domain.HabitAvailabilityService
import net.interstellarai.unreminder.domain.isDoableNow
import net.interstellarai.unreminder.service.notification.EmojiRotator
import javax.inject.Inject
import javax.inject.Singleton

data class DoableHabit(
    val id: Long,
    val name: String,
    val emoji: String,
)

/** Chooses what the home-screen widget shows: one eligible habit, or nothing. */
@Singleton
class DoableHabitPicker @Inject constructor(
    private val habitRepository: HabitRepository,
    private val availabilityService: HabitAvailabilityService,
    private val emojiRotator: EmojiRotator,
) {
    suspend fun pick(): DoableHabit? {
        val habits = habitRepository.getAll().first()
        if (habits.isEmpty()) return null
        val availability = availabilityService.computeForAll(habits)
        val habit = habits
            .filter { availability[it.id]?.isDoableNow == true }
            .randomOrNull()
            ?: return null
        // Notifications key the emoji by trigger id; the widget has no trigger, so the
        // habit id keeps each habit's emoji stable across refreshes instead.
        return DoableHabit(id = habit.id, name = habit.name, emoji = emojiRotator.pick(habit.id))
    }
}
