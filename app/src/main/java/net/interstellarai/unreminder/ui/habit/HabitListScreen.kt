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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import net.interstellarai.unreminder.service.llm.AiStatus
import net.interstellarai.unreminder.ui.theme.Dimens
import net.interstellarai.unreminder.ui.theme.DisplayHuge
import net.interstellarai.unreminder.ui.theme.DisplaySmall
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
    viewModel: HabitListViewModel = hiltViewModel(),
) {
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
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
            AiDownloadBanner(
                progress = downloadProgress,
                aiStatus = aiStatus,
                onRetry = viewModel::retryModelDownload,
            )

            HabitListHeader()

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
private fun HabitListHeader() {
    val today = LocalDate.now()
    val dateLabel = today.format(DateTimeFormatter.ofPattern("EEE · MMM d", Locale.getDefault()))

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
}

// ─────────────────────────────────────────────────────────────────────────
// AI download banner — renders at the top of the list when the on-device
// model is streaming down, or for a short "AI ready" flash after it
// finishes. Collapsed (0dp) when idle so it doesn't shove content.
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun AiDownloadBanner(
    progress: Float?,
    aiStatus: AiStatus,
    onRetry: () -> Unit,
) {
    // Track progress→null transitions so we can flash an "AI ready" label for
    // ~2s after download completes. `rememberSaveable` isn't needed — on
    // config-change we re-read the VM state; on app restart the banner should
    // not persist.
    var showReadyFlash by remember { mutableStateOf(false) }
    var lastProgress by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(progress, aiStatus) {
        val prev = lastProgress
        lastProgress = progress
        if (prev != null && progress == null && aiStatus is AiStatus.Ready) {
            showReadyFlash = true
            delay(2_000)
            showReadyFlash = false
        }
    }

    val isDownloading = progress != null
    val isUnavailableOrEmpty = progress == null &&
        (aiStatus is AiStatus.Failed || aiStatus is AiStatus.Unavailable || aiStatus is AiStatus.Empty)
    val shouldRender = isDownloading || showReadyFlash || isUnavailableOrEmpty
    if (!shouldRender) return

    val clickMod = if (isUnavailableOrEmpty && aiStatus is AiStatus.Failed) {
        Modifier.clickable(onClick = onRetry)
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(clickMod)
            .padding(horizontal = Dimens.xxl, vertical = Dimens.md),
    ) {
        when {
            isDownloading -> {
                val pct = ((progress ?: 0f).coerceIn(0f, 1f) * 100).toInt()
                MonoSectionLabel("Downloading AI model · ${pct}%")
                Spacer(Modifier.height(Dimens.xs + 2.dp))
                LinearProgressIndicator(
                    progress = { (progress ?: 0f).coerceIn(0f, 1f) },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                )
            }
            showReadyFlash -> {
                MonoSectionLabel("AI ready")
                Spacer(Modifier.height(Dimens.xs + 2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            aiStatus is AiStatus.Empty -> {
                MonoSectionLabel("cloud pool empty — variants being generated")
                Spacer(Modifier.height(Dimens.xs + 2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
                )
            }
            isUnavailableOrEmpty -> {
                MonoSectionLabel("AI unavailable — tap to retry")
                Spacer(Modifier.height(Dimens.xs + 2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
                )
            }
        }
    }
}

@Composable
private fun HabitRow(
    name: String,
    active: Boolean,
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
// Previews — designer / reviewer can eyeball all 3 banner variants.
// ─────────────────────────────────────────────────────────────────────────

@Preview(name = "banner · downloading 45%", showBackground = true)
@Composable
private fun PreviewBannerDownloading() {
    UnReminderTheme {
        AiDownloadBanner(
            progress = 0.45f,
            aiStatus = AiStatus.Downloading(0.45f),
            onRetry = {},
        )
    }
}

@Preview(name = "banner · unavailable", showBackground = true)
@Composable
private fun PreviewBannerUnavailable() {
    UnReminderTheme {
        AiDownloadBanner(
            progress = null,
            aiStatus = AiStatus.Failed,
            onRetry = {},
        )
    }
}

@Preview(name = "banner · idle (renders nothing)", showBackground = true)
@Composable
private fun PreviewBannerIdle() {
    UnReminderTheme {
        AiDownloadBanner(
            progress = null,
            aiStatus = AiStatus.Ready,
            onRetry = {},
        )
    }
}
