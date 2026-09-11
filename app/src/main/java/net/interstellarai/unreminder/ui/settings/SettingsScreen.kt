package net.interstellarai.unreminder.ui.settings

import android.Manifest
import android.app.TimePickerDialog
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.interstellarai.unreminder.service.geofence.RegistrationHealth
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
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ─────────────────────────────────────────────────────────────────────────
// Settings — no explicit screen in the handoff, but styled to match the rest
// of the app: context strip + serif heading + mono section labels + sharp-
// cornered "soft" permission rows and accent action buttons.
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
    val context = LocalContext.current

    LaunchedEffect(uiState.errorMessage) {
        val msg = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearError()
    }

    LaunchedEffect(uiState.testTriggered) {
        if (!uiState.testTriggered) return@LaunchedEffect
        snackbarHostState.showSnackbar("Trigger fired")
        viewModel.clearTestTriggered()
    }

    LaunchedEffect(uiState.testTriggeredEmpty) {
        if (!uiState.testTriggeredEmpty) return@LaunchedEffect
        snackbarHostState.showSnackbar("No eligible habits right now")
        viewModel.clearTestTriggeredEmpty()
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
                label = "personal context",
                modifier = Modifier.padding(horizontal = Dimens.xxl),
            ) {
                TextField(
                    value = uiState.personalContext,
                    onValueChange = {
                        if (it.length <= 500) viewModel.setPersonalContext(it)
                    },
                    placeholder = {
                        Text(
                            "e.g., use words of encouragement",
                            style = MonoLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    },
                    singleLine = false,
                    maxLines = 5,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.lg, vertical = Dimens.md),
                )
                Text(
                    "affects future AI-generated notifications",
                    style = MonoLabelTiny,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(
                        horizontal = Dimens.lg,
                        vertical = Dimens.md,
                    ),
                )
            }

            Spacer(Modifier.height(Dimens.xxl))

            SettingsSection(
                label = "evening invitation",
                modifier = Modifier.padding(horizontal = Dimens.xxl),
            ) {
                ToggleRow(
                    title = "Evening invitation",
                    subtitle = "one nudge on days with nothing done yet",
                    checked = uiState.eveningInvitationEnabled,
                    onCheckedChange = viewModel::setEveningInvitationEnabled,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = Dimens.hairline,
                )
                TimeRow(
                    title = "Around",
                    time = uiState.eveningInvitationTime,
                    enabled = uiState.eveningInvitationEnabled,
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, m -> viewModel.setEveningInvitationTime(LocalTime.of(h, m)) },
                            uiState.eveningInvitationTime.hour,
                            uiState.eveningInvitationTime.minute,
                            true,
                        ).show()
                    },
                )
            }

            Spacer(Modifier.height(Dimens.xxl))

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
            }

            Spacer(Modifier.height(Dimens.xxl))

            LocationTrackingSection(
                health = uiState.registrationHealth,
                status = uiState.locationTracking,
                onNavigateToLocations = onNavigateToLocations,
                modifier = Modifier.padding(horizontal = Dimens.xxl),
            )

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
                OutlineAction(label = "send feedback", onClick = onNavigateToFeedback)
            }

            Spacer(Modifier.height(Dimens.xxl))
            NavPill()
        }
    }
}

private val ClockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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
internal fun LocationTrackingSection(
    health: RegistrationHealth?,
    status: LocationTrackingStatus,
    onNavigateToLocations: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(label = "location tracking", modifier = modifier) {
        GeofenceCountRow(health)
        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceVariant,
            thickness = Dimens.hairline,
        )
        TrackingStatusRow(status)
        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceVariant,
            thickness = Dimens.hairline,
        )
        LinkRow(
            title = "Locations",
            subtitle = "where each location stands",
            onClick = onNavigateToLocations,
        )
    }
}

@Composable
private fun GeofenceCountRow(health: RegistrationHealth?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.lg, vertical = Dimens.md + 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Geofences registered",
                style = DisplaySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (health == null) "not checked yet"
                else "checked ${health.checkedAt.atZone(ZoneId.systemDefault()).format(ClockFormat)}",
                style = MonoLabelTiny,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
        Text(
            if (health == null) "\u2013" else "${health.registeredCount} / ${health.savedCount}",
            style = MonoLabel.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun TrackingStatusRow(status: LocationTrackingStatus) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.lg, vertical = Dimens.md + 2.dp),
    ) {
        Text(
            "Location tracking",
            style = DisplaySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            status.label,
            style = MonoLabelTiny,
            color = when {
                status.isFault -> MaterialTheme.colorScheme.error
                status == LocationTrackingStatus.Healthy -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            },
        )
        status.advice?.let { advice ->
            Spacer(Modifier.height(Dimens.xs))
            Text(
                advice,
                style = SansBody,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LinkRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                subtitle,
                style = MonoLabelTiny,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
        Text(
            "open \u2192",
            style = MonoLabel.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
        )
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
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                subtitle,
                style = MonoLabelTiny,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.background,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.background,
                uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            ),
        )
    }
}

@Composable
private fun TimeRow(
    title: String,
    time: LocalTime,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Dimens.lg, vertical = Dimens.md + 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = DisplaySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
        )
        Text(
            time.format(ClockFormat),
            style = MonoLabel.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.5f),
        )
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
