package net.interstellarai.unreminder.widget

import net.interstellarai.unreminder.data.db.WindowEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class WindowBoundariesTest {

    private val allDays = 0b1111111
    private val friday = LocalDateTime.of(2026, 9, 11, 12, 0)

    private fun window(start: LocalTime, end: LocalTime, days: Int = allDays, active: Boolean = true) =
        WindowEntity(startTime = start, endTime = end, daysOfWeekBitmask = days, active = active)

    @Test
    fun `before a window opens the boundary is its start`() {
        val windows = listOf(window(LocalTime.of(14, 0), LocalTime.of(16, 0)))

        assertEquals(friday.withHour(14), WindowBoundaries.next(windows, friday))
    }

    @Test
    fun `inside a window the boundary is the second after its end`() {
        val windows = listOf(window(LocalTime.of(9, 0), LocalTime.of(17, 0)))

        assertEquals(friday.withHour(17).plusSeconds(1), WindowBoundaries.next(windows, friday))
    }

    @Test
    fun `the earliest boundary across windows wins`() {
        val windows = listOf(
            window(LocalTime.of(9, 0), LocalTime.of(17, 0)),
            window(LocalTime.of(13, 30), LocalTime.of(14, 0)),
        )

        assertEquals(friday.withHour(13).withMinute(30), WindowBoundaries.next(windows, friday))
    }

    @Test
    fun `a window not scheduled today rolls over to its next day`() {
        val mondayOnly = 0b0000001
        val windows = listOf(window(LocalTime.of(8, 0), LocalTime.of(9, 0), days = mondayOnly))

        assertEquals(LocalDateTime.of(2026, 9, 14, 8, 0), WindowBoundaries.next(windows, friday))
    }

    @Test
    fun `a window already closed today comes back tomorrow`() {
        val windows = listOf(window(LocalTime.of(8, 0), LocalTime.of(9, 0)))

        assertEquals(friday.plusDays(1).withHour(8), WindowBoundaries.next(windows, friday))
    }

    @Test
    fun `inactive windows and empty lists yield no boundary`() {
        assertNull(WindowBoundaries.next(emptyList(), friday))
        assertNull(WindowBoundaries.next(listOf(window(LocalTime.of(14, 0), LocalTime.of(16, 0), active = false)), friday))
    }
}
