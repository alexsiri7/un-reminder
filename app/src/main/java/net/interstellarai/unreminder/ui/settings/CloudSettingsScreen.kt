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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
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
                Text(
                    uiState.tokenId?.let { "token: $it" } ?: "no token — ask Alex for one",
                    style = MonoLabel,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(Dimens.sm))
                OutlinedTextField(
                    value = uiState.tokenInput,
                    onValueChange = viewModel::setTokenInput,
                    label = { Text("paste token", style = MonoLabel) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = uiState.tokenInputError != null,
                    supportingText = uiState.tokenInputError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Dimens.sm))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent, UnReminderShapes.small)
                        .border(
                            1.5.dp,
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                            UnReminderShapes.small,
                        )
                        .clickable(enabled = uiState.tokenInput.isNotBlank()) { viewModel.saveToken() }
                        .padding(vertical = Dimens.md + 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "save token",
                        style = SansBody,
                        color = MaterialTheme.colorScheme.onBackground.copy(
                            alpha = if (uiState.tokenInput.isNotBlank()) 1f else 0.6f,
                        ),
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
                        .clickable(enabled = uiState.regeneration == null) { viewModel.regenerateAll() }
                        .padding(vertical = Dimens.md + 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val progress = uiState.regeneration
                    Text(
                        if (progress == null) "regenerate all variants" else regeneratingLabel(progress),
                        style = SansBody,
                        color = MaterialTheme.colorScheme.onBackground.copy(
                            alpha = if (progress == null) 1f else 0.6f,
                        ),
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

internal fun regeneratingLabel(progress: RegenerationProgress): String {
    val settled = progress.done + progress.failed
    val failed = if (progress.failed > 0) " (${progress.failed} failed)" else ""
    return "regenerating… $settled of ${progress.total}$failed"
}
