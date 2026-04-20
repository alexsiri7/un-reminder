package net.interstellarai.unreminder.ui.habit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.interstellarai.unreminder.ui.theme.Dimens

@Composable
fun DedicationProgressBar(
    currentLevel: Int,
    modifier: Modifier = Modifier,
    stopSize: Dp = 8.dp,
    onLevelTap: ((Int) -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(6) { level ->
            val filled = level <= currentLevel
            val color = if (filled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            }
            val tapMod = if (onLevelTap != null) {
                Modifier.clickable { onLevelTap(level) }
            } else {
                Modifier
            }
            Box(
                modifier = Modifier
                    .size(stopSize)
                    .clip(CircleShape)
                    .background(color)
                    .then(tapMod),
            )
        }
    }
}
