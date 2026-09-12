package net.interstellarai.unreminder.ui.recent

import net.interstellarai.unreminder.domain.model.ActivityMode
import net.interstellarai.unreminder.domain.model.ActivityResolution
import net.interstellarai.unreminder.domain.model.ActivityState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class ActivityFormatterTest {

    @Test
    fun `no observation names the fallback and says so`() {
        assertEquals(
            "sitting · nothing observed",
            formatActivity(ActivityResolution(ActivityState.Mode(ActivityMode.SITTING), null)),
        )
    }

    @Test
    fun `a fresh observation prints its age in seconds`() {
        assertEquals(
            "walking · seen 42s ago",
            formatActivity(ActivityResolution(ActivityState.Mode(ActivityMode.WALKING), Duration.ofSeconds(42))),
        )
    }

    @Test
    fun `an observation minutes old prints whole minutes`() {
        assertEquals(
            "cycling · seen 3m ago",
            formatActivity(ActivityResolution(ActivityState.Cycling, Duration.ofSeconds(3 * 60 + 20))),
        )
    }

    @Test
    fun `a stale fallback still shows how old the observation is`() {
        assertEquals(
            "sitting · seen 2h 5m ago",
            formatActivity(ActivityResolution(ActivityState.Mode(ActivityMode.SITTING), Duration.ofMinutes(125))),
        )
    }
}
