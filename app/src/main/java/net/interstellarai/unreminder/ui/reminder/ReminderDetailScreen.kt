package net.interstellarai.unreminder.ui.reminder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.interstellarai.unreminder.ui.habit.DedicationProgressBar
import net.interstellarai.unreminder.ui.theme.ActionChip
import net.interstellarai.unreminder.ui.theme.Dimens
import net.interstellarai.unreminder.ui.theme.DisplaySmall
import net.interstellarai.unreminder.ui.theme.MonoLabel
import net.interstellarai.unreminder.ui.theme.MonoSectionLabel
import net.interstellarai.unreminder.ui.theme.NavPill
import net.interstellarai.unreminder.ui.theme.SansBody

@Composable
fun ReminderDetailScreen(
    triggerId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ReminderDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(triggerId) { viewModel.init(triggerId) }
    LaunchedEffect(uiState.isDone) { if (uiState.isDone) onNavigateBack() }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.xxl, vertical = Dimens.xl),
    ) {
        Text(
            "\u2190 back",
            style = MonoLabel,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.clickable(onClick = onNavigateBack),
        )

        Spacer(Modifier.height(Dimens.lg))

        MonoSectionLabel("reminder")
        HorizontalDivider(
            thickness = Dimens.hairline,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
        )
        Spacer(Modifier.height(Dimens.sm))
        Text(
            uiState.promptText,
            style = SansBody,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(Dimens.xl))

        if (uiState.habitName.isNotBlank()) {
            MonoSectionLabel("habit")
            HorizontalDivider(
                thickness = Dimens.hairline,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
            )
            Spacer(Modifier.height(Dimens.sm))
            Text(
                uiState.habitName,
                style = DisplaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(Dimens.md))
            DedicationProgressBar(
                level = uiState.dedicationLevel,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Dimens.xl))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.md, Alignment.CenterHorizontally),
        ) {
            ActionChip(label = "Did it", filled = true, onClick = { viewModel.markCompleted() })
            ActionChip(label = "Dismiss", filled = false, onClick = { viewModel.markDismissed() })
        }

        Spacer(Modifier.height(Dimens.lg))
        NavPill()
    }
}
