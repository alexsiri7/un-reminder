package net.interstellarai.unreminder.ui.onboarding

import android.Manifest
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalTime
import androidx.compose.ui.platform.LocalContext
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

// ─────────────────────────────────────────────────────────────────────────
// Onboarding — three-step "get started" flow. The design handoff doesn't
// include a dedicated onboarding screen, so this reuses the language of the
// home and editor refs: big italic serif header, mono uppercase section
// labels, sharp-cornered accent buttons, and the soft surface "cards" that
// the home screen uses for its context strip.
// ViewModel untouched.
// ─────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) onFinished()
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshPermissions() }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refreshPermissions() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.xxl, vertical = Dimens.xl),
            verticalArrangement = Arrangement.spacedBy(Dimens.md),
        ) {
            Column(modifier = Modifier.padding(bottom = Dimens.sm)) {
                MonoContextStrip("get started")
                Spacer(Modifier.height(Dimens.sm))
                Text(
                    "welcome",
                    style = DisplayHuge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(Dimens.sm))
                Text(
                    "un-reminder nudges you toward habits at moments you might actually do them. three small steps.",
                    style = SansBody,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            }

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "skip \u2192",
                    style = MonoLabel,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.clickable { viewModel.skip() },
                )
            }

            StepCard(
                number = 1,
                title = "grant permissions",
                isActive = uiState.step == 0,
                isDone = uiState.step > 0,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                ) {
                    PermissionButton(
                        label = if (uiState.hasNotificationPermission) "Notifications \u2713"
                            else "Grant Notifications",
                        enabled = !uiState.hasNotificationPermission,
                        modifier = Modifier.weight(1f),
                    ) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    PermissionButton(
                        label = if (uiState.hasFineLocationPermission) "Location \u2713"
                            else "Grant Location",
                        enabled = !uiState.hasFineLocationPermission,
                        modifier = Modifier.weight(1f),
                    ) {
                        locationLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(Dimens.sm))
                AccentPillButton(
                    label = "next",
                    onClick = { viewModel.advanceToStep(1) },
                    modifier = Modifier.align(Alignment.End),
                )
            }

            StepCard(
                number = 2,
                title = "name a habit",
                isActive = uiState.step == 1,
                isDone = uiState.step > 1,
            ) {
                OutlinedTextField(
                    value = uiState.habitName,
                    onValueChange = { viewModel.updateHabitName(it) },
                    label = { Text("Habit name", style = MonoLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = UnReminderShapes.small,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    GhostButton(label = "skip") { viewModel.advanceToStep(2) }
                    Spacer(modifier = Modifier.width(Dimens.sm))
                    AccentPillButton(
                        label = "next",
                        onClick = { viewModel.advanceToStep(2) },
                        enabled = uiState.habitName.isNotBlank(),
                    )
                }
            }

            StepCard(
                number = 3,
                title = "set a reminder window",
                isActive = uiState.step == 2,
                isDone = uiState.step > 2,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                ) {
                    PermissionButton(
                        label = "From: ${uiState.windowStartTime}",
                        enabled = true,
                        modifier = Modifier.weight(1f),
                    ) {
                        TimePickerDialog(context, { _, h, m ->
                            viewModel.updateWindowStartTime(LocalTime.of(h, m))
                        }, uiState.windowStartTime.hour, uiState.windowStartTime.minute, true).show()
                    }
                    PermissionButton(
                        label = "To: ${uiState.windowEndTime}",
                        enabled = true,
                        modifier = Modifier.weight(1f),
                    ) {
                        TimePickerDialog(context, { _, h, m ->
                            viewModel.updateWindowEndTime(LocalTime.of(h, m))
                        }, uiState.windowEndTime.hour, uiState.windowEndTime.minute, true).show()
                    }
                }
                Text(
                    "monday \u2013 friday",
                    style = MonoLabelTiny,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = Dimens.sm - 2.dp),
                )
                AccentPillButton(
                    label = "done",
                    onClick = {
                        viewModel.completeOnboarding(
                            saveHabit = uiState.habitName.isNotBlank(),
                            saveWindow = true,
                        )
                    },
                    modifier = Modifier.align(Alignment.End),
                )
            }

            NavPill()
        }
    }
}

@Composable
private fun StepCard(
    number: Int,
    title: String,
    isActive: Boolean,
    isDone: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, UnReminderShapes.small)
            .padding(Dimens.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (isDone || isActive) accent else Color.Transparent)
                    .border(
                        1.dp,
                        if (isDone || isActive) accent else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isDone) "\u2713" else number.toString(),
                    style = MonoLabelTiny.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isDone || isActive) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(modifier = Modifier.width(Dimens.md - 2.dp))
            Text(
                text = title,
                style = DisplaySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = isActive) {
            Column(
                modifier = Modifier.padding(top = Dimens.md),
                verticalArrangement = Arrangement.spacedBy(Dimens.sm),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun PermissionButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val fg = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = modifier
            .background(Color.Transparent, UnReminderShapes.small)
            .border(
                1.5.dp,
                if (enabled) MaterialTheme.colorScheme.primary else fg.copy(alpha = 0.2f),
                UnReminderShapes.small,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Dimens.md, vertical = Dimens.sm + 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = SansBodyStrong,
            color = if (enabled) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun AccentPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val bg = if (enabled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    Box(
        modifier = modifier
            .background(bg, UnReminderShapes.small)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Dimens.lg, vertical = Dimens.sm + 2.dp),
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
private fun GhostButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.md, vertical = Dimens.sm + 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MonoLabel,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }
}
