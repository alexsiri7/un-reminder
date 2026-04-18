package com.alexsiri7.unreminder.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToLocations: () -> Unit,
    onNavigateToFeedback: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refreshPermissions() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Permissions", style = MaterialTheme.typography.titleMedium)

            PermissionCard(
                title = "Notifications",
                granted = uiState.hasNotificationPermission,
                onRequest = {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            )

            PermissionCard(
                title = "Fine Location",
                granted = uiState.hasFineLocationPermission,
                onRequest = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            )

            PermissionCard(
                title = "Background Location",
                granted = uiState.hasBackgroundLocationPermission,
                onRequest = {
                    locationPermissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    )
                }
            )

            PermissionCard(
                title = "Exact Alarms",
                granted = uiState.hasExactAlarmPermission,
                onRequest = { /* must open system settings */ }
            )

            Text("Actions", style = MaterialTheme.typography.titleMedium)

            Button(
                onClick = onNavigateToLocations,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Set Locations")
            }

            OutlinedButton(
                onClick = { viewModel.surpriseMe() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Surprise me")
            }

            OutlinedButton(
                onClick = { viewModel.testTriggerNow() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Test Trigger Now")
            }

            OutlinedButton(
                onClick = { viewModel.regenerateTriggers() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Regenerate Tomorrow's Triggers")
            }

            OutlinedButton(
                onClick = onNavigateToFeedback,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send Feedback")
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (granted) "Granted" else "Not granted",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (granted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
            if (!granted) {
                Button(onClick = onRequest) {
                    Text("Grant")
                }
            }
        }
    }
}
