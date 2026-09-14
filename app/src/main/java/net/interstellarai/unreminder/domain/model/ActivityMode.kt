package net.interstellarai.unreminder.domain.model

import java.time.Duration

/** What the user is doing at fire time, as far as choosing a habit is concerned. */
enum class ActivityMode { WALKING, SITTING, TRANSPORT }

/**
 * The activity resolved at fire time. Cycling is deliberately not an [ActivityMode]: it
 * suppresses the notification outright rather than narrowing which habits can be offered.
 */
sealed interface ActivityState {
    /** Lowercase, as both the debug readout and the transition breadcrumb print it. */
    val label: String

    data class Mode(val mode: ActivityMode) : ActivityState {
        override val label: String get() = mode.name.lowercase()
    }

    data object Cycling : ActivityState {
        override val label: String get() = "cycling"
    }
}

/** What a resolved [ActivityState] rests on. */
enum class ActivityBasis {
    /** A trusted observation of the state itself. */
    OBSERVED,
    /** The sitting fallback: nothing observed, or an observation too old or too vague to trust. */
    ASSUMED,
    /** The sitting fallback because ACTIVITY_RECOGNITION is denied, so nothing can be observed. */
    PERMISSION_DENIED,
}

/**
 * What a fire-time read resolved, and how old the observation behind it was. An absent or
 * stale observation resolves to [ActivityMode.SITTING]; [observationAge] still reports the
 * stale one's age, so a debug readout can tell a real STILL from the fallback. Null means
 * there was nothing to read at all. [basis] defaults to what the age alone implies; the
 * resolver overrides it where a non-null age is still a fallback.
 */
data class ActivityResolution(
    val state: ActivityState,
    val observationAge: Duration?,
    val basis: ActivityBasis = if (observationAge == null) ActivityBasis.ASSUMED else ActivityBasis.OBSERVED,
)
