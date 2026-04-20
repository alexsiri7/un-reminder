package net.interstellarai.unreminder.ui.habit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.interstellarai.unreminder.data.db.VariationEntity
import net.interstellarai.unreminder.data.db.WindowEntity
import net.interstellarai.unreminder.service.llm.AiStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import net.interstellarai.unreminder.ui.theme.Dimens
import net.interstellarai.unreminder.ui.theme.DisplayLarge
import net.interstellarai.unreminder.ui.theme.DisplayMedium
import net.interstellarai.unreminder.ui.theme.DisplaySmall
import net.interstellarai.unreminder.ui.theme.MonoLabel
import net.interstellarai.unreminder.ui.theme.MonoLabelTiny
import net.interstellarai.unreminder.ui.theme.MonoSectionLabel
import net.interstellarai.unreminder.ui.theme.NavPill
import net.interstellarai.unreminder.ui.theme.SansBody
import net.interstellarai.unreminder.ui.theme.SansBodyStrong
import net.interstellarai.unreminder.ui.theme.UnReminderShapes

// ─────────────────────────────────────────────────────────────────────────
// Habit editor — mirrors `components/editor.jsx`:
//   - Thin top bar ("← back · EDITING · save")
//   - "habit name" label + big display-serif field underlined in accent
//   - "gemma · on-device" AI-assist strip with an "✦ autofill" accent pill
//   - Full + low-floor description fields rendered as italic serif blocks
//   - Location chips with sharp corners, filled-accent when selected
//   - Dark "preview" card at the bottom (ink bg, bg ink)
//   - Bottom row: active toggle + delete habit link
// ViewModel untouched.
// ─────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitEditScreen(
    habitId: Long?,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    viewModel: HabitEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allLocations by viewModel.allLocations.collectAsStateWithLifecycle()
    val allWindows by viewModel.allWindows.collectAsStateWithLifecycle()
    val aiStatus by viewModel.aiStatus.collectAsStateWithLifecycle()
    val unusedVariations by viewModel.unusedVariations.collectAsStateWithLifecycle()
    val recentlyUsedVariations by viewModel.recentlyUsedVariations.collectAsStateWithLifecycle()
    val totalVariationCount by viewModel.totalVariationCount.collectAsStateWithLifecycle()
    val flashAlpha = remember { Animatable(0f) }

    LaunchedEffect(habitId) {
        if (habitId != null) viewModel.loadHabit(habitId)
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onNavigateBack()
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        val msg = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearError()
    }

    LaunchedEffect(uiState.showSpendCapLink) {
        if (!uiState.showSpendCapLink) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Spend cap reached — check Settings for today's usage.",
            actionLabel = "Settings",
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            onNavigateToSettings()
        }
        viewModel.clearSpendCapLink()
    }

    LaunchedEffect(uiState.fieldsFlashing) {
        if (uiState.fieldsFlashing) {
            try {
                flashAlpha.snapTo(0.3f)
                flashAlpha.animateTo(0f, animationSpec = tween(durationMillis = 600))
            } finally {
                viewModel.clearFieldsFlash()
            }
        }
    }

    val previewText = uiState.previewNotification
    if (uiState.showPreviewDialog && previewText != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPreviewDialog() },
            title = { Text("Notification preview", style = DisplaySmall) },
            text = { Text(previewText, style = SansBody) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissPreviewDialog() }) { Text("Close") }
            },
            containerColor = MaterialTheme.colorScheme.background,
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            EditorTopBar(
                isNew = habitId == null,
                onBack = onNavigateBack,
                onSave = { viewModel.save() },
            )

            Column(modifier = Modifier.padding(horizontal = Dimens.xxl, vertical = Dimens.xl)) {
                MonoSectionLabel("habit name")
                Spacer(Modifier.height(Dimens.sm))
                UnderlinedDisplayField(
                    value = uiState.name,
                    onValueChange = viewModel::updateName,
                    placeholder = "e.g. meditation",
                )
            }

            val aiHelper: String? = when {
                aiStatus is AiStatus.Unavailable -> "AI unavailable on this build"
                aiStatus is AiStatus.Empty -> "pool empty — AI variants being regenerated"
                else -> null
            }
            AiAssistStrip(
                enabled = uiState.name.length >= 2 &&
                    !uiState.isGeneratingFields &&
                    aiStatus is AiStatus.Ready,
                loading = uiState.isGeneratingFields,
                helperText = aiHelper,
                onAutofill = { viewModel.autofillWithAi() },
                modifier = Modifier.padding(horizontal = Dimens.xl),
            )

            Spacer(Modifier.height(Dimens.xl))

            Column(modifier = Modifier.padding(horizontal = Dimens.xxl)) {
                DescriptionBlock(
                    label = "full version",
                    value = uiState.fullDescription,
                    onValueChange = viewModel::updateFullDescription,
                    flashAlpha = flashAlpha.value,
                    placeholder = "what it actually is — e.g. 20-minute guided meditation",
                )
                Spacer(Modifier.height(Dimens.lg))
                DescriptionBlock(
                    label = "low-floor · counts as a win",
                    value = uiState.lowFloorDescription,
                    onValueChange = viewModel::updateLowFloorDescription,
                    flashAlpha = flashAlpha.value,
                    placeholder = "the minimum — e.g. 3 deep breaths",
                )
            }

            Column(modifier = Modifier.padding(horizontal = Dimens.xxl, vertical = Dimens.xxl)) {
                MonoSectionLabel("eligible at")
                Spacer(Modifier.height(Dimens.md - 2.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sm),
                ) {
                    SelectionChip(
                        label = "Anywhere",
                        selected = uiState.selectedLocationIds.isEmpty(),
                        muted = uiState.selectedLocationIds.isEmpty(),
                        onClick = { viewModel.setAnywhere() },
                    )
                    allLocations.forEach { loc ->
                        SelectionChip(
                            label = loc.name,
                            selected = loc.id in uiState.selectedLocationIds,
                            onClick = { viewModel.toggleLocation(loc.id) },
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = Dimens.xxl, vertical = Dimens.xxl)) {
                MonoSectionLabel("when")
                Spacer(Modifier.height(Dimens.md - 2.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sm),
                ) {
                    SelectionChip(
                        label = "Any time",
                        selected = uiState.selectedWindowIds.isEmpty(),
                        muted = uiState.selectedWindowIds.isEmpty(),
                        onClick = { viewModel.setAnyTime() },
                    )
                    allWindows.forEach { win ->
                        SelectionChip(
                            label = win.label(),
                            selected = win.id in uiState.selectedWindowIds,
                            onClick = { viewModel.toggleWindow(win.id) },
                        )
                    }
                }
            }

            PreviewCard(
                enabled = uiState.name.isNotBlank() &&
                    uiState.fullDescription.isNotBlank() &&
                    uiState.lowFloorDescription.isNotBlank() &&
                    !uiState.isGeneratingFields,
                loading = uiState.isGeneratingFields,
                onResample = { viewModel.previewNotification() },
                modifier = Modifier.padding(horizontal = Dimens.xl),
            )

            Spacer(Modifier.height(Dimens.xxl))

            if (habitId != null) {
                QueuedNotificationsSection(
                    unused = unusedVariations,
                    recentlyUsed = recentlyUsedVariations,
                    totalCount = totalVariationCount,
                    onRemove = viewModel::deleteVariation,
                    modifier = Modifier.padding(horizontal = Dimens.xl),
                )
                Spacer(Modifier.height(Dimens.xl))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = Dimens.hairline)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.xl, vertical = Dimens.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = uiState.active,
                        onCheckedChange = viewModel::updateActive,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.background,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.background,
                            uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        ),
                    )
                    Spacer(Modifier.size(Dimens.md - 2.dp))
                    Text("Active", style = SansBodyStrong, color = MaterialTheme.colorScheme.onBackground)
                }
                // "delete habit" link is in the handoff — intentionally NOT wired to
                // the VM delete because the redesign is visual-only and deleting from
                // the editor would be a behaviour change vs main. A follow-up issue
                // should connect this.
                Text(
                    "delete habit",
                    style = MonoLabel,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }

            NavPill()
        }
    }
}

