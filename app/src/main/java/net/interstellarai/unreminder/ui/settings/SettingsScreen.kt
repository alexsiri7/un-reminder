package net.interstellarai.unreminder.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.interstellarai.unreminder.service.llm.AiStatus
import net.interstellarai.unreminder.service.llm.ModelCatalog
import net.interstellarai.unreminder.service.llm.ModelDescriptor
import net.interstellarai.unreminder.ui.theme.Dimens
import net.interstellarai.unreminder.ui.theme.DisplayHuge
import net.interstellarai.unreminder.ui.theme.DisplaySmall
import net.interstellarai.unreminder.ui.theme.MonoContextStrip
import net.interstellarai.unreminder.ui.theme.MonoLabel
import net.interstellarai.unreminder.ui.theme.MonoLabelTiny
import net.interstellarai.unreminder.ui.theme.MonoSectionLabel
import net.interstellarai.unreminder.ui.theme.NavPill
import net.interstellarai.unreminder.ui.theme.SansBody
import net.interstellarai.unreminder.ui.theme.SansBodyStrong
import net.interstellarai.unreminder.ui.theme.UnReminderShapes
import net.interstellarai.unreminder.ui.theme.UnReminderTheme

// ─────────────────────────────────────────────────────────────────────────
// Settings — no explicit screen in the handoff, but styled to match the rest
// of the app: context strip + serif heading + mono section labels + sharp-
// cornered "soft" permission rows and accent action buttons.
// ViewModel untouched.
// ─────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToLocations: () -> Unit,
    onNavigateToFeedback: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeModel by viewModel.activeModel.collectAsStateWithLifecycle()
    val aiStatus by viewModel.aiStatus.collectAsStateWithLifecycle()
    val workerUrl by viewModel.workerUrl.collectAsStateWithLifecycle()
    val workerSecret by viewModel.workerSecret.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshPermissions() }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refreshPermissions() }

    // Pending selection: drives the confirmation dialog. Holds (previous,
    // newly-chosen) so the dialog can name both models explicitly.
    var pendingSelection by remember { mutableStateOf<Pair<ModelDescriptor, ModelDescriptor>?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        val msg = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearError()
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
            Column(
                modifier = Modifier.padding(
                    horizontal = Dimens.xxl,
                    vertical = Dimens.xl,
                ),
            ) {
                MonoContextStrip("settings")
                Spacer(Modifier.height(Dimens.sm))
                Text(
                    "settings",
                    style = DisplayHuge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            ModelCatalogSection(
                catalog = viewModel.catalog,
                active = activeModel,
                status = aiStatus,
                onSelect = { chosen ->
                    if (chosen.id != activeModel.id) {
                        pendingSelection = activeModel to chosen
                    }
                },
                modifier = Modifier.padding(horizontal = Dimens.xxl),
            )

            Spacer(Modifier.height(Dimens.xxl))

            SettingsSection(
                label = "cloud ai (optional)",
                modifier = Modifier.padding(horizontal = Dimens.xxl),
            ) {
                OutlinedTextField(
                    value = workerUrl,
                    onValueChange = { viewModel.setWorkerUrl(it) },
                    label = { Text("worker url", style = MonoLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Dimens.sm))
                OutlinedTextField(
                    value = workerSecret,
                    onValueChange = { viewModel.setWorkerSecret(it) },
                    label = { Text("worker secret", style = MonoLabel) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(Dimens.xxl))

            SettingsSection(
                label = "permissions",
                modifier = Modifier.padding(horizontal = Dimens.xxl),
            ) {
                PermissionRow(
                    title = "Notifications",
                    granted = uiState.hasNotificationPermission,
                    onRequest = {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = Dimens.hairline,
                )
                PermissionRow(
                    title = "Fine location",
                    granted = uiState.hasFineLocationPermission,
                    onRequest = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = Dimens.hairline,
                )
                PermissionRow(
                    title = "Background location",
                    granted = uiState.hasBackgroundLocationPermission,
                    onRequest = {
                        locationPermissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                        )
                    },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = Dimens.hairline,
                )
                PermissionRow(
                    title = "Exact alarms",
                    granted = uiState.hasExactAlarmPermission,
                    onRequest = { /* must open system settings */ },
                )
            }

            Spacer(Modifier.height(Dimens.xxl))

            SettingsSection(
                label = "actions",
                modifier = Modifier.padding(horizontal = Dimens.xxl),
            ) {
                FilledAction(
                    label = "set locations",
                    onClick = onNavigateToLocations,
                )
                Spacer(Modifier.height(Dimens.sm))
                OutlineAction(label = "surprise me") { viewModel.surpriseMe() }
                Spacer(Modifier.height(Dimens.sm))
                OutlineAction(label = "test trigger now") { viewModel.testTriggerNow() }
                Spacer(Modifier.height(Dimens.sm))
                OutlineAction(label = "regenerate tomorrow's triggers") {
                    viewModel.regenerateTriggers()
                }
                Spacer(Modifier.height(Dimens.sm))
                OutlineAction(label = "send feedback", onClick = onNavigateToFeedback)
            }

            Spacer(Modifier.height(Dimens.xxl))
            NavPill()
        }
    }

    val pending = pendingSelection
    if (pending != null) {
        val (previous, chosen) = pending
        AlertDialog(
            onDismissRequest = { pendingSelection = null },
            title = {
                Text(
                    "Switch to ${chosen.displayName}?",
                    style = DisplaySmall,
                )
            },
            text = {
                Column {
                    Text(
                        "The download (${formatSize(chosen.sizeBytes)}) will start on Wi-Fi. " +
                            "AI generation will be unavailable until it finishes.",
                        style = SansBody,
                    )
                    if (chosen.notes != null) {
                        Spacer(Modifier.height(Dimens.sm))
                        Text(
                            chosen.notes,
                            style = MonoLabelTiny,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                    Spacer(Modifier.height(Dimens.md))
                    Text(
                        "You can free up space by deleting the previous model " +
                            "(${previous.displayName}).",
                        style = SansBody,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.selectModel(chosen.id)
                    viewModel.deleteOtherModelFiles(keep = chosen)
                    pendingSelection = null
                }) {
                    Text("Switch & delete previous")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.selectModel(chosen.id)
                    pendingSelection = null
                }) {
                    Text("Switch & keep previous")
                }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// AI model catalog UI
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun ModelCatalogSection(
    catalog: List<ModelDescriptor>,
    active: ModelDescriptor,
    status: AiStatus,
    onSelect: (ModelDescriptor) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        MonoSectionLabel("ai model")
        Spacer(Modifier.height(Dimens.md - 2.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, UnReminderShapes.small),
        ) {
            // Active model row — always visible, tap to expand.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = Dimens.lg, vertical = Dimens.md + 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        active.displayName,
                        style = DisplaySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        statusLabel(status),
                        style = MonoLabelTiny,
                        color = statusColor(status),
                    )
                }
                Text(
                    if (expanded) "hide \u2191" else "change \u2193",
                    style = MonoLabel.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (status is AiStatus.Downloading) {
                // Inline progress bar so the user sees download activity
                // without leaving the Settings screen.
                LinearProgressIndicator(
                    progress = { status.fraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.lg)
                        .padding(bottom = Dimens.md),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f),
                )
            }

            if (expanded) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.background,
                    thickness = Dimens.hairline,
                )
                catalog.forEachIndexed { index, desc ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.background,
                            thickness = Dimens.hairline,
                        )
                    }
                    CatalogEntryRow(
                        descriptor = desc,
                        isActive = desc.id == active.id,
                        onClick = { onSelect(desc) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogEntryRow(
    descriptor: ModelDescriptor,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isActive, onClick = onClick)
            .padding(horizontal = Dimens.lg, vertical = Dimens.md + 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                descriptor.displayName,
                style = DisplaySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                descriptor.description,
                style = SansBody,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(Dimens.xs))
            Text(
                "${formatSize(descriptor.sizeBytes)} \u00B7 needs ${descriptor.minDeviceMemoryGb}+ GB RAM",
                style = MonoLabelTiny,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            if (descriptor.notes != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    descriptor.notes,
                    style = MonoLabelTiny,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        Spacer(Modifier.height(Dimens.xs))
        Text(
            if (isActive) "\u2713 active" else "select \u2192",
            style = MonoLabel.copy(fontWeight = FontWeight.SemiBold),
            color = if (isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun statusLabel(status: AiStatus): String = when (status) {
    is AiStatus.Ready -> "ready"
    is AiStatus.Downloading -> "downloading ${(status.fraction * 100).toInt()}%"
    is AiStatus.Failed -> "failed \u2014 tap to change model"
    is AiStatus.Unavailable -> "unavailable \u2014 url is placeholder"
    is AiStatus.Idle -> "not downloaded"
}

@Composable
private fun statusColor(status: AiStatus): Color = when (status) {
    is AiStatus.Ready -> MaterialTheme.colorScheme.primary
    is AiStatus.Downloading -> MaterialTheme.colorScheme.primary
    is AiStatus.Failed, is AiStatus.Unavailable ->
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    is AiStatus.Idle -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
}

/** "2.58 GB", "689 MB". Keeps the UI stable across locales without pulling in NumberFormat. */
private fun formatSize(bytes: Long): String {
    val gb = bytes.toDouble() / 1_000_000_000.0
    if (gb >= 1.0) return "%.2f GB".format(gb)
    val mb = bytes.toDouble() / 1_000_000.0
    return "%.0f MB".format(mb)
}

@Composable
private fun SettingsSection(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        MonoSectionLabel(label)
        Spacer(Modifier.height(Dimens.md - 2.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, UnReminderShapes.small),
        ) {
            content()
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    onRequest: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !granted, onClick = onRequest)
            .padding(horizontal = Dimens.lg, vertical = Dimens.md + 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = DisplaySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (granted) "granted" else "not granted",
                style = MonoLabelTiny,
                color = if (granted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
        if (!granted) {
            Text(
                "grant \u2192",
                style = MonoLabel.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                "\u2713",
                style = SansBodyStrong,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun FilledAction(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary, UnReminderShapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = Dimens.md + 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.uppercase(),
            style = MonoLabel.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun OutlineAction(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent, UnReminderShapes.small)
            .border(
                1.5.dp,
                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                UnReminderShapes.small,
            )
            .clickable(onClick = onClick)
            .padding(vertical = Dimens.md + 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = SansBody,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Preview — mirrors the production UI path but uses stub state so the
// catalog section renders without Hilt wiring. Useful for design review
// and for the PR body screenshot.
// ─────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFE8EBD9)
@Composable
private fun ModelCatalogSectionPreview() {
    UnReminderTheme {
        Column(Modifier.padding(Dimens.xxl)) {
            ModelCatalogSection(
                catalog = ModelCatalog.all,
                active = ModelCatalog.default,
                status = AiStatus.Downloading(0.42f),
                onSelect = {},
            )
        }
    }
}
