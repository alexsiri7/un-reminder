package net.interstellarai.unreminder.ui.recent

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.BugReport
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.ui.theme.CompletedFull
import net.interstellarai.unreminder.ui.theme.CompletedLowFloor
import net.interstellarai.unreminder.ui.theme.Dimens
import net.interstellarai.unreminder.ui.theme.Dismissed
import net.interstellarai.unreminder.ui.theme.DisplayHuge
import net.interstellarai.unreminder.ui.theme.DisplaySmall
import net.interstellarai.unreminder.ui.theme.MonoContextStrip
import net.interstellarai.unreminder.ui.theme.MonoLabelTiny
import net.interstellarai.unreminder.ui.theme.MonoSectionLabel
import net.interstellarai.unreminder.ui.theme.NavPill
import net.interstellarai.unreminder.ui.theme.SansBody
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ─────────────────────────────────────────────────────────────────────────
// Recent triggers — visual pass using the handoff's language: mono meta
// labels, italic serif prompt text, a small accent dot for status rather
// than a suggestion-chip. ViewModel untouched.
// ─────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentTriggersScreen(
    onNavigateToFeedback: () -> Unit = {},
    viewModel: RecentTriggersViewModel = hiltViewModel(),
) {
    val triggers by viewModel.triggers.collectAsStateWithLifecycle()
    val formatter = DateTimeFormatter.ofPattern("MMM d \u00b7 HH:mm")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToFeedback,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
            ) {
                Icon(Icons.Default.BugReport, contentDescription = "Send Feedback")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = Dimens.xxl,
                    vertical = Dimens.xl,
                ),
            ) {
                MonoContextStrip("recent")
                Spacer(Modifier.height(Dimens.sm))
                Text(
                    "what happened",
                    style = DisplayHuge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            if (triggers.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "no triggers yet",
                        style = DisplaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Dimens.sm),
                ) {
                    items(triggers, key = { it.trigger.id }) { item ->
                        TriggerRow(
                            habitName = item.habitName ?: "unknown",
                            prompt = item.trigger.generatedPrompt,
                            status = item.trigger.status,
                            scheduledText = item.trigger.scheduledAt
                                .atZone(ZoneId.systemDefault())
                                .format(formatter),
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
private fun TriggerRow(
    habitName: String,
    prompt: String?,
    status: TriggerStatus,
    scheduledText: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.lg, vertical = Dimens.md + 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        StatusDot(status)
        Spacer(Modifier.width(Dimens.md - 2.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = habitName,
                style = DisplaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (!prompt.isNullOrBlank()) {
                Spacer(Modifier.height(Dimens.xs))
                Text(
                    text = prompt,
                    style = SansBody,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                )
            }
            Spacer(Modifier.height(Dimens.xs))
            Row {
                MonoSectionLabel(statusLabel(status))
                Spacer(Modifier.width(Dimens.sm))
                Text(
                    scheduledText,
                    style = MonoLabelTiny,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
private fun StatusDot(status: TriggerStatus) {
    val color = when (status) {
        TriggerStatus.COMPLETED -> CompletedFull
        TriggerStatus.COMPLETED_FULL -> CompletedFull
        TriggerStatus.COMPLETED_LOW_FLOOR -> CompletedLowFloor
        TriggerStatus.DISMISSED -> Dismissed
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .padding(top = Dimens.xs + 2.dp)
            .background(color, CircleShape)
            .size(8.dp),
    )
}

private fun statusLabel(status: TriggerStatus): String = when (status) {
    TriggerStatus.COMPLETED -> "done"
    TriggerStatus.COMPLETED_FULL -> "done \u00b7 full"
    TriggerStatus.COMPLETED_LOW_FLOOR -> "done \u00b7 floor"
    TriggerStatus.DISMISSED -> "dismissed"
    else -> status.name.lowercase().replace('_', ' ')
}
