package net.interstellarai.unreminder.ui.habit

import net.interstellarai.unreminder.service.llm.AiStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HabitListScreenTest {

    @Test
    fun `banner shows nothing when AI is ready and no token was rejected`() {
        assertNull(aiBannerLabel(AiStatus.Ready, tokenRejected = false))
    }

    @Test
    fun `banner names a rejected token`() {
        assertEquals(
            "token rejected — check cloud settings",
            aiBannerLabel(AiStatus.Ready, tokenRejected = true),
        )
    }

    @Test
    fun `a rejected token outranks an empty pool`() {
        assertEquals(
            "token rejected — check cloud settings",
            aiBannerLabel(AiStatus.Empty, tokenRejected = true),
        )
    }

    @Test
    fun `no token at all outranks a stale rejection`() {
        assertEquals(
            "AI unavailable — check cloud settings",
            aiBannerLabel(AiStatus.Unavailable, tokenRejected = true),
        )
    }

    @Test
    fun `an empty pool keeps its label`() {
        assertEquals(
            "cloud pool empty — variants being generated",
            aiBannerLabel(AiStatus.Empty, tokenRejected = false),
        )
    }
}
