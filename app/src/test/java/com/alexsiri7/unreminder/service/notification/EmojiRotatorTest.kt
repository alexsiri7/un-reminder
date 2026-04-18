package com.alexsiri7.unreminder.service.notification

import org.junit.Assert.assertEquals
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
    fun `negative triggerId maps to correct slot via mod`() {
        // mod(-1, 20) == 19 → same slot as triggerId 19
        assertEquals(rotator.pick(19L), rotator.pick(-1L))
    }
}
