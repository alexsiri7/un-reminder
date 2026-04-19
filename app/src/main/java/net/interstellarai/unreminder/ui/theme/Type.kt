package net.interstellarai.unreminder.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────
// Typography — per the design handoff (tokens.jsx):
//   FONT_DISPLAY = Instrument Serif / Cormorant Garamond / Georgia serif
//   FONT_SANS    = Geist / Inter / system sans
//   FONT_MONO    = JetBrains Mono / Geist Mono / system mono
//
// We use system FontFamily.Serif/Default/Monospace rather than shipping
// custom font files to keep the PR a pure visuals change without adding
// dependencies or assets. When the time comes, swapping in Instrument Serif
// + Geist is a token-only change here.
// ─────────────────────────────────────────────────────────────────────────

val Display = FontFamily.Serif
val Sans = FontFamily.Default
val Mono = FontFamily.Monospace

// Reusable text styles — the handoff uses a small, opinionated stack of
// sizes rather than Material's default type scale.

val DisplayHuge = TextStyle(
    fontFamily = Display,
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Normal,
    fontSize = 52.sp,
    lineHeight = 52.sp,
    letterSpacing = (-1).sp,
)

val DisplayLarge = TextStyle(
    fontFamily = Display,
    fontWeight = FontWeight.Normal,
    fontSize = 44.sp,
    lineHeight = 46.sp,
    letterSpacing = (-0.5).sp,
)

val DisplayMedium = TextStyle(
    fontFamily = Display,
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Normal,
    fontSize = 26.sp,
    lineHeight = 30.sp,
    letterSpacing = (-0.3).sp,
)

val DisplaySmall = TextStyle(
    fontFamily = Display,
    fontWeight = FontWeight.Normal,
    fontSize = 22.sp,
    lineHeight = 26.sp,
    letterSpacing = (-0.3).sp,
)

val SansBody = TextStyle(
    fontFamily = Sans,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 22.sp,
)

val SansBodyStrong = TextStyle(
    fontFamily = Sans,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 18.sp,
)

val MonoLabel = TextStyle(
    fontFamily = Mono,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 1.5.sp,
)

val MonoLabelTiny = TextStyle(
    fontFamily = Mono,
    fontWeight = FontWeight.Normal,
    fontSize = 10.sp,
    lineHeight = 12.sp,
    letterSpacing = 2.sp,
)

val MonoMeta = TextStyle(
    fontFamily = Mono,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 16.sp,
)

// Material3 Typography — maps our design stack onto the slots Material
// widgets (TopAppBar, Text with `style = MaterialTheme.typography.X`) pull
// from. Keeps the redesign applied even to widgets we don't override.
val Typography = Typography(
    displayLarge = DisplayHuge,
    displayMedium = DisplayLarge,
    displaySmall = DisplayMedium,
    headlineLarge = DisplayMedium,
    headlineMedium = DisplaySmall,
    headlineSmall = DisplaySmall,
    titleLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = SansBodyStrong,
    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = SansBody,
    bodySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = SansBodyStrong,
    labelMedium = MonoLabel,
    labelSmall = MonoLabelTiny,
)
