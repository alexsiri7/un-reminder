package net.interstellarai.unreminder.ui.reminder

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.interstellarai.unreminder.ui.habit.DedicationProgressBar
import net.interstellarai.unreminder.ui.theme.ActionChip
import net.interstellarai.unreminder.ui.theme.Dimens
import net.interstellarai.unreminder.ui.theme.MonoLabel
import net.interstellarai.unreminder.ui.theme.MonoSectionLabel
import net.interstellarai.unreminder.ui.theme.NavPill
import net.interstellarai.unreminder.ui.theme.SansBody
import net.interstellarai.unreminder.ui.theme.UnReminderShapes

// ─────────────────────────────────────────────────────────────────────────
// Reminder detail — the variant view: the mascot that led here, the variant
// as headline with its habit as label, and the one action. One of five
// layouts, keyed on the target so reopening never reshuffles; each owns
// the surface it draws on and the scale of its headline.
// ─────────────────────────────────────────────────────────────────────────

@Composable
fun ReminderDetailScreen(
    target: ReminderDetailTarget,
    onNavigateBack: () -> Unit,
    viewModel: ReminderDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(target) {
        when (target) {
            is ReminderDetailTarget.Trigger -> viewModel.init(target.triggerId)
            is ReminderDetailTarget.Variant -> viewModel.initVariant(target.habitId, target.variationId)
        }
    }
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

    ReminderDetailContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onComplete = viewModel::markCompleted,
        onWatch = { openVideo(context, it) },
    )
}

@Composable
internal fun ReminderDetailContent(
    uiState: ReminderDetailUiState,
    onNavigateBack: () -> Unit,
    onComplete: () -> Unit,
    onWatch: (String) -> Unit,
) {
    val layout = uiState.layout
    val dark = isSystemInDarkTheme()
    val surface = layout.palette.surface(dark)
    val ink = layout.palette.ink(dark)

    // One scheme override lets every shared component (chips, labels, the progress bar, the
    // pill) take the layout's palette unchanged. "Did it" becomes ink on surface inverted, so
    // it can never vanish into an accent surface.
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            background = surface,
            surface = surface,
            onBackground = ink,
            onSurface = ink,
            primary = ink,
            onPrimary = surface,
            surfaceVariant = ink.copy(alpha = 0.15f),
        ),
    ) {
        // Not a scroll: the body is weighted to fill whatever the text leaves, which is what
        // keeps the lower two-thirds occupied, and a scroll's unbounded height would collapse
        // it. Text is never truncated; the sprite yields first.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(surface)
                .padding(vertical = Dimens.xl),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.xxl),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "← back",
                    style = MonoLabel,
                    color = ink.copy(alpha = 0.7f),
                    modifier = Modifier.clickable(onClick = onNavigateBack),
                )
                if (uiState.promptText.isNotBlank() || uiState.habitName.isNotBlank()) {
                    // The Now page's own heading over a row opened from it, so nothing implies a
                    // notification fired.
                    MonoSectionLabel(if (uiState.target is ReminderDetailTarget.Variant) "doable now" else "reminder")
                }
            }

            Spacer(Modifier.height(Dimens.lg))

            when (layout) {
                ReminderDetailLayout.SPRITE_TOP -> SpriteTopBody(uiState, ink)
                ReminderDetailLayout.SPRITE_LEFT -> SpriteLeftBody(uiState, ink)
                ReminderDetailLayout.BACKDROP -> BackdropBody(uiState, ink, surface)
                ReminderDetailLayout.TEXT_DOMINANT -> TextDominantBody(uiState, ink)
                ReminderDetailLayout.SPRITE_BOTTOM -> SpriteBottomBody(uiState, ink)
            }

            Spacer(Modifier.height(Dimens.xl))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.xxl),
                horizontalArrangement = Arrangement.spacedBy(Dimens.md, Alignment.CenterHorizontally),
            ) {
                if (uiState.canComplete) {
                    ActionChip(label = "Did it", filled = true, onClick = onComplete)
                }
                uiState.videoUrl?.let { url ->
                    ActionChip(label = "Watch", filled = false, onClick = { onWatch(url) })
                }
            }

            Spacer(Modifier.height(Dimens.lg))
            NavPill()
        }
    }
}