@Composable
private fun EditorTopBar(
    isNew: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.lg, vertical = Dimens.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "\u2190 back",
            style = MonoLabel,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.clickable(onClick = onBack),
        )
        Text(
            if (isNew) "new" else "editing",
            style = MonoLabelTiny,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
        )
        Text(
            "save",
            style = MonoLabel.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onSave),
        )
    }
}

@Composable
private fun UnderlinedDisplayField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = DisplayLarge.copy(color = MaterialTheme.colorScheme.onBackground),
        placeholder = {
            Text(
                placeholder,
                style = DisplayLarge.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)),
            )
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.primary,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AiAssistStrip(
    enabled: Boolean,
    loading: Boolean,
    helperText: String?,
    onAutofill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonAlpha = if (enabled) 1f else 0.4f
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, UnReminderShapes.small)
                .padding(horizontal = Dimens.lg, vertical = Dimens.md + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                MonoSectionLabel("gemma · on-device")
                Spacer(Modifier.height(2.dp))
                Text(
                    "Autofill descriptions",
                    style = SansBodyStrong,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = buttonAlpha),
                        shape = UnReminderShapes.small,
                    )
                    .let {
                        if (enabled) it.clickable(onClick = onAutofill) else it
                    }
                    .padding(horizontal = Dimens.md + 2.dp, vertical = Dimens.sm),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        "\u2726 autofill",
                        style = MonoLabel.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
        if (helperText != null) {
            Spacer(Modifier.height(Dimens.xs))
            Text(
                text = helperText,
                style = MonoLabelTiny,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                modifier = Modifier.padding(start = Dimens.md + 2.dp),
            )
        }
    }
}

