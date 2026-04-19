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

// Status colours for triggers — kept for recent-triggers chip styling,
// re-tuned to harmonise with the sage palette.
val CompletedFull = Color(0xFF4D6B3A)      // accent green
val CompletedLowFloor = Color(0xFF9A7A15)  // muted butter (win-but-smaller)
val Dismissed = Color(0xFF6E6E6E)          // soft grey ink
