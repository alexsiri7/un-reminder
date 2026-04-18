package com.alexsiri7.unreminder.service.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiRotatorTest {

    private val rotator = EmojiRotator()

    @Test
    fun `same triggerId always returns same emoji`() {
        val first = rotator.pick(42L)
        val second = rotator.pick(42L)
        assertEquals(first, second)
    }

    @Test
    fun `pick covers all 20 slots`() {
        val emojis = (0L..19L).map { rotator.pick(it) }.toSet()
        assertEquals(20, emojis.size)
    }

    @Test
    fun `triggerId 20 wraps to same as 0`() {
        assertEquals(rotator.pick(0L), rotator.pick(20L))
    }

    @Test
    fun `negative triggerId does not throw`() {
        val result = rotator.pick(-1L)
        assertTrue(result.isNotEmpty())
    }
}
