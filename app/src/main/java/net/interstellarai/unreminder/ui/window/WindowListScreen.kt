package net.interstellarai.unreminder.ui.window

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.interstellarai.unreminder.ui.theme.Dimens
import net.interstellarai.unreminder.ui.theme.DisplayHuge
import net.interstellarai.unreminder.ui.theme.DisplaySmall
import net.interstellarai.unreminder.ui.theme.MonoContextStrip
import net.interstellarai.unreminder.ui.theme.MonoLabel
import net.interstellarai.unreminder.ui.theme.MonoLabelTiny
import net.interstellarai.unreminder.ui.theme.MonoSectionLabel
import net.interstellarai.unreminder.ui.theme.NavPill
import net.interstellarai.unreminder.ui.theme.UnReminderShapes
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────
// Window list — the handoff doesn't draw this screen explicitly but the
// home screen's list affordances transfer directly: context strip, serif
// header, hairline-divider list rows, "anywhere"-style day chips.
// ViewModel untouched.
// ─────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WindowListScreen(
    onAddWindow: () -> Unit,
    onEditWindow: (Long) -> Unit,
    viewModel: WindowListViewModel = hiltViewModel(),
) {
    val windows by viewModel.windows.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddWindow,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add window")
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
                MonoContextStrip("windows")
                Spacer(Modifier.height(Dimens.sm))
                Text(
                    "when",
                    style = DisplayHuge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            if (windows.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "no windows yet",
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
                    MonoSectionLabel("${windows.count { it.active }} active")
                    Text(
                        "+ new",
                        style = MonoLabel,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.clickable(onClick = onAddWindow),
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Dimens.sm),
                ) {
                    items(windows, key = { it.id }) { window ->
                        WindowRow(
                            timeText = "${window.startTime} \u2013 ${window.endTime}",
                            frequency = window.frequencyPerDay,
                            daysBitmask = window.daysOfWeekBitmask,
                            active = window.active,
                            onClick = { onEditWindow(window.id) },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WindowRow(
    timeText: String,
    frequency: Int,
    daysBitmask: Int,
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
        Box(
            modifier = Modifier
                .size(Dimens.glyphBubble)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${frequency}\u00d7",
                style = MonoLabel,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.size(Dimens.md + 2.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = timeText,
                style = DisplaySmall.copy(
                    textDecoration = if (active) TextDecoration.None else TextDecoration.LineThrough,
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha),
            )
            Spacer(Modifier.height(Dimens.xs))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                DayOfWeek.entries.forEach { day ->
                    val bit = 1 shl (day.value - 1)
                    if (daysBitmask and bit != 0) {
                        Box(
                            modifier = Modifier
                                .border(
                                    Dimens.hairline,
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                                    UnReminderShapes.small,
                                )
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Text(
                                day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).lowercase(),
                                style = MonoLabelTiny,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f * alpha),
                            )
                        }
                    }
                }
            }
        }
    }
}
