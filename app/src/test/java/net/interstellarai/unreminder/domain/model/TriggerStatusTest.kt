package net.interstellarai.unreminder.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TriggerStatusTest {

    @Test
    fun `TriggerStatus has exactly 5 values`() {
        assertEquals(5, TriggerStatus.entries.size)
    }

    @Test
    fun `TriggerStatus values are correct`() {
        val expected = setOf("SCHEDULED", "FIRED", "COMPLETED_FULL", "COMPLETED_LOW_FLOOR", "DISMISSED")
        val actual = TriggerStatus.entries.map { it.name }.toSet()
        assertEquals(expected, actual)
    }
}
