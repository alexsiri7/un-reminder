package net.interstellarai.unreminder.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VariantTreatmentTest {

    @Test
    fun `seed is the variation id when there is one`() {
        assertEquals(11L, VariantTreatment.seed(variationId = 11L, habitId = 7L))
    }

    @Test
    fun `seed falls back to the habit id for a fallback row`() {
        assertEquals(7L, VariantTreatment.seed(variationId = null, habitId = 7L))
    }

    @Test
    fun `pick is non-negative for a negative seed`() {
        val entries = listOf("a", "b", "c", "d", "e")
        for (seed in -10L..-1L) assertTrue(VariantTreatment.pick(entries, seed) in entries)
    }

    // Pool ids are sequential, so this is what "consecutively generated variants get different
    // looks" can honestly promise with five entries.
    @Test
    fun `five consecutive seeds pick five different entries of a five-entry list`() {
        val entries = listOf("a", "b", "c", "d", "e")
        for (base in 0L until 5L) {
            assertEquals("from $base", 5, (base until base + 5).map { VariantTreatment.pick(entries, it) }.toSet().size)
        }
    }

    // The same seed reads differently on a four-entry and a five-entry surface; each surface is
    // only promised to agree with itself.
    @Test
    fun `pick is deterministic for a given seed and list size`() {
        val four = listOf("a", "b", "c", "d")
        val five = listOf("a", "b", "c", "d", "e")
        for (seed in 0L until 40L) {
            assertEquals(VariantTreatment.pick(four, seed), VariantTreatment.pick(four, seed))
            assertEquals(VariantTreatment.pick(five, seed), VariantTreatment.pick(five, seed))
        }
    }
}
