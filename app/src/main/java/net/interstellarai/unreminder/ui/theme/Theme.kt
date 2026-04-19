package net.interstellarai.unreminder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// ─────────────────────────────────────────────────────────────────────────
// UnReminder theme — translates the "sage" palette from the design handoff
// (tokens.jsx) onto Material3's ColorScheme slots.
//
// Token → slot mapping rationale:
//   bg      → background / surface
//   ink     → onBackground / onSurface (primary ink colour)
//   accent  → primary (also used for FAB, active chips, ring/glyph fills)
//   soft    → surfaceVariant / secondaryContainer (chip backs, meta strips)
//
// The handoff ships with 8 palettes but only one is active at a time; we
// default to Sage and mirror with its dark variant. Dynamic colour is
// intentionally disabled here — the redesign is an identity, not a
// wallpaper-derived Material You scheme.
// ─────────────────────────────────────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    primary = SageAccent,
    onPrimary = SageBg,
    primaryContainer = SageSoft,
    onPrimaryContainer = SageInk,
    secondary = SageAccent,
    onSecondary = SageBg,
    secondaryContainer = SageSoft,
    onSecondaryContainer = SageInk,
    tertiary = SageAccent,
    onTertiary = SageBg,
    tertiaryContainer = SageSoft,
    onTertiaryContainer = SageInk,
    background = SageBg,
    onBackground = SageInk,
    surface = SageBg,
    onSurface = SageInk,
    surfaceVariant = SageSoft,
    onSurfaceVariant = SageInk,
    outline = SageInk.copy(alpha = 0.3f),
    outlineVariant = SageSoft,
)

private val DarkColorScheme = darkColorScheme(
    primary = SageAccentDark,
    onPrimary = SageBgDark,
    primaryContainer = SageSoftDark,
    onPrimaryContainer = SageInkDark,
    secondary = SageAccentDark,
    onSecondary = SageBgDark,
    secondaryContainer = SageSoftDark,
    onSecondaryContainer = SageInkDark,
    tertiary = SageAccentDark,
    onTertiary = SageBgDark,
    tertiaryContainer = SageSoftDark,
    onTertiaryContainer = SageInkDark,
    background = SageBgDark,
    onBackground = SageInkDark,
    surface = SageBgDark,
    onSurface = SageInkDark,
    surfaceVariant = SageSoftDark,
    onSurfaceVariant = SageInkDark,
    outline = SageInkDark.copy(alpha = 0.3f),
    outlineVariant = SageSoftDark,
)

@Composable
fun UnReminderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Kept as an unused parameter so any caller that still passes it compiles.
    // The redesign has a fixed identity so dynamic colour is intentionally off.
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = UnReminderShapes,
        content = content,
    )
}
