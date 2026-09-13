package net.interstellarai.unreminder.domain.model

import kotlin.random.Random

/**
 * How a trigger notification is dressed, chosen at post time and frozen on the trigger row.
 * Presentation only: never a [VariantShape] and never a Variation field, so the two rotate
 * independently. Four entries, not the five #386 listed: the large icon has one system-positioned
 * slot (`notification_template_right_icon.xml`, gravity top|end), so a "sprite right" would be
 * identical to [SPRITE]; and a collapsed-only look is impossible because
 * `Notification.Builder.bigContentViewRequired()` always builds an expanded view when actions
 * exist (they always do). InboxStyle's rows are single-line and would truncate the prompt.
 */
enum class NotificationStyle {
    /** BigText with the sprite as the large icon — the original look. */
    SPRITE,
    /** BigPicture: sprite thumbnail collapsed, full-width sprite expanded. */
    BIG_PICTURE,
    /** BigText, no sprite, sage header tint. */
    TEXT_ONLY,
    /** BigText with the sprite and a header tint that rotates by trigger id. */
    ACCENT;

    companion object {
        /** Uniform over the entries other than [previous]; over all of them when there is none. */
        fun next(previous: NotificationStyle?, random: Random = Random.Default): NotificationStyle =
            entries.filter { it != previous }.random(random)
    }
}
