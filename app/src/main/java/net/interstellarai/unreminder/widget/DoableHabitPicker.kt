package net.interstellarai.unreminder.widget

import kotlinx.coroutines.flow.first
import net.interstellarai.unreminder.data.repository.HabitLevelDescriptionRepository
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.VariationRepository
import net.interstellarai.unreminder.domain.HabitAvailabilityService
import net.interstellarai.unreminder.service.notification.EmojiRotator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the widget shows for its habit: a peeked (not consumed) variant and the sprite tag it
 * carries, or — when the pool is empty — the dedication-level description and no tag, which
 * [net.interstellarai.unreminder.service.notification.SpriteResolver] rotates by habit id.
 */
data class DoableHabit(
    val id: Long,
    val name: String,
    val emoji: String,
    val text: String?,
    val variationId: Long?,
    val spriteTag: String?,
)

/**
 * Chooses what the home-screen widget shows: one habit from the best display tier that has
 * any, so a blocked habit still gets offered. Nothing only when there is no active habit.
 */
@Singleton
class DoableHabitPicker @Inject constructor(
    private val habitRepository: HabitRepository,
    private val availabilityService: HabitAvailabilityService,
    private val levelDescriptionRepository: HabitLevelDescriptionRepository,
    private val variationRepository: VariationRepository,
    private val emojiRotator: EmojiRotator,
) {
    suspend fun pick(): DoableHabit? {
        val habits = habitRepository.getAll().first()
        val tiers = availabilityService.computeDisplayTiers(habits)
        val bestTier = tiers.values.minOrNull() ?: return null
        val habit = habits.filter { tiers[it.id] == bestTier }.randomOrNull() ?: return null
        val variation = variationRepository.peekUnusedVariation(habit.id)
        val text = variation?.text
            ?: levelDescriptionRepository.getDescriptionForLevel(habit.id, habit.dedicationLevel)
        // Notifications key the emoji by trigger id; the widget has no trigger, so the
        // habit id keeps each habit's emoji stable across refreshes instead.
        return DoableHabit(
            id = habit.id,
            name = habit.name,
            emoji = emojiRotator.pick(habit.id),
            text = text?.takeIf { it.isNotBlank() },
            variationId = variation?.id,
            spriteTag = variation?.spriteTag,
        )
    }
}
