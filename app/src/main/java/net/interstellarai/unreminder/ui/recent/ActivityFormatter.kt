package net.interstellarai.unreminder.ui.recent

import net.interstellarai.unreminder.domain.model.ActivityResolution
import java.time.Duration

/** Debug readout: the state a trigger would resolve now, and how old the observation behind it is. */
fun formatActivity(resolution: ActivityResolution): String {
    val age = resolution.observationAge ?: return "${resolution.state.label} · nothing observed"
    return "${resolution.state.label} · seen ${formatAge(age)} ago"
}

private fun formatAge(age: Duration): String = when {
    age < Duration.ofMinutes(1) -> "${age.seconds}s"
    age < Duration.ofHours(1) -> "${age.toMinutes()}m"
    else -> "${age.toHours()}h ${age.toMinutesPart()}m"
}
