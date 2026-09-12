package net.interstellarai.unreminder.data.db

import net.interstellarai.unreminder.domain.model.ActivityMode
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.domain.model.VariantShape
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
    fun `VariantShape round-trip`() {
        for (shape in VariantShape.entries) {
            assertEquals(shape, converters.toVariantShape(converters.fromVariantShape(shape)))
        }
    }

    @Test
    fun `VariantShape null round-trip`() {
        assertNull(converters.fromVariantShape(null))
        assertNull(converters.toVariantShape(null))
    }

    @Test
    fun `toVariantShape reads an unrecognized shape as null instead of throwing`() {
        assertNull(converters.toVariantShape("PLEA"))
        assertNull(converters.toVariantShape("question"))
        assertNull(converters.toVariantShape(""))
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
    fun `descriptionLadder round-trip`() {
        val ladder = listOf("3 deep breaths", "", "", "20-min session", "", "")
        val json = converters.fromDescriptionLadder(ladder)
        val result = converters.toDescriptionLadder(json)
        assertEquals(ladder, result)
    }

    @Test
    fun `descriptionLadder null round-trip`() {
        assertNull(converters.fromDescriptionLadder(null))
        assertNull(converters.toDescriptionLadder(null))
    }

    @Test
    fun `descriptionLadder preserves 6-element structure`() {
        val ladder = List(6) { "level $it" }
        val json = converters.fromDescriptionLadder(ladder)
        val result = converters.toDescriptionLadder(json)
        assertEquals(6, result?.size)
        assertEquals(ladder, result)
    }

    @Test
    fun `descriptionLadder with special characters round-trip`() {
        val ladder = listOf("Listen to a 5-minute \"body scan\" meditation", "", "", "20-min\\nsession", "", "")
        val json = converters.fromDescriptionLadder(ladder)
        val result = converters.toDescriptionLadder(json)
        assertEquals(ladder, result)
    }

    @Test
    fun `toDescriptionLadder malformed JSON returns blank ladder`() {
        val result = converters.toDescriptionLadder("not-valid-json")
        assertEquals(List(6) { "" }, result)
    }

    @Test
    fun `toTriggerStatus maps legacy COMPLETED_FULL to COMPLETED`() {
        assertEquals(TriggerStatus.COMPLETED, converters.toTriggerStatus("COMPLETED_FULL"))
    }

    @Test
    fun `toTriggerStatus maps legacy COMPLETED_LOW_FLOOR to COMPLETED`() {
        assertEquals(TriggerStatus.COMPLETED, converters.toTriggerStatus("COMPLETED_LOW_FLOOR"))
    }

    @Test
    fun `activity mode bits are persisted values and never renumbered`() {
        assertEquals(listOf(1, 2, 4), ActivityMode.entries.map { it.bit })
    }

    @Test
    fun `an empty mode set is stored as 0 meaning any mode`() {
        assertEquals(0, converters.fromActivityModes(emptySet()))
        assertEquals(emptySet<ActivityMode>(), converters.toActivityModes(0))
    }

    @Test
    fun `activity mode set round-trip`() {
        assertEquals(5, converters.fromActivityModes(setOf(ActivityMode.WALKING, ActivityMode.TRANSPORT)))
        assertEquals(setOf(ActivityMode.WALKING, ActivityMode.TRANSPORT), converters.toActivityModes(5))
        for (mode in ActivityMode.entries) {
            assertEquals(setOf(mode), converters.toActivityModes(converters.fromActivityModes(setOf(mode))))
        }
    }
}
