package net.interstellarai.unreminder.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────
// UnReminder palette — "Sage" from the design handoff (tokens.jsx).
// Warm, calm, low-stimulation. Not material default.
// The design defines 8 palettes + dark variants; a future release may let
// users pick one. For now we ship Sage as the canonical light palette and
// its dark mirror.
// ─────────────────────────────────────────────────────────────────────────

// Light — "sage"
val SageBg = Color(0xFFE8EBD9)
val SageInk = Color(0xFF1F2A1A)
val SageAccent = Color(0xFF4D6B3A)
val SageSoft = Color(0xFFCFD7B8)

// Dark — "sage-d"
val SageBgDark = Color(0xFF171C14)
val SageInkDark = Color(0xFFE0E8CC)
val SageAccentDark = Color(0xFFA8C485)
val SageSoftDark = Color(0xFF252D1F)

// Dedication level colors — index = level (0 = muted, 5 = full accent)
val LevelColors = listOf(
    Color(0xFFA8A8A8),  // 0 — grey (just starting)
    Color(0xFF9A9A50),  // 1 — warm khaki
    Color(0xFF7A8F40),  // 2 — olive
    Color(0xFF5B7A35),  // 3 — mid-green
    Color(0xFF4D6B3A),  // 4 — accent green
    Color(0xFF3A5228),  // 5 — deep green (peak)
)

val CompletedFull = LevelColors[4]
val Dismissed = Color(0xFF6E6E6E)
