package net.interstellarai.unreminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests the guard logic in MainActivity.handleDetailIntent().
 * Uses a pure companion function that mirrors the intent-parsing conditional chain,
 * following the same pattern as NotificationActionMappingTest.
 */
class DetailIntentParserTest {

    /**
     * Mirrors the guard logic in MainActivity.handleDetailIntent().
     * Returns the trigger ID to navigate to, or null if the intent should be ignored.
     */
    private fun parseDetailIntent(hasFlag: Boolean, triggerId: Long): Long? {
        if (!hasFlag) return null
        if (triggerId == -1L) return null
        return triggerId
    }

    @Test
    fun `returns triggerId when flag is true and id is valid`() {
        assertEquals(42L, parseDetailIntent(hasFlag = true, triggerId = 42L))
    }

    @Test
    fun `returns null when EXTRA_OPEN_DETAIL flag is absent`() {
        assertNull(parseDetailIntent(hasFlag = false, triggerId = 42L))
    }

    @Test
    fun `returns null when triggerId is the sentinel value -1`() {
        assertNull(parseDetailIntent(hasFlag = true, triggerId = -1L))
    }

    @Test
    fun `returns null when both flag is false and id is sentinel`() {
        assertNull(parseDetailIntent(hasFlag = false, triggerId = -1L))
    }
}