@Composable
private fun DescriptionBlock(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    flashAlpha: Float,
    placeholder: String,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MonoSectionLabel(label)
            if (value.isNotBlank()) {
                Text(
                    "\u2726 filled",
                    style = MonoLabelTiny,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(Dimens.sm - 2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = flashAlpha),
                    shape = UnReminderShapes.small,
                ),
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = DisplaySmall.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                placeholder = {
                    Text(
                        placeholder,
                        style = DisplaySmall.copy(
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                        ),
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SelectionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    muted: Boolean = false,
) {
    val (bg, fg) = if (selected) {
        MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    } else {
        Color.Transparent to MaterialTheme.colorScheme.onBackground
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
    }
    Box(
        modifier = Modifier
            .background(bg, UnReminderShapes.small)
            .border(BorderStroke(1.5.dp, borderColor), UnReminderShapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.md + 2.dp, vertical = Dimens.sm),
    ) {
        Text(
            text = label,
            style = SansBodyStrong,
            color = fg.copy(alpha = if (muted && !selected) 0.5f else 1f),
        )
    }
}

@Composable
private fun PreviewCard(
    enabled: Boolean,
    loading: Boolean,
    onResample: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = Dimens.xs)) {
        MonoSectionLabel("preview · sampled from gemma")
        Spacer(Modifier.height(Dimens.md - 2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.onBackground, UnReminderShapes.small)
                .padding(horizontal = Dimens.lg, vertical = Dimens.lg),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "UN-REMINDER · NOW",
                        style = MonoLabelTiny,
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                    )
                    Box(
                        modifier = Modifier
                            .let { if (enabled) it.clickable(onClick = onResample) else it },
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.background,
                            )
                        } else {
                            Text(
                                "\u21bb resample",
                                style = MonoLabelTiny,
                                color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Dimens.sm - 2.dp))
                Text(
                    "Settle for a moment \u2014 the floor is enough.",
                    style = DisplayMedium,
                    color = MaterialTheme.colorScheme.background,
                )
            }
        }
    }
}

