package net.interstellarai.unreminder.ui.reminder

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import net.interstellarai.unreminder.ui.theme.DisplayLarge
import net.interstellarai.unreminder.ui.theme.DisplayMedium
import net.interstellarai.unreminder.ui.theme.DisplaySmall
import net.interstellarai.unreminder.ui.theme.SageAccent
import net.interstellarai.unreminder.ui.theme.SageAccentDark
import net.interstellarai.unreminder.ui.theme.SageBg
import net.interstellarai.unreminder.ui.theme.SageBgDark
import net.interstellarai.unreminder.ui.theme.SageInk
import net.interstellarai.unreminder.ui.theme.SageInkDark
import net.interstellarai.unreminder.ui.theme.SageMossDark
import net.interstellarai.unreminder.ui.theme.SageSoft
import net.interstellarai.unreminder.ui.theme.SageSoftDark
import net.interstellarai.unreminder.ui.theme.Sans

/**
 * A screen surface and the one ink drawn on it, as raw colours so a plain JVM test can check
 * the contrast of every pairing before it is handed to the theme.
 */
internal class ReminderDetailPalette(
    val surfaceDay: Color,
    val surfaceNight: Color,
    val inkDay: Color,
    val inkNight: Color,
) {
    fun surface(dark: Boolean): Color = if (dark) surfaceNight else surfaceDay
    fun ink(dark: Boolean): Color = if (dark) inkNight else inkDay
}

// The one sans headline, so a single layout reads in the body face the way the widget's
// TYPOGRAPHIC layout reads in a serif.
private val SansHeadline = TextStyle(
    fontFamily = Sans,
    fontWeight = FontWeight.Medium,
    fontSize = 24.sp,
    lineHeight = 32.sp,
)

/**
 * The five looks the reminder detail screen rotates through, independent of
 * [net.interstellarai.unreminder.domain.model.NotificationStyle],
 * [net.interstellarai.unreminder.widget.WidgetLayout] and
 * [net.interstellarai.unreminder.domain.model.VariantShape]. Each owns its palette and
 * headline scale so every pairing that can render is contrast-checked. The layout is
 * derived from the target, never drawn: the screen can be reopened, and the same variant
 * must look the same each time.
 */
internal enum class ReminderDetailLayout(
    val palette: ReminderDetailPalette,
    val headline: TextStyle,
) {
    SPRITE_TOP(
        palette = ReminderDetailPalette(surfaceDay = SageBg, surfaceNight = SageBgDark, inkDay = SageInk, inkNight = SageInkDark),
        headline = DisplayMedium,
    ),
    SPRITE_LEFT(
        palette = ReminderDetailPalette(surfaceDay = SageSoft, surfaceNight = SageSoftDark, inkDay = SageInk, inkNight = SageInkDark),
        headline = DisplaySmall,
    ),
    BACKDROP(
        palette = ReminderDetailPalette(surfaceDay = SageInk, surfaceNight = SageAccent, inkDay = SageBg, inkNight = SageInkDark),
        headline = DisplayMedium,
    ),
    TEXT_DOMINANT(
        palette = ReminderDetailPalette(surfaceDay = SageAccent, surfaceNight = SageAccentDark, inkDay = SageBg, inkNight = SageBgDark),
        headline = DisplayLarge,
    ),
    SPRITE_BOTTOM(
        palette = ReminderDetailPalette(surfaceDay = SageAccentDark, surfaceNight = SageMossDark, inkDay = SageInk, inkNight = SageInkDark),
        headline = SansHeadline,
    );

    companion object {
        /**
         * Keyed on the variation id (the habit id for a fallback row, the trigger id for a
         * trigger), so reopening never reshuffles and consecutively generated variants of one
         * habit spread across all five.
         */
        fun forTarget(target: ReminderDetailTarget): ReminderDetailLayout {
            val seed = when (target) {
                is ReminderDetailTarget.Variant -> target.variationId ?: target.habitId
                is ReminderDetailTarget.Trigger -> target.triggerId
            }
            // .mod() (not %) ensures a non-negative index when the seed is negative
            return entries[seed.mod(entries.size)]
        }
    }
}
