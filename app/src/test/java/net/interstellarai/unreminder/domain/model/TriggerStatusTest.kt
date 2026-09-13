package net.interstellarai.unreminder.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TriggerStatusTest {

    @Test
    fun `TriggerStatus has exactly 7 values`() {
        assertEquals(7, TriggerStatus.entries.size)
    }

    @Test
    fun `TriggerStatus values are correct`() {
        val expected = setOf("SCHEDULED", "FIRED", "COMPLETED", "DISMISSED", "EXPIRED", "LATER", "OPENED")
        val actual = TriggerStatus.entries.map { it.name }.toSet()
        assertEquals(expected, actual)
    }
}
