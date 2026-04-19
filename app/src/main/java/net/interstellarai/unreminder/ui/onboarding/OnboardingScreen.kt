package net.interstellarai.unreminder.ui.onboarding

import android.Manifest
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import java.time.LocalTime
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) onFinished()
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refreshPermissions() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Get Started") },
                actions = {
                    TextButton(onClick = { viewModel.skip() }) { Text("Skip") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Step 1: Permissions
            StepCard(
                number = 1,
                title = "Grant Permissions",
                isActive = uiState.step == 0,
                isDone = uiState.step > 0
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        enabled = !uiState.hasNotificationPermission,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (uiState.hasNotificationPermission) "Notifications \u2713" else "Grant Notifications")
                    }
                    OutlinedButton(
                        onClick = {
                            locationLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        enabled = !uiState.hasFineLocationPermission,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (uiState.hasFineLocationPermission) "Location \u2713" else "Grant Location")
                    }
                }
                Button(
                    onClick = { viewModel.advanceToStep(1) },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Next") }
            }

            // Step 2: Add First Habit
            StepCard(
                number = 2,
                title = "Add Your First Habit",
                isActive = uiState.step == 1,
                isDone = uiState.step > 1
            ) {
                OutlinedTextField(
                    value = uiState.habitName,
                    onValueChange = { viewModel.updateHabitName(it) },
                    label = { Text("Habit name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { viewModel.advanceToStep(2) }) { Text("Skip") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.advanceToStep(2) },
                        enabled = uiState.habitName.isNotBlank()
                    ) { Text("Next") }
                }
            }

            // Step 3: Set a Reminder Window
            StepCard(
                number = 3,
                title = "Set a Reminder Window",
                isActive = uiState.step == 2,
                isDone = uiState.step > 2
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(context, { _, h, m ->
                                viewModel.updateWindowStartTime(LocalTime.of(h, m))
                            }, uiState.windowStartTime.hour, uiState.windowStartTime.minute, true).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("From: ${uiState.windowStartTime}") }
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(context, { _, h, m ->
                                viewModel.updateWindowEndTime(LocalTime.of(h, m))
                            }, uiState.windowEndTime.hour, uiState.windowEndTime.minute, true).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("To: ${uiState.windowEndTime}") }
                }
                Text(
                    "Monday \u2013 Friday",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Button(
                    onClick = {
                        viewModel.completeOnboarding(
                            saveHabit = uiState.habitName.isNotBlank(),
                            saveWindow = true
                        )
                    },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Done") }
            }
        }
    }
}

@Composable
private fun StepCard(
    number: Int,
    title: String,
    isActive: Boolean,
    isDone: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (isDone) "\u2713 $title" else "$number. $title",
                style = MaterialTheme.typography.titleMedium
            )
            AnimatedVisibility(visible = isActive) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content()
                }
            }
        }
    }
}
