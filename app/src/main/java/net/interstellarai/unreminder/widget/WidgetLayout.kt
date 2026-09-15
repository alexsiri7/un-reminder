package net.interstellarai.unreminder.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.unit.ColorProvider
import net.interstellarai.unreminder.ui.theme.SageAccent
import net.interstellarai.unreminder.ui.theme.SageAccentDark
import net.interstellarai.unreminder.ui.theme.SageBg
import net.interstellarai.unreminder.ui.theme.SageBgDark
import net.interstellarai.unreminder.ui.theme.SageInk
import net.interstellarai.unreminder.ui.theme.SageInkDark
import net.interstellarai.unreminder.ui.theme.SageMossDark
import net.interstellarai.unreminder.ui.theme.SageSoft
import net.interstellarai.unreminder.ui.theme.SageSoftDark
import kotlin.random.Random

/**
 * A card surface and the one ink drawn on it. The raw colours are the source of truth so a
 * plain JVM test can check the contrast of every pairing; the providers are what Glance draws.
 */
internal class WidgetPalette(
    val surfaceDay: Color,
    val surfaceNight: Color,
    val inkDay: Color,
    val inkNight: Color,
) {
    val surface: ColorProvider get() = ColorProvider(day = surfaceDay, night = surfaceNight)
    val ink: ColorProvider get() = ColorProvider(day = inkDay, night = inkNight)
}

/**
 * The five looks the widget rotates through, one per refresh, never the same one twice in a
 * row. Rotation is independent of [net.interstellarai.unreminder.domain.model.VariantShape]
 * and of the notification style. Each layout owns its palette rather than rotating colours
 * separately, so every surface/ink pairing that can ever render is contrast-checked. The
 * widget deliberately does not use `GlanceTheme`: its default is wallpaper-derived Material
 * You, which the app's own theme turned off.
 */
internal enum class WidgetLayout(
    val palette: WidgetPalette,
    val titleWeight: FontWeight,
    val titleFamily: FontFamily?,
) {
    SPRITE_LEFT(
        palette = WidgetPalette(surfaceDay = SageBg, surfaceNight = SageBgDark, inkDay = SageInk, inkNight = SageInkDark),
        titleWeight = FontWeight.Bold,
        titleFamily = null,
    ),
    SPRITE_RIGHT(
        palette = WidgetPalette(surfaceDay = SageSoft, surfaceNight = SageSoftDark, inkDay = SageInk, inkNight = SageInkDark),
        titleWeight = FontWeight.Medium,
        titleFamily = null,
    ),
    SPRITE_LARGE(
        palette = WidgetPalette(surfaceDay = SageAccent, surfaceNight = SageAccentDark, inkDay = SageBg, inkNight = SageBgDark),
        titleWeight = FontWeight.Medium,
        titleFamily = null,
    ),
    TYPOGRAPHIC(
        palette = WidgetPalette(surfaceDay = SageInk, surfaceNight = SageAccent, inkDay = SageBg, inkNight = SageInkDark),
        titleWeight = FontWeight.Bold,
        titleFamily = FontFamily.Serif,
    ),
    COMPACT(
        palette = WidgetPalette(surfaceDay = SageAccentDark, surfaceNight = SageMossDark, inkDay = SageInk, inkNight = SageInkDark),
        titleWeight = FontWeight.Medium,
        titleFamily = null,
    );

    companion object {
        fun next(previous: WidgetLayout?, random: Random = Random.Default): WidgetLayout =
            entries.filter { it != previous }.random(random)

        /** Tolerates a missing or unknown stored name: a rename must not crash a placed widget. */
        fun fromName(name: String?): WidgetLayout? = entries.firstOrNull { it.name == name }
    }
}
