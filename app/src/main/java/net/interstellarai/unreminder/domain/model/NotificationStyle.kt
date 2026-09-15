package net.interstellarai.unreminder.domain.model

/**
 * How a trigger notification is dressed, derived from the variant's [VariantTreatment] seed
 * and frozen on the trigger row. Presentation only: never a [VariantShape] and never a
 * Variation field, so the look is independent of the shape. Four entries, not the five #386 listed: the large icon has one system-positioned
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
    /** BigText with the sprite and a header tint keyed on the variant's seed. */
    ACCENT;

    companion object {
        fun forSeed(seed: Long): NotificationStyle = VariantTreatment.pick(entries, seed)
    }
}
