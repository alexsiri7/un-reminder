package net.interstellarai.unreminder.service.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelConfigTest {

    @Test
    fun `PLACEHOLDER_URL matches build gradle fallback`() {
        // Must stay in sync with the `?:` fallback in app/build.gradle.kts.
        assertEquals("https://placeholder.invalid/model.task", ModelConfig.PLACEHOLDER_URL)
    }

    @Test
    fun `isPlaceholderUrl returns true for placeholder`() {
        assertTrue(ModelConfig.isPlaceholderUrl("https://placeholder.invalid/model.task"))
    }

    @Test
    fun `isPlaceholderUrl returns true for null`() {
        assertTrue(ModelConfig.isPlaceholderUrl(null))
    }

    @Test
    fun `isPlaceholderUrl returns true for blank`() {
        assertTrue(ModelConfig.isPlaceholderUrl(""))
        assertTrue(ModelConfig.isPlaceholderUrl("   "))
    }

    @Test
    fun `isPlaceholderUrl returns false for a real URL`() {
        assertFalse(ModelConfig.isPlaceholderUrl("https://cdn.example.com/gemma3-1b-it-int4.task"))
    }
}
