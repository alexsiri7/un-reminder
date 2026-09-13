package net.interstellarai.unreminder.widget

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow
import kotlin.random.Random

class WidgetLayoutTest {

    // WCAG 2 relative luminance over sRGB; ratio is (lighter + 0.05) / (darker + 0.05).
    private fun contrast(a: Color, b: Color): Double {
        fun channel(c: Float): Double = if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
        val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    // The "did it" button is the pairing inverted (ink on surface becomes surface on ink), so
    // this one bound also keeps the button from vanishing into its card.
    @Test
    fun `every layout's ink reads on its surface in both themes`() {
        for (layout in WidgetLayout.entries) {
            val day = contrast(layout.palette.surfaceDay, layout.palette.inkDay)
            val night = contrast(layout.palette.surfaceNight, layout.palette.inkNight)
            assertTrue("$layout day contrast $day", day >= 4.5)
            assertTrue("$layout night contrast $night", night >= 4.5)
        }
    }

    @Test
    fun `five layouts give five different surfaces in each theme`() {
        assertEquals(5, WidgetLayout.entries.map { it.palette.surfaceDay }.toSet().size)
        assertEquals(5, WidgetLayout.entries.map { it.palette.surfaceNight }.toSet().size)
    }

    @Test
    fun `next never repeats the previous layout`() {
        for (previous in WidgetLayout.entries) {
            repeat(100) { assertNotEquals(previous, WidgetLayout.next(previous, Random(it))) }
        }
    }

    @Test
    fun `next reaches every layout`() {
        val seen = (0 until 200).map { WidgetLayout.next(null, Random(it)) }.toSet()

        assertEquals(WidgetLayout.entries.toSet(), seen)
    }

    // A placed widget always has a previous layout, so a fixed successor order would pass the
    // two tests above and still show every widget the same predictable cycle.
    @Test
    fun `from any previous layout next can land on each of the other four`() {
        for (previous in WidgetLayout.entries) {
            val seen = (0 until 200).map { WidgetLayout.next(previous, Random(it)) }.toSet()

            assertEquals("after $previous", WidgetLayout.entries.toSet() - previous, seen)
        }
    }

    @Test
    fun `an unknown name resolves to no layout`() {
        assertNull(WidgetLayout.fromName("SPRITE_TOP"))
        assertNull(WidgetLayout.fromName(null))
        assertEquals(WidgetLayout.COMPACT, WidgetLayout.fromName("COMPACT"))
    }
}
