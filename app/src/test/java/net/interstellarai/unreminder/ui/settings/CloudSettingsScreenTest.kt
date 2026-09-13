package net.interstellarai.unreminder.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudSettingsScreenTest {

    @Test
    fun `regenerating label counts settled works against the total`() {
        assertEquals(
            "regenerating… 0 of 3",
            regeneratingLabel(RegenerationProgress(total = 3, done = 0, failed = 0)),
        )
        assertEquals(
            "regenerating… 2 of 3",
            regeneratingLabel(RegenerationProgress(total = 3, done = 2, failed = 0)),
        )
    }

    @Test
    fun `regenerating label appends the failed count only when something failed`() {
        assertEquals(
            "regenerating… 2 of 3 (1 failed)",
            regeneratingLabel(RegenerationProgress(total = 3, done = 1, failed = 1)),
        )
    }
}
