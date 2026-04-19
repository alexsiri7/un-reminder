package net.interstellarai.unreminder.ui.habit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitEditScreen(
    habitId: Long?,
    onNavigateBack: () -> Unit,
    viewModel: HabitEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allLocations by viewModel.allLocations.collectAsStateWithLifecycle()
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
            title = { Text("Notification preview") },
            text = { Text(previewText) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissPreviewDialog() }) { Text("Close") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (habitId != null) "Edit Habit" else "New Habit") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save() }) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::updateName,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Box(modifier = Modifier
                .fillMaxWidth()
                .clip(OutlinedTextFieldDefaults.shape)
                .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = flashAlpha.value))
            ) {
                OutlinedTextField(
                    value = uiState.fullDescription,
                    onValueChange = viewModel::updateFullDescription,
                    label = { Text("Full description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
            Box(modifier = Modifier
                .fillMaxWidth()
                .clip(OutlinedTextFieldDefaults.shape)
                .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = flashAlpha.value))
            ) {
                OutlinedTextField(
                    value = uiState.lowFloorDescription,
                    onValueChange = viewModel::updateLowFloorDescription,
                    label = { Text("Low-floor description (minimum viable)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.autofillWithAi() },
                    enabled = uiState.name.length >= 2 && !uiState.isGeneratingFields,
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.isGeneratingFields) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Generating\u2026")
                    } else {
                        Text("Autofill with AI")
                    }
                }
                OutlinedButton(
                    onClick = { viewModel.previewNotification() },
                    enabled = uiState.name.isNotBlank() &&
                              uiState.fullDescription.isNotBlank() &&
                              uiState.lowFloorDescription.isNotBlank() &&
                              !uiState.isGeneratingFields,
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.isGeneratingFields) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                    }
                    Text("Preview notification")
                }
            }

            val selectedIds = uiState.selectedLocationIds

            Text("Location")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedIds.isEmpty(),
                    onClick = { viewModel.setAnywhere() },
                    label = { Text("Anywhere") }
                )
                allLocations.forEach { loc ->
                    FilterChip(
                        selected = loc.id in selectedIds,
                        onClick = { viewModel.toggleLocation(loc.id) },
                        label = { Text(loc.name) }
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Active")
                Switch(
                    checked = uiState.active,
                    onCheckedChange = viewModel::updateActive
                )
            }
        }
    }
}
