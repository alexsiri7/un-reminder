package net.interstellarai.unreminder.service.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogCollectorTest {

    @Test fun `anonymizeLogs replaces email addresses`() {
        val result = anonymizeLogs("user john@example.com connected")
        assertTrue(result.contains("[EMAIL]"))
        assertFalse(result.contains("john@example.com"))
    }

    @Test fun `anonymizeLogs replaces IPv4 addresses`() {
        val result = anonymizeLogs("Connected to 192.168.1.100")
        assertTrue(result.contains("[IP]"))
        assertFalse(result.contains("192.168.1.100"))
    }

    @Test fun `anonymizeLogs leaves non-PII log lines unchanged`() {
        val input = "D/FeedbackViewModel: Direct submit succeeded"
        assertEquals(input, anonymizeLogs(input))
    }

    @Test fun `anonymizeLogs replaces multiple occurrences`() {
        val result = anonymizeLogs("src 10.0.0.1 dst 192.168.1.1")
        assertFalse(result.contains("10.0.0.1"))
        assertFalse(result.contains("192.168.1.1"))
        assertEquals("src [IP] dst [IP]", result)
    }

    @Test fun `anonymizeLogs handles empty string`() {
        assertEquals("", anonymizeLogs(""))
    }
}
