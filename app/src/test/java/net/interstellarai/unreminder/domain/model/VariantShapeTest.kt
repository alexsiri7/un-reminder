package net.interstellarai.unreminder.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VariantShapeTest {

    // The worker owns the canonical list as VARIANT_SHAPES (worker/src/types.ts), pinned by
    // worker/src/types.test.ts. RequestyProxyClient rejects a batch carrying a shape this enum
    // lacks, and the app ships over days while the worker deploys instantly, so both pins must
    // change in the same PR.
    @Test
    fun `VariantShape is pinned to the six shapes the worker declares`() {
        val expected = listOf("QUESTION", "STATEMENT", "CHALLENGE", "OBSERVATION", "TERSE", "TIMEBOXED")
        assertEquals(expected, VariantShape.entries.map { it.name })
    }
}
