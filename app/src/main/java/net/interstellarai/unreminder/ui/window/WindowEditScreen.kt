package net.interstellarai.unreminder.ui.window

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.interstellarai.unreminder.ui.theme.Dimens
import net.interstellarai.unreminder.ui.theme.DisplayLarge
import net.interstellarai.unreminder.ui.theme.MonoSectionLabel
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WindowEditScreen(
    windowId: Long?,
    onNavigateBack: () -> Unit,
    viewModel: WindowEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(windowId) {
        if (windowId != null) viewModel.loadWindow(windowId)
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onNavigateBack()
    }

    val startTimeState = rememberTimePickerState(
        initialHour = uiState.startHour,
        initialMinute = uiState.startMinute
    )
    val endTimeState = rememberTimePickerState(
        initialHour = uiState.endHour,
        initialMinute = uiState.endMinute
    )

    LaunchedEffect(startTimeState.hour, startTimeState.minute) {
        viewModel.updateStartTime(startTimeState.hour, startTimeState.minute)
    }
    LaunchedEffect(endTimeState.hour, endTimeState.minute) {
        viewModel.updateEndTime(endTimeState.hour, endTimeState.minute)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (windowId != null) "Edit Window" else "New Window") },
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
            Column {
                MonoSectionLabel("window name")
                Spacer(Modifier.height(Dimens.sm))
                UnderlinedDisplayField(
                    value = uiState.name,
                    onValueChange = viewModel::updateName,
                    placeholder = "e.g. morning",
                )
            }

            Text("Start Time", style = MaterialTheme.typography.titleSmall)
            TimePicker(state = startTimeState)

            Text("End Time", style = MaterialTheme.typography.titleSmall)
            TimePicker(state = endTimeState)

            Text("Days of Week", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayOfWeek.entries.forEach { day ->
                    val bit = 1 shl (day.value - 1)
                    FilterChip(
                        selected = uiState.daysOfWeekBitmask and bit != 0,
                        onClick = { viewModel.toggleDay(bit) },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) }
                    )
                }
            }

            Text("Frequency: ${uiState.frequencyPerDay}x per day", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = uiState.frequencyPerDay.toFloat(),
                onValueChange = { viewModel.updateFrequency(it.roundToInt()) },
                valueRange = 1f..3f,
                steps = 1
            )

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
