package net.interstellarai.unreminder.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TriggerStatusTest {

    @Test
    fun `TriggerStatus has exactly 6 values`() {
        assertEquals(6, TriggerStatus.entries.size)
    }

    @Test
    fun `TriggerStatus values are correct`() {
        // COMPLETED is the new primary status (DB v6); COMPLETED_FULL / COMPLETED_LOW_FLOOR are legacy.
        val expected = setOf("SCHEDULED", "FIRED", "COMPLETED_FULL", "COMPLETED_LOW_FLOOR", "COMPLETED", "DISMISSED")
        val actual = TriggerStatus.entries.map { it.name }.toSet()
        assertEquals(expected, actual)
    }
}