@Composable
private fun QueuedNotificationsSection(
    unused: List<VariationEntity>,
    recentlyUsed: List<VariationEntity>,
    totalCount: Int,
    onRemove: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d · HH:mm") }
    val dateOnlyFormatter = remember { DateTimeFormatter.ofPattern("MMM d") }

    Column(modifier = modifier.fillMaxWidth()) {
        // Header row — always visible, toggles expansion
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = Dimens.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonoSectionLabel("queued notifications")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${unused.size} / $totalCount",
                    style = MonoLabelTiny,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                )
                Spacer(Modifier.size(Dimens.xs))
                Text(
                    if (expanded) "▾" else "▸",
                    style = MonoLabelTiny,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                )
            }
        }

        if (expanded) {
            Spacer(Modifier.height(Dimens.sm))

            // Count summary + last refilled
            val lastRefilled = unused.maxByOrNull { it.generatedAt }?.generatedAt
            val usedTodayCount = recentlyUsed.count { v ->
                v.consumedAt?.atZone(ZoneId.systemDefault())?.toLocalDate() ==
                    java.time.LocalDate.now()
            }
            Text(
                buildString {
                    append("${unused.size} unused")
                    append(" · $usedTodayCount used today")
                    append(" · $totalCount total")
                    if (lastRefilled != null) {
                        append(" · last refilled: ")
                        append(lastRefilled.atZone(ZoneId.systemDefault()).format(
                            dateOnlyFormatter
                        ))
                    }
                },
                style = MonoLabelTiny,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            )

            Spacer(Modifier.height(Dimens.md))

            if (unused.isEmpty()) {
                Text(
                    "pool empty — refill pending",
                    style = SansBody,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            } else {
                MonoSectionLabel("unused")
                Spacer(Modifier.height(Dimens.sm))
                unused.forEach { variation ->
                    VariationRow(
                        variation = variation,
                        onRemove = { onRemove(variation.id) },
                        formatter = formatter,
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        thickness = Dimens.hairline,
                    )
                }
            }

            if (recentlyUsed.isNotEmpty()) {
                Spacer(Modifier.height(Dimens.lg))
                MonoSectionLabel("recently used")
                Spacer(Modifier.height(Dimens.sm))
                recentlyUsed.forEach { variation ->
                    RecentlyUsedVariationRow(variation = variation, formatter = formatter)
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        thickness = Dimens.hairline,
                    )
                }
            }
        }
    }
}

@Composable
private fun VariationRow(
    variation: VariationEntity,
    onRemove: () -> Unit,
    formatter: DateTimeFormatter,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.md),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = variation.text,
                style = SansBody,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(Dimens.xs))
            Text(
                text = variation.generatedAt
                    .atZone(ZoneId.systemDefault())
                    .format(formatter),
                style = MonoLabelTiny,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            )
        }
        Box {
            Text(
                "⋮",
                style = MonoLabel,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier
                    .clickable { menuExpanded = true }
                    .padding(start = Dimens.sm),
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Remove", style = SansBody) },
                    onClick = {
                        menuExpanded = false
                        onRemove()
                    },
                )
            }
        }
    }
}

@Composable
private fun RecentlyUsedVariationRow(
    variation: VariationEntity,
    formatter: DateTimeFormatter,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.md),
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            Text(
                text = variation.text,
                style = SansBody,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(Dimens.xs))
            val firedAt = variation.consumedAt
            if (firedAt != null) {
                Text(
                    text = "fired ${firedAt.atZone(ZoneId.systemDefault()).format(formatter)}",
                    style = MonoLabelTiny,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                )
            }
        }
    }
}

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun WindowEntity.label(): String =
    "${startTime.format(TIME_FMT)}\u2013${endTime.format(TIME_FMT)}"
