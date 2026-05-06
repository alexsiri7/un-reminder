package net.interstellarai.unreminder.data.db

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class WindowEntityLabelTest {

    private val baseWindow = WindowEntity(
        startTime = LocalTime.of(9, 0),
        endTime = LocalTime.of(17, 0),
        daysOfWeekBitmask = 0b1111111,
    )

    @Test
    fun `label returns name when non-blank`() {
        assertEquals("morning", baseWindow.copy(name = "morning").label())
    }

    @Test
    fun `label returns formatted time range when name is blank`() {
        assertEquals("09:00–17:00", baseWindow.copy(name = "").label())
    }

    @Test
    fun `label returns formatted time range when name is whitespace only`() {
        assertEquals("09:00–17:00", baseWindow.copy(name = "   ").label())
    }
}
