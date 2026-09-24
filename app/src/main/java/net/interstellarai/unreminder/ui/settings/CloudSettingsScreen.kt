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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.interstellarai.unreminder.data.repository.GenerationFailure
import net.interstellarai.unreminder.domain.model.SpendCapType
import net.interstellarai.unreminder.ui.theme.Dimens
import net.interstellarai.unreminder.ui.theme.DisplayHuge
import net.interstellarai.unreminder.ui.theme.MonoContextStrip
import net.interstellarai.unreminder.ui.theme.MonoLabel
import net.interstellarai.unreminder.ui.theme.SansBody
import net.interstellarai.unreminder.ui.theme.UnReminderShapes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
                    tokenStatus(uiState.tokenId, uiState.deviceLabel),
                    style = MonoLabel,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
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
                        .clickable(enabled = !uiState.registering) { viewModel.reregister() }
                        .padding(vertical = Dimens.md + 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (uiState.registering) "registering…" else "re-register",
                        style = SansBody,
                        color = MaterialTheme.colorScheme.onBackground.copy(
                            alpha = if (uiState.registering) 0.6f else 1f,
                        ),
                    )
                }
                uiState.lastFailure?.let { failure ->
                    Spacer(Modifier.height(Dimens.lg))
                    Text(
                        failureTimestamp(failure.at, ZoneId.systemDefault()),
                        style = MonoLabel,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(Dimens.xs))
                    Text(
                        failureMessage(failure),
                        style = SansBody,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(Modifier.height(Dimens.lg))
                var advancedOpen by rememberSaveable { mutableStateOf(false) }
                Text(
                    if (advancedOpen) "advanced ▾" else "advanced ▸",
                    style = MonoLabel,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { advancedOpen = !advancedOpen }
                        .padding(vertical = Dimens.xs),
                )
                if (advancedOpen) {
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

internal fun tokenStatus(tokenId: String?, deviceLabel: String?): String = when {
    tokenId == null -> "no token yet — tap re-register"
    deviceLabel != null -> "registered as $tokenId on $deviceLabel"
    else -> "token: $tokenId"
}

internal fun regeneratingLabel(progress: RegenerationProgress): String {
    val settled = progress.done + progress.failed
    val failed = if (progress.failed > 0) " (${progress.failed} failed)" else ""
    return "regenerating… $settled of ${progress.total}$failed"
}

internal fun failureMessage(failure: GenerationFailure): String = when (failure.kind) {
    GenerationFailure.Kind.TOKEN_REJECTED ->
        "token rejected — tap re-register, or paste a new one under advanced"
    GenerationFailure.Kind.SPEND_CAP_USER -> when (failure.capType) {
        SpendCapType.DAILY -> "your generation budget for today is spent — it resets tomorrow"
        SpendCapType.MONTHLY -> "your generation budget for this month is spent — it resets next month"
        null -> "your generation budget is spent — try again later"
    }
    GenerationFailure.Kind.SPEND_CAP_GLOBAL -> when (failure.capType) {
        SpendCapType.DAILY -> "the service's budget for today is spent, not yours — try tomorrow"
        SpendCapType.MONTHLY -> "the service's budget for this month is spent, not yours — try next month"
        null -> "the service's budget is spent, not yours — try again later"
    }
    GenerationFailure.Kind.SERVICE_UNAVAILABLE ->
        "the service could not be reached — the app will try again on its own"
    GenerationFailure.Kind.INTEGRITY_UNAVAILABLE ->
        "this device can't prove it's a Play install — install from Play, or paste a token under advanced"
    GenerationFailure.Kind.REGISTRATION_REJECTED ->
        "Play didn't verify this install — install from Play, or paste a token under advanced"
    GenerationFailure.Kind.REGISTRATION_CAP ->
        "too many new installs today — the app will try again later"
}

private val FAILURE_TIME = DateTimeFormatter.ofPattern("MMM d · HH:mm")

internal fun failureTimestamp(at: Instant, zone: ZoneId): String =
    "last generation failed " + at.atZone(zone).format(FAILURE_TIME)
