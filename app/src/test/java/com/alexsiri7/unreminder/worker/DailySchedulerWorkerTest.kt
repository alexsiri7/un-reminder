package com.alexsiri7.unreminder.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.DayOfWeek

class DailySchedulerWorkerTest {

    private fun todayBit(dayOfWeek: DayOfWeek): Int = 1 shl (dayOfWeek.value - 1)

    @Test
    fun `Monday is bit 0`() {
        assertEquals(1, todayBit(DayOfWeek.MONDAY))
    }

    @Test
    fun `Sunday is bit 6`() {
        assertEquals(64, todayBit(DayOfWeek.SUNDAY))
    }

    @Test
    fun `all days bitmask is 127`() {
        var mask = 0
        for (day in DayOfWeek.entries) {
            mask = mask or todayBit(day)
        }
        assertEquals(0b1111111, mask)
    }

    @Test
    fun `weekday-only mask excludes Saturday and Sunday`() {
        val weekdayMask = todayBit(DayOfWeek.MONDAY) or
            todayBit(DayOfWeek.TUESDAY) or
            todayBit(DayOfWeek.WEDNESDAY) or
            todayBit(DayOfWeek.THURSDAY) or
            todayBit(DayOfWeek.FRIDAY)
        assertEquals(0b0011111, weekdayMask)
        assertEquals(0, weekdayMask and todayBit(DayOfWeek.SATURDAY))
        assertEquals(0, weekdayMask and todayBit(DayOfWeek.SUNDAY))
    }

    @Test
    fun `random time generation stays within bounds`() {
        val startSeconds = 9 * 3600L  // 09:00
        val endSeconds = 17 * 3600L   // 17:00
        repeat(100) {
            val random = startSeconds + (Math.random() * (endSeconds - startSeconds)).toLong()
            assert(random >= startSeconds) { "Random time $random < start $startSeconds" }
            assert(random < endSeconds) { "Random time $random >= end $endSeconds" }
        }
    }

    @Test
    fun `each day has unique bit`() {
        val bits = DayOfWeek.entries.map { todayBit(it) }
        assertEquals(bits.size, bits.toSet().size)
    }
}
