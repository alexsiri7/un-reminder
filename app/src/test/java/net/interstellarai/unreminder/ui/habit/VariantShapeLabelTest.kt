package net.interstellarai.unreminder.ui.habit

import net.interstellarai.unreminder.data.db.VariationEntity
import net.interstellarai.unreminder.domain.model.VariantShape
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class VariantShapeLabelTest {

    @Test
    fun `shape label is the lowercase enum name`() {
        assertEquals("question", shapeLabel(VariantShape.QUESTION))
        assertEquals("timeboxed", shapeLabel(VariantShape.TIMEBOXED))
    }

    @Test
    fun `pre-shape rows read as unshaped`() {
        assertEquals("unshaped", shapeLabel(null))
    }

    @Test
    fun `breakdown counts in enum order with unshaped last and zero counts omitted`() {
        val unused = listOf(
            variation(null),
            variation(VariantShape.TERSE),
            variation(VariantShape.QUESTION),
            variation(VariantShape.TERSE),
            variation(null),
        )

        assertEquals("1 question · 2 terse · 2 unshaped", shapeBreakdown(unused))
    }

    private fun variation(shape: VariantShape?) = VariationEntity(
        habitId = 1L,
        text = "text",
        promptFingerprint = "fp",
        generatedAt = Instant.EPOCH,
        shape = shape,
    )
}
