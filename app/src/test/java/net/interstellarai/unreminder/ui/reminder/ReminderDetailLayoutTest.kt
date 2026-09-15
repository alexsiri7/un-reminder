package net.interstellarai.unreminder.ui.reminder

import androidx.compose.ui.graphics.Color
import net.interstellarai.unreminder.ui.reminder.ReminderDetailLayout.Companion.forTarget
import net.interstellarai.unreminder.ui.reminder.ReminderDetailTarget.Trigger
import net.interstellarai.unreminder.ui.reminder.ReminderDetailTarget.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ReminderDetailLayoutTest {

    // WCAG 2 relative luminance over sRGB; ratio is (lighter + 0.05) / (darker + 0.05).
    private fun contrast(a: Color, b: Color): Double {
        fun channel(c: Float): Double = if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
        val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    // "Did it" is the pairing inverted (ink on surface becomes surface on ink), so this one
    // bound also keeps the button from vanishing into its screen.
    @Test
    fun `every layout's ink reads on its surface in both themes`() {
        for (layout in ReminderDetailLayout.entries) {
            val day = contrast(layout.palette.surfaceDay, layout.palette.inkDay)
            val night = contrast(layout.palette.surfaceNight, layout.palette.inkNight)
            assertTrue("$layout day contrast $day", day >= 4.5)
            assertTrue("$layout night contrast $night", night >= 4.5)
        }
    }

    @Test
    fun `five layouts give five different surfaces in each theme`() {
        assertEquals(5, ReminderDetailLayout.entries.map { it.palette.surfaceDay }.toSet().size)
        assertEquals(5, ReminderDetailLayout.entries.map { it.palette.surfaceNight }.toSet().size)
    }

    @Test
    fun `a variant target is keyed on its variation id`() {
        assertEquals(forTarget(Variant(7, 11)), forTarget(Variant(99, 11)))
        assertNotEquals(forTarget(Variant(7, 11)), forTarget(Variant(7, 12)))
    }

    @Test
    fun `a fallback row is keyed on its habit id`() {
        assertEquals(ReminderDetailLayout.entries[7 % 5], forTarget(Variant(7, null)))
        assertEquals(forTarget(Variant(7, null)), forTarget(Variant(7, null)))
    }

    @Test
    fun `a trigger target is keyed on its trigger id`() {
        assertEquals(ReminderDetailLayout.entries[42 % 5], forTarget(Trigger(42)))
        assertEquals(forTarget(Trigger(42)), forTarget(Trigger(42)))
    }

    // The look is persisted by id, so the id-to-layout mapping is part of the contract: reordering
    // the enum would reshuffle every already-seen variant and trigger.
    @Test
    fun `known ids are pinned to named layouts`() {
        assertEquals(ReminderDetailLayout.SPRITE_TOP, forTarget(Trigger(0)))
        assertEquals(ReminderDetailLayout.SPRITE_LEFT, forTarget(Trigger(1)))
        assertEquals(ReminderDetailLayout.BACKDROP, forTarget(Trigger(2)))
        assertEquals(ReminderDetailLayout.TEXT_DOMINANT, forTarget(Trigger(3)))
        assertEquals(ReminderDetailLayout.SPRITE_BOTTOM, forTarget(Trigger(4)))
    }

    @Test
    fun `a negative id still resolves`() {
        assertTrue(forTarget(Trigger(-1)) in ReminderDetailLayout.entries)
        assertTrue(forTarget(Variant(-3, null)) in ReminderDetailLayout.entries)
    }

    // Pool ids are sequential, so this is what "different variants of the same habit open to
    // different layouts" can honestly promise with five buckets.
    @Test
    fun `five consecutive ids land on five different layouts`() {
        for (base in 0 until 5) {
            val layouts = (base until base + 5).map { forTarget(Variant(1, it.toLong())) }.toSet()
            assertEquals("from $base", 5, layouts.size)
        }
    }
}
