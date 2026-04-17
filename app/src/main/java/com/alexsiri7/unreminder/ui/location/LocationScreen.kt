package com.alexsiri7.unreminder.ui.location

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(
    onNavigateBack: () -> Unit,
    viewModel: LocationViewModel = hiltViewModel()
) {
    val locations by viewModel.locations.collectAsStateWithLifecycle()

    var pendingLabel by remember { mutableStateOf<String?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            pendingLabel?.let { viewModel.setCurrentLocation(it) }
            pendingLabel = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Locations") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Set your locations so the app knows where you are for context-aware triggers.",
                style = MaterialTheme.typography.bodyMedium
            )

            listOf("HOME", "WORK").forEach { label ->
                val saved = locations.find { it.label == label }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(label, style = MaterialTheme.typography.titleMedium)
                        if (saved != null) {
                            Text(
                                "Lat: %.4f, Lng: %.4f".format(saved.lat, saved.lng),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Radius: ${saved.radiusM.toInt()}m",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text("Not set", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = {
                                pendingLabel = label
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Set to current location")
                        }
                    }
                }
            }

            Text(
                "Background location access is needed for geofence triggers. " +
                    "Grant it in Settings > Location > Allow all the time.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
