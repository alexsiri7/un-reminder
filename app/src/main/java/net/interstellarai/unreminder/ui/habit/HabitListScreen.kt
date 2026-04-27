package net.interstellarai.unreminder.ui.habit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.interstellarai.unreminder.service.llm.AiStatus
import net.interstellarai.unreminder.ui.theme.Dimens
import net.interstellarai.unreminder.ui.theme.DisplayHuge
import net.interstellarai.unreminder.ui.theme.DisplaySmall
import net.interstellarai.unreminder.ui.theme.FeedbackIconButton
import net.interstellarai.unreminder.ui.theme.MonoContextStrip
import net.interstellarai.unreminder.ui.theme.MonoLabel
import net.interstellarai.unreminder.ui.theme.MonoLabelTiny
import net.interstellarai.unreminder.ui.theme.MonoSectionLabel
import net.interstellarai.unreminder.ui.theme.NavPill
import net.interstellarai.unreminder.ui.theme.UnReminderShapes
import net.interstellarai.unreminder.ui.theme.UnReminderTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────
// Habit list — mirrors `components/home.jsx`:
//   - Context strip (date · time-of-day)
//   - Big italic serif "un-reminder" header
//   - List of habits with a circular glyph bubble, name, low-floor text,
//     and a "location" tag on the right.
// The ViewModel and data contracts are untouched — this is purely layout.
// ─────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitListScreen(
    onAddHabit: () -> Unit,
    onEditHabit: (Long) -> Unit,
    onNavigateToFeedback: () -> Unit = {},
    viewModel: HabitListViewModel = hiltViewModel(),
) {
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val aiStatus by viewModel.aiStatus.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddHabit,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add habit")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AiDownloadBanner(aiStatus = aiStatus)

            HabitListHeader(onNavigateToFeedback)

            if (habits.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "no habits yet",
                        style = DisplaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(Dimens.sm))
                    Text(
                        "tap + to add one",
                        style = MonoLabel,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.xxl, vertical = Dimens.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    val activeCount = habits.count { it.active }
                    MonoSectionLabel("$activeCount habits")
                    Text(
                        "+ new",
                        style = MonoLabel,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.clickable(onClick = onAddHabit),
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Dimens.sm),
                ) {
                    items(habits, key = { it.id }) { habit ->
                        HabitRow(
                            name = habit.name,
                            active = habit.active,
                            dedicationLevel = habit.dedicationLevel,
                            onClick = { onEditHabit(habit.id) },
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            thickness = Dimens.hairline,
                        )
                    }
                }
            }

            NavPill()
        }
    }
}

@Composable
private fun HabitListHeader(onNavigateToFeedback: () -> Unit) {
    val today = LocalDate.now()
    val dateLabel = today.format(DateTimeFormatter.ofPattern("EEE · MMM d", Locale.getDefault()))

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(
                start = Dimens.xxl,
                end = Dimens.xxl,
                top = Dimens.xl,
                bottom = Dimens.md,
            ),
        ) {
            MonoContextStrip(dateLabel)
            Spacer(Modifier.height(Dimens.sm))
            Text(
                text = "un-reminder",
                style = DisplayHuge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        FeedbackIconButton(
            onClick = onNavigateToFeedback,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// AI status banner — renders at the top of the list when the cloud worker
// is not configured or when the pool is empty.
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun AiDownloadBanner(
    aiStatus: AiStatus,
) {
    val label = when (aiStatus) {
        is AiStatus.Empty -> "cloud pool empty — variants being generated"
        is AiStatus.Unavailable -> "AI unavailable — check cloud settings"
        else -> return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Dimens.xxl, vertical = Dimens.md),
    ) {
        MonoSectionLabel(label)
        Spacer(Modifier.height(Dimens.xs + 2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
        )
    }
}

@Composable
private fun HabitRow(
    name: String,
    active: Boolean,
    dedicationLevel: Int,
    onClick: () -> Unit,
) {
    val alpha = if (active) 1f else 0.35f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.lg, vertical = Dimens.md + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphBubble()
        Spacer(Modifier.size(Dimens.md + 2.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = DisplaySmall.copy(
                    textDecoration = if (active) TextDecoration.None else TextDecoration.LineThrough,
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "L$dedicationLevel",
                style = MonoLabelTiny,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f * alpha),
            )
            Spacer(Modifier.width(4.dp))
            repeat(6) { i ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            if (i <= dedicationLevel) MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f * alpha),
                            CircleShape
                        )
                )
                if (i < 5) Spacer(Modifier.width(2.dp))
            }
        }
        Spacer(Modifier.width(Dimens.sm))
        Box(
            modifier = Modifier
                .border(
                    width = Dimens.hairline,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                    shape = UnReminderShapes.small,
                )
                .padding(horizontal = Dimens.sm - 2.dp, vertical = 3.dp),
        ) {
            Text(
                text = "anywhere",
                style = MonoLabelTiny,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f * alpha),
            )
        }
    }
}

/**
 * The small circular glyph bubble next to each habit. The design cycles a
 * family of seed glyphs (arch / ring / wave / bars / dot / slash) — until
 * the Kotlin port of those SVGs lands we render a solid accent dot as a
 * stand-in so the circle still reads as a mark.
 */
@Composable
private fun GlyphBubble() {
    Box(
        modifier = Modifier
            .size(Dimens.glyphBubble)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────

@Preview(name = "banner · unavailable", showBackground = true)
@Composable
private fun PreviewBannerUnavailable() {
    UnReminderTheme {
        AiDownloadBanner(aiStatus = AiStatus.Unavailable)
    }
}

@Preview(name = "banner · ready (renders nothing)", showBackground = true)
@Composable
private fun PreviewBannerReady() {
    UnReminderTheme {
        AiDownloadBanner(aiStatus = AiStatus.Ready)
    }
}
