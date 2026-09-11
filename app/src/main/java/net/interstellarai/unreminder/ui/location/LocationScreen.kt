package net.interstellarai.unreminder.ui.location

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.interstellarai.unreminder.service.geofence.LocationCheck
import net.interstellarai.unreminder.service.geofence.LocationSetChangeCause
import net.interstellarai.unreminder.ui.theme.Dimens
import net.interstellarai.unreminder.ui.theme.MonoLabelTiny
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LocationScreen(
    onNavigateBack: () -> Unit,
    onAddLocation: () -> Unit,
    onEditLocation: (String) -> Unit,
    viewModel: LocationViewModel = hiltViewModel()
) {
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val recalculation by viewModel.recalculation.collectAsStateWithLifecycle()
    LocationContent(
        locations = locations,
        recalculation = recalculation,
        onRecalculate = viewModel::recalculate,
        onNavigateBack = onNavigateBack,
        onAddLocation = onAddLocation,
        onEditLocation = onEditLocation,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocationContent(
    locations: List<LocationRow>,
    recalculation: RecalculationState,
    onRecalculate: () -> Unit,
    onNavigateBack: () -> Unit,
    onAddLocation: () -> Unit,
    onEditLocation: (String) -> Unit,
) {
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
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddLocation) {
                Icon(Icons.Default.Add, contentDescription = "Add location")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Tap + to add a location. The app will trigger habits when you arrive.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            item {
                RecalculateControl(state = recalculation, onRecalculate = onRecalculate)
            }
            items(locations) { row ->
                LocationCard(row = row, onEditLocation = onEditLocation)
            }
            if (locations.isEmpty()) {
                item {
                    Text(
                        "No locations saved yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RecalculateControl(state: RecalculationState, onRecalculate: () -> Unit) {
    val running = state == RecalculationState.Running
    Column {
        OutlinedButton(onClick = onRecalculate, enabled = !running) {
            if (running) {
                CircularProgressIndicator(modifier = Modifier.size(Dimens.lg), strokeWidth = 2.dp)
                Spacer(Modifier.width(Dimens.sm))
                Text("Checking…")
            } else {
                Text("Recalculate now")
            }
        }
        if (state is RecalculationState.Failed) {
            Spacer(Modifier.height(Dimens.sm))
            Text(
                state.label,
                style = MonoLabelTiny,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                state.advice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LocationCard(row: LocationRow, onEditLocation: (String) -> Unit) {
    val loc = row.location
    val inside = row.presence == LocationPresence.INSIDE
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (inside) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        border = when (row.presence) {
            LocationPresence.INSIDE -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            LocationPresence.UNKNOWN -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            LocationPresence.OUTSIDE -> null
        },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(loc.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(Dimens.sm))
                    Text(
                        "· ${presenceLabel(row.presence)}",
                        style = MonoLabelTiny,
                        color = if (inside) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Text(
                    "%.4f, %.4f — radius ${loc.radiusM.toInt()} m".format(loc.lat, loc.lng),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    checkedLabel(row.check),
                    style = MonoLabelTiny,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onEditLocation(loc.name) }) {
                Icon(Icons.Default.Edit, contentDescription = "Edit ${loc.name}")
            }
        }
    }
}

private val ClockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun presenceLabel(presence: LocationPresence) = when (presence) {
    LocationPresence.INSIDE -> "inside"
    LocationPresence.OUTSIDE -> "outside"
    LocationPresence.UNKNOWN -> "unknown"
}

private fun checkedLabel(check: LocationCheck?): String {
    if (check == null) return "never checked"
    val via = when (check.via) {
        LocationSetChangeCause.ENTER -> "arrival"
        LocationSetChangeCause.EXIT -> "departure"
        LocationSetChangeCause.RECONCILE -> "location check"
        LocationSetChangeCause.RESTORE, LocationSetChangeCause.MANUAL -> "recorded"
    }
    return "checked ${check.at.atZone(ZoneId.systemDefault()).format(ClockFormat)} · $via"
}
