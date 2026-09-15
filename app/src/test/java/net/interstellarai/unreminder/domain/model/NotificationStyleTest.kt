package net.interstellarai.unreminder.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

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

    // The look is derived from a persisted id, so the seed-to-style mapping is part of the
    // contract: reordering the enum would restyle every variant already seen.
    @Test
    fun `seeds 0 to 3 map to the four styles in order and 4 wraps to SPRITE`() {
        assertEquals(NotificationStyle.SPRITE, NotificationStyle.forSeed(0))
        assertEquals(NotificationStyle.BIG_PICTURE, NotificationStyle.forSeed(1))
        assertEquals(NotificationStyle.TEXT_ONLY, NotificationStyle.forSeed(2))
        assertEquals(NotificationStyle.ACCENT, NotificationStyle.forSeed(3))
        assertEquals(NotificationStyle.SPRITE, NotificationStyle.forSeed(4))
    }
}
