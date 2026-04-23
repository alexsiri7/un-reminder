package net.interstellarai.unreminder.data.db

import net.interstellarai.unreminder.domain.model.TriggerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `Instant round-trip`() {
        val now = Instant.ofEpochMilli(1700000000000)
        val millis = converters.fromInstant(now)
        val result = converters.toInstant(millis)
        assertEquals(now, result)
    }

    @Test
    fun `Instant null round-trip`() {
        assertNull(converters.fromInstant(null))
        assertNull(converters.toInstant(null))
    }

    @Test
    fun `LocalTime round-trip`() {
        val time = LocalTime.of(14, 30, 45)
        val seconds = converters.fromLocalTime(time)
        val result = converters.toLocalTime(seconds)
        assertEquals(time, result)
    }

    @Test
    fun `LocalTime null round-trip`() {
        assertNull(converters.fromLocalTime(null))
        assertNull(converters.toLocalTime(null))
    }

    @Test
    fun `TriggerStatus round-trip`() {
        for (status in TriggerStatus.entries) {
            val str = converters.fromTriggerStatus(status)
            val result = converters.toTriggerStatus(str)
            assertEquals(status, result)
        }
    }

    @Test
    fun `TriggerStatus null round-trip`() {
        assertNull(converters.fromTriggerStatus(null))
        assertNull(converters.toTriggerStatus(null))
    }

    @Test
    fun `LocalTime midnight round-trip`() {
        val midnight = LocalTime.MIDNIGHT
        val seconds = converters.fromLocalTime(midnight)
        assertEquals(0, seconds)
        val result = converters.toLocalTime(seconds)
        assertEquals(midnight, result)
    }

    @Test
    fun `LocalTime end of day round-trip`() {
        val endOfDay = LocalTime.of(23, 59, 59)
        val seconds = converters.fromLocalTime(endOfDay)
        val result = converters.toLocalTime(seconds)
        assertEquals(endOfDay, result)
    }

    @Test
    fun `StringList round-trip with 6-element list`() {
        val list = listOf("3 deep breaths", "", "", "20-min meditation", "", "")
        val json = converters.fromStringList(list)
        val result = converters.toStringList(json)
        assertEquals(list, result)
    }

    @Test
    fun `StringList null round-trip`() {
        assertNull(converters.fromStringList(null))
        assertNull(converters.toStringList(null))
    }

    @Test
    fun `StringList round-trip preserves special characters`() {
        val list = listOf("it's a \"habit\"", "café", "", "", "", "")
        val json = converters.fromStringList(list)
        val result = converters.toStringList(json)
        assertEquals(list, result)
    }
}
