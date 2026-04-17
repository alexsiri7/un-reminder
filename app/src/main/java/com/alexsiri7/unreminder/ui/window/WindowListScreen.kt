package com.alexsiri7.unreminder.ui.window

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WindowListScreen(
    onAddWindow: () -> Unit,
    onEditWindow: (Long) -> Unit,
    viewModel: WindowListViewModel = hiltViewModel()
) {
    val windows by viewModel.windows.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Windows") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddWindow) {
                Icon(Icons.Default.Add, contentDescription = "Add window")
            }
        }
    ) { padding ->
        if (windows.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No windows yet", style = MaterialTheme.typography.bodyLarge)
                Text("Tap + to add one", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(windows, key = { it.id }) { window ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditWindow(window.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${window.startTime} - ${window.endTime}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "${window.frequencyPerDay}x per day",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    DayOfWeek.entries.forEach { day ->
                                        val bit = 1 shl (day.value - 1)
                                        if (window.daysOfWeekBitmask and bit != 0) {
                                            SuggestionChip(
                                                onClick = {},
                                                label = {
                                                    Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            Switch(
                                checked = window.active,
                                onCheckedChange = { viewModel.toggleActive(window) }
                            )
                            IconButton(onClick = { viewModel.delete(window) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}
