package net.interstellarai.unreminder.ui.now

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.interstellarai.unreminder.domain.UnavailableReason
import net.interstellarai.unreminder.ui.theme.ActionChip
import net.interstellarai.unreminder.ui.theme.Dimens
import net.interstellarai.unreminder.ui.theme.DisplayHuge
import net.interstellarai.unreminder.ui.theme.DisplayMedium
import net.interstellarai.unreminder.ui.theme.DisplaySmall
import net.interstellarai.unreminder.ui.theme.FeedbackIconButton
import net.interstellarai.unreminder.ui.theme.MonoContextStrip
import net.interstellarai.unreminder.ui.theme.MonoLabel
import net.interstellarai.unreminder.ui.theme.MonoSectionLabel
import net.interstellarai.unreminder.ui.theme.NavPill
import net.interstellarai.unreminder.ui.theme.SansBody
import net.interstellarai.unreminder.ui.theme.UnReminderShapes

// ─────────────────────────────────────────────────────────────────────────
// Doable-now menu — the pull surface: three things you could do right now,
// each with a freshly worded ask, its mascot sprite and a single "did it"
// action. The shuffle and the peeked variants are held by the ViewModel and
// only redrawn on a fresh entry to the screen.
// ─────────────────────────────────────────────────────────────────────────

@Composable
fun NowMenuScreen(
    onAddHabit: () -> Unit,
    onNavigateToFeedback: () -> Unit = {},
    viewModel: NowMenuViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val daysWithAnyCompletion by viewModel.daysWithAnyCompletion.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    NowMenuContent(
        uiState = uiState,
        daysWithAnyCompletion = daysWithAnyCompletion,
        onComplete = viewModel::complete,
        onLoadMore = viewModel::loadMore,
        onAddHabit = onAddHabit,
        onNavigateToFeedback = onNavigateToFeedback,
    )
}

@Composable
internal fun NowMenuContent(
    uiState: NowMenuUiState,
    daysWithAnyCompletion: Int,
    onComplete: (Long) -> Unit,
    onLoadMore: () -> Unit,
    onAddHabit: () -> Unit,
    onNavigateToFeedback: () -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            NowMenuHeader(daysWithAnyCompletion, onNavigateToFeedback)

            when (uiState) {
                is NowMenuUiState.Loading -> Box(Modifier.fillMaxSize())
                is NowMenuUiState.NoHabits -> EmptyState(
                    title = "no habits yet",
                    hint = "add one to get started",
                    onHintClick = onAddHabit,
                )
                is NowMenuUiState.NothingDoable -> EmptyState(
                    title = "nothing doable right now",
                    hint = reasonLabel(uiState.reason),
                )
                is NowMenuUiState.Menu -> MenuList(uiState, onComplete, onLoadMore)
            }

            NavPill()
        }
    }
}

@Composable
private fun NowMenuHeader(daysWithAnyCompletion: Int, onNavigateToFeedback: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(
                start = Dimens.xxl,
                end = Dimens.xxl,
                top = Dimens.xl,
                bottom = Dimens.md,
            ),
        ) {
            MonoContextStrip("now")
            Spacer(Modifier.height(Dimens.sm))
            Text(
                text = "doable now",
                style = DisplayHuge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(Dimens.sm))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = daysWithAnyCompletion.toString(),
                    style = DisplayMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(Dimens.sm))
                MonoSectionLabel(
                    if (daysWithAnyCompletion == 1) "day with something done" else "days with something done",
                    modifier = Modifier.padding(bottom = Dimens.xs),
                )
            }
        }
        FeedbackIconButton(
            onClick = onNavigateToFeedback,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

@Composable
private fun MenuList(
    state: NowMenuUiState.Menu,
    onComplete: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Dimens.sm),
    ) {
        items(state.items, key = { it.habitId }) { item ->
            MenuRow(item = item, onComplete = { onComplete(item.habitId) })
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant,
                thickness = Dimens.hairline,
            )
        }
        if (state.canLoadMore) {
            item {
                Text(
                    "+ more",
                    style = MonoLabel,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier
                        .clickable(onClick = onLoadMore)
                        .padding(horizontal = Dimens.lg, vertical = Dimens.md + 2.dp),
                )
            }
        }
    }
}

@Composable
private fun MenuRow(item: NowMenuItem, onComplete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.lg, vertical = Dimens.md + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpriteTile(item.spriteRes)
        Spacer(Modifier.width(Dimens.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = DisplaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (item.text != null) {
                Spacer(Modifier.height(Dimens.xs))
                Text(
                    text = item.text,
                    style = SansBody,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                )
            }
        }
        Spacer(Modifier.width(Dimens.md))
        ActionChip(label = "did it", filled = true, onClick = onComplete)
    }
}

// The sprites are opaque full-colour tiles and deliberately the one saturated
// element on a calm palette, so they are shown untinted; the hairline in the
// palette's soft tone stops a pale tile bleeding into the light background.
@Composable
private fun SpriteTile(@DrawableRes spriteRes: Int) {
    Image(
        painter = painterResource(spriteRes),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(Dimens.spriteTile)
            .clip(UnReminderShapes.large)
            .border(Dimens.hairline, MaterialTheme.colorScheme.surfaceVariant, UnReminderShapes.large),
    )
}

@Composable
private fun EmptyState(
    title: String,
    hint: String,
    onHintClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = DisplaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(Dimens.sm))
        Text(
            hint,
            style = MonoLabel,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = if (onHintClick != null) Modifier.clickable(onClick = onHintClick) else Modifier,
        )
    }
}

private fun reasonLabel(reason: UnavailableReason): String = when (reason) {
    UnavailableReason.INACTIVE -> "all your habits are paused"
    UnavailableReason.LOCATION -> "nothing fits where you are"
    UnavailableReason.TIME_WINDOW -> "out of hours for all of them"
    UnavailableReason.COMPLETED -> "already done for today"
    UnavailableReason.COOLDOWN -> "cooling down — check back later"
    UnavailableReason.DAILY_LIMIT -> "daily limits reached"
}
