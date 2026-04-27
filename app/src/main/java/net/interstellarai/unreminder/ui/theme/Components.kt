package net.interstellarai.unreminder.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────
// Small, shared building blocks reused across the redesigned screens.
// Kept in the theme package so they live next to tokens and shapes.
// ─────────────────────────────────────────────────────────────────────────

/**
 * Small mono-uppercase label, used everywhere in the handoff as a section
 * caption (e.g. "YOU ARE AT", "HABIT NAME", "PREVIEW · SAMPLED FROM GEMMA").
 */
@Composable
fun MonoSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MonoLabelTiny,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        modifier = modifier,
    )
}

/**
 * Horizontal "mono · dash · mono" context marker, like
 *   ── wed · apr 18 · evening ──
 * Used at the top of the home screen.
 */
@Composable
fun MonoContextStrip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = "\u2500\u2500 ${text.lowercase(Locale.getDefault())} \u2500\u2500",
            style = MonoLabelTiny,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

/**
 * Context strip card (warm "soft" background) that the home and habit editor
 * use for contextual meta info — "YOU ARE AT · HOME · NEXT WINDOW · 18–21".
 */
@Composable
fun ContextStrip(
    leadingLabel: String,
    leadingValue: String,
    trailingLabel: String,
    trailingValue: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, UnReminderShapes.small)
            .padding(horizontal = Dimens.lg, vertical = Dimens.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        ContextStripSlot(
            label = leadingLabel,
            value = leadingValue,
            alignEnd = false,
        )
        ContextStripSlot(
            label = trailingLabel,
            value = trailingValue,
            alignEnd = true,
        )
    }
}

@Composable
private fun ContextStripSlot(
    label: String,
    value: String,
    alignEnd: Boolean,
) {
    val horizontalAlignment = if (alignEnd) {
        androidx.compose.ui.Alignment.End
    } else {
        androidx.compose.ui.Alignment.Start
    }
    androidx.compose.foundation.layout.Column(horizontalAlignment = horizontalAlignment) {
        MonoSectionLabel(label)
        Spacer(Modifier.width(Dimens.xs))
        Text(
            text = value,
            style = SansBodyStrong,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Subtle bug-report icon overlaid at the top-end of a header Box, used on
 * list screens to reach the Feedback screen.
 */
@Composable
fun FeedbackIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.padding(end = Dimens.md),
    ) {
        Icon(
            Icons.Default.BugReport,
            contentDescription = "Send Feedback",
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }
}

/**
 * The slim home-indicator style pill at the very bottom of each screen in
 * the handoff. Doesn't affect layout above it beyond its own height.
 */
@Composable
fun NavPill(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Dimens.sm),
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .width(Dimens.navPillWidth)
                .background(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    shape = UnReminderShapes.small,
                )
                .padding(vertical = 2.dp),
        ) {
            // solid bar height handled by padding+background alone
        }
    }
}
