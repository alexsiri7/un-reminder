package net.interstellarai.unreminder.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.random.Random

class NotificationStyleTest {

    // #386 asked for five looks and allowed four with a documented reason. Sprite-right is
    // absent because the Android 12+ template has a single large-icon slot (top|end) and no
    // gravity API, so it would render identically to SPRITE; collapsed-only is absent because
    // bigContentViewRequired() forces an expanded view whenever actions exist, and Open/Later
    // always do; InboxStyle is absent because its single-line rows truncate the prompt.
    @Test
    fun `NotificationStyle is pinned to the four achievable styles`() {
        assertEquals(
            listOf("SPRITE", "BIG_PICTURE", "TEXT_ONLY", "ACCENT"),
            NotificationStyle.entries.map { it.name },
        )
    }

    @Test
    fun `next never repeats the previous style`() {
        for (previous in NotificationStyle.entries) {
            repeat(100) { assertNotEquals(previous, NotificationStyle.next(previous, Random(it))) }
        }
    }

    // A fixed successor order would pass the test above and still give every habit the same
    // predictable cycle.
    @Test
    fun `from any previous style next can land on each of the other three`() {
        for (previous in NotificationStyle.entries) {
            val seen = (0 until 200).map { NotificationStyle.next(previous, Random(it)) }.toSet()

            assertEquals(NotificationStyle.entries.toSet() - previous, seen)
        }
    }

    @Test
    fun `next with no previous reaches every style`() {
        val seen = (0 until 200).map { NotificationStyle.next(null, Random(it)) }.toSet()

        assertEquals(NotificationStyle.entries.toSet(), seen)
    }
}
