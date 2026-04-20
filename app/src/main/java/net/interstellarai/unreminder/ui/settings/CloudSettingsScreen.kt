package net.interstellarai.unreminder.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.interstellarai.unreminder.ui.theme.Dimens
import net.interstellarai.unreminder.ui.theme.DisplayHuge
import net.interstellarai.unreminder.ui.theme.MonoContextStrip
import net.interstellarai.unreminder.ui.theme.MonoLabel
import net.interstellarai.unreminder.ui.theme.SansBody
import net.interstellarai.unreminder.ui.theme.UnReminderShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: CloudSettingsViewModel = hiltViewModel(),
) {
    val workerUrl by viewModel.workerUrl.collectAsStateWithLifecycle()
    val workerSecret by viewModel.workerSecret.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        val msg = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearError()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = Dimens.xxl,
                    vertical = Dimens.xl,
                ),
            ) {
                MonoContextStrip("settings / cloud ai")
                Spacer(Modifier.height(Dimens.sm))
                Text(
                    "cloud ai",
                    style = DisplayHuge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = Dimens.xxl),
            ) {
                OutlinedTextField(
                    value = workerUrl,
                    onValueChange = { viewModel.setWorkerUrl(it) },
                    label = { Text("worker url", style = MonoLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Dimens.sm))
                OutlinedTextField(
                    value = workerSecret,
                    onValueChange = { viewModel.setWorkerSecret(it) },
                    label = { Text("worker secret", style = MonoLabel) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Dimens.xxl))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent, UnReminderShapes.small)
                        .border(
                            1.5.dp,
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                            UnReminderShapes.small,
                        )
                        .clickable(enabled = !uiState.isRegenerating) { viewModel.regenerateAll() }
                        .padding(vertical = Dimens.md + 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "regenerate all variants",
                        style = SansBody,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(Modifier.height(Dimens.xxl))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent, UnReminderShapes.small)
                        .border(
                            1.5.dp,
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                            UnReminderShapes.small,
                        )
                        .clickable(onClick = onNavigateBack)
                        .padding(vertical = Dimens.md + 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "back",
                        style = SansBody,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            Spacer(Modifier.height(Dimens.xxl))
        }
    }
}
