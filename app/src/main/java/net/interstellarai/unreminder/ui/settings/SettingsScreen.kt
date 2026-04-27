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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    onNavigateToCloudSettings: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.refreshPermissions()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshPermissions() }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refreshPermissions() }

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

            SettingsSection(
                label = "cloud ai (optional)",
                modifier = Modifier.padding(horizontal = Dimens.xxl),
            ) {
                OutlineAction(
                    label = "cloud ai settings",
                    onClick = onNavigateToCloudSettings,
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