@Composable
private fun ColumnScope.SpriteTopBody(uiState: ReminderDetailUiState, ink: Color) {
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = Dimens.xxl),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            uiState.spriteRes?.let { FittedSprite(it) }
        }
        Spacer(Modifier.height(Dimens.lg))
        TextBlock(uiState, uiState.layout.headline, ink)
    }
}

@Composable
private fun ColumnScope.SpriteLeftBody(uiState: ReminderDetailUiState, ink: Color) {
    Row(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = Dimens.xxl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            uiState.spriteRes?.let { FittedSprite(it) }
        }
        Spacer(Modifier.width(Dimens.lg))
        TextBlock(uiState, uiState.layout.headline, ink, modifier = Modifier.weight(3f))
    }
}

// Edge to edge, so the frame's horizontal padding goes on the overlaid text instead. The
// scrim reaches the full surface above where the text sits, so the headline reads on the
// surface, never on the mascot's face.
@Composable
private fun ColumnScope.BackdropBody(uiState: ReminderDetailUiState, ink: Color, surface: Color) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
    ) {
        uiState.spriteRes?.let {
            Image(
                painter = painterResource(it),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.2f to Color.Transparent, 0.8f to surface)),
        )
        TextBlock(
            uiState,
            uiState.layout.headline,
            ink,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(Dimens.xxl),
        )
    }
}

// The mascot is a tile in the corner and the text is centred in what is left below it, so
// the largest headline never runs into the sprite.
@Composable
private fun ColumnScope.TextDominantBody(uiState: ReminderDetailUiState, ink: Color) {
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = Dimens.xxl),
        horizontalAlignment = Alignment.End,
    ) {
        uiState.spriteRes?.let {
            Image(
                painter = painterResource(it),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(Dimens.spriteTile)
                    .clip(UnReminderShapes.large),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.CenterStart,
        ) {
            TextBlock(uiState, uiState.layout.headline, ink)
        }
    }
}

@Composable
private fun ColumnScope.SpriteBottomBody(uiState: ReminderDetailUiState, ink: Color) {
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = Dimens.xxl),
    ) {
        TextBlock(uiState, uiState.layout.headline, ink)
        Spacer(Modifier.height(Dimens.lg))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            uiState.spriteRes?.let { FittedSprite(it) }
        }
    }
}

// Sized to the sprite's own proportions rather than the box's, so the rounded clip follows
// the image edge instead of a letterboxed frame.
@Composable
private fun FittedSprite(@DrawableRes spriteRes: Int) {
    val painter = painterResource(spriteRes)
    val ratio = painter.intrinsicSize
        .takeIf { it.isSpecified && it.height > 0f }
        ?.let { it.width / it.height }
        ?: 1f
    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .aspectRatio(ratio)
            .clip(UnReminderShapes.large),
    )
}

// The variant is the headline and the habit name its label; without a variant the name takes
// the headline's place, as on the Now row.
@Composable
private fun TextBlock(
    uiState: ReminderDetailUiState,
    headline: TextStyle,
    ink: Color,
    modifier: Modifier = Modifier,
) {
    val promoted = uiState.promptText.isBlank()
    Column(modifier = modifier) {
        if (!promoted && uiState.habitName.isNotBlank()) {
            Text(
                uiState.habitName,
                style = SansBody,
                color = ink.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(Dimens.xs))
        }
        val headlineText = uiState.promptText.ifBlank { uiState.habitName }
        if (headlineText.isNotBlank()) {
            Text(
                headlineText,
                style = headline,
                color = ink,
            )
        }
        if (uiState.habitName.isNotBlank()) {
            Spacer(Modifier.height(Dimens.md))
            DedicationProgressBar(
                level = uiState.dedicationLevel,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun openVideo(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Log.w("ReminderDetailScreen", "No activity can open $url", e)
    }
}
