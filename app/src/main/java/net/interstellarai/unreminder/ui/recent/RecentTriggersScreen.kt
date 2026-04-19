package net.interstellarai.unreminder.ui.recent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.ui.theme.CompletedFull
import net.interstellarai.unreminder.ui.theme.CompletedLowFloor
import net.interstellarai.unreminder.ui.theme.Dismissed
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentTriggersScreen(
    onNavigateToFeedback: () -> Unit = {},
    viewModel: RecentTriggersViewModel = hiltViewModel()
) {
    val triggers by viewModel.triggers.collectAsStateWithLifecycle()
    val formatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Recent Triggers") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToFeedback) {
                Icon(Icons.Default.BugReport, contentDescription = "Send Feedback")
            }
        }
    ) { padding ->
        if (triggers.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No triggers yet", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(triggers) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    item.habitName ?: "Unknown",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                val chipColor = when (item.trigger.status) {
                                    TriggerStatus.COMPLETED_FULL -> CompletedFull
                                    TriggerStatus.COMPLETED_LOW_FLOOR -> CompletedLowFloor
                                    TriggerStatus.DISMISSED -> Dismissed
                                    else -> MaterialTheme.colorScheme.outline
                                }
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(item.trigger.status.name) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = chipColor.copy(alpha = 0.2f),
                                        labelColor = chipColor
                                    )
                                )
                            }
                            if (item.trigger.generatedPrompt != null) {
                                Text(
                                    item.trigger.generatedPrompt,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Text(
                                item.trigger.scheduledAt
                                    .atZone(ZoneId.systemDefault())
                                    .format(formatter),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
