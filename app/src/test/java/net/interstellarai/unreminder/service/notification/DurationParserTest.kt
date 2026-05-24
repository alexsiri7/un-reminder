package net.interstellarai.unreminder.service.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DurationParserTest {

    @Test
    fun `meditate for 3 minutes returns 180`() {
        assertEquals(180, DurationParser.parseTotalSeconds("meditate for 3 minutes"))
    }

    @Test
    fun `hold for 30 seconds returns 30`() {
        assertEquals(30, DurationParser.parseTotalSeconds("hold for 30 seconds"))
    }

    @Test
    fun `breathe for 2 min returns 120`() {
        assertEquals(120, DurationParser.parseTotalSeconds("breathe for 2 min"))
    }

    @Test
    fun `run for 1 hour returns 3600`() {
        assertEquals(3600, DurationParser.parseTotalSeconds("run for 1 hour"))
    }

    @Test
    fun `3m returns 180`() {
        assertEquals(180, DurationParser.parseTotalSeconds("3m"))
    }

    @Test
    fun `90s returns 90`() {
        assertEquals(90, DurationParser.parseTotalSeconds("90s"))
    }

    @Test
    fun `1 hr returns 3600`() {
        assertEquals(3600, DurationParser.parseTotalSeconds("1 hr"))
    }

    @Test
    fun `case insensitive 3 MINUTES returns 180`() {
        assertEquals(180, DurationParser.parseTotalSeconds("3 MINUTES"))
    }

    @Test
    fun `no duration do 5 pushups returns null`() {
        assertNull(DurationParser.parseTotalSeconds("do 5 pushups"))
    }

    @Test
    fun `zero amount for 0 minutes returns null`() {
        assertNull(DurationParser.parseTotalSeconds("for 0 minutes"))
    }

    @Test
    fun `first match wins 5 min then 30 sec returns 300`() {
        assertEquals(300, DurationParser.parseTotalSeconds("5 min then 30 sec"))
    }
}
