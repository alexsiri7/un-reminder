package net.interstellarai.unreminder.widget

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

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

    // The look is derived from a persisted id, so the seed-to-layout mapping is part of the
    // contract: reordering the enum would redraw every variant already seen.
    @Test
    fun `seeds 0 to 4 map to the five layouts in order`() {
        assertEquals(WidgetLayout.SPRITE_LEFT, WidgetLayout.forSeed(0))
        assertEquals(WidgetLayout.SPRITE_RIGHT, WidgetLayout.forSeed(1))
        assertEquals(WidgetLayout.SPRITE_LARGE, WidgetLayout.forSeed(2))
        assertEquals(WidgetLayout.TYPOGRAPHIC, WidgetLayout.forSeed(3))
        assertEquals(WidgetLayout.COMPACT, WidgetLayout.forSeed(4))
        assertEquals(WidgetLayout.SPRITE_LEFT, WidgetLayout.forSeed(5))
    }
}
