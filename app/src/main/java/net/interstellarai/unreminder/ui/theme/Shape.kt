package net.interstellarai.unreminder.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────
// Shapes — the design handoff uses 2px radii across the board
// (see borderRadius: 2 on cards, buttons, chips in the jsx refs).
// Slightly softer than pure squares, but nowhere near Material's pill defaults.
// ─────────────────────────────────────────────────────────────────────────

private val Sharp = RoundedCornerShape(2.dp)
private val Soft = RoundedCornerShape(4.dp)

val UnReminderShapes = Shapes(
    extraSmall = Sharp,
    small = Sharp,
    medium = Sharp,
    large = Soft,
    extraLarge = Soft,
)
