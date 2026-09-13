package net.interstellarai.unreminder.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityModeTest {

    // The worker re-declares this list as ACTIVITY_MODES (worker/src/types.ts), pinned by
    // worker/src/types.test.ts. A habit's supported modes are sent to generateBatch under these
    // names, and the worker silently discards a name it does not know — a habit scoped to a
    // renamed mode would generate for all modes, with no error on either side. The app ships
    // over days while the worker deploys instantly, so both pins must change in the same PR.
    @Test
    fun `ActivityMode is pinned to the three modes the worker declares`() {
        val expected = listOf("WALKING", "SITTING", "TRANSPORT")
        assertEquals(expected, ActivityMode.entries.map { it.name })
    }
}
