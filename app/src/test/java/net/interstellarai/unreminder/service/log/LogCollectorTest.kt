package net.interstellarai.unreminder.service.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test fun `anonymizeLogs replaces IPv6 loopback address`() {
        val result = anonymizeLogs("connected via ::1 locally")
        assertTrue(result.contains("[IP]"))
        assertFalse(result.contains("::1"))
    }

    @Test fun `anonymizeLogs replaces compressed IPv6 addresses`() {
        val result = anonymizeLogs("connected to 2001:db8::1 via link")
        assertTrue(result.contains("[IP]"))
        assertFalse(result.contains("2001:db8::1"))
    }

    @Test fun `anonymizeLogs replaces link-local IPv6 addresses`() {
        val result = anonymizeLogs("link-local fe80::1 assigned")
        assertTrue(result.contains("[IP]"))
        assertFalse(result.contains("fe80::1"))
    }

    @Test fun `anonymizeLogs does not replace logcat timestamps`() {
        val input = "04-27 12:34:56.789 1234 5678 D TAG: message"
        val result = anonymizeLogs(input)
        assertTrue(result.contains("12:34:56.789"))
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

    @Test fun `processLogOutput returns null for blank input`() {
        assertNull(processLogOutput("   \n  "))
    }

    @Test fun `processLogOutput returns null for empty string`() {
        assertNull(processLogOutput(""))
    }

    @Test fun `processLogOutput anonymizes output before returning`() {
        val result = processLogOutput("error at 10.0.0.1 user@example.com")
        assertNotNull(result)
        assertFalse(result!!.contains("10.0.0.1"))
        assertFalse(result.contains("user@example.com"))
        assertTrue(result.contains("[IP]"))
        assertTrue(result.contains("[EMAIL]"))
    }

    @Test fun `processLogOutput trims whitespace from output`() {
        val result = processLogOutput("  D/Tag: message  \n")
        assertNotNull(result)
        assertEquals("D/Tag: message", result)
    }
}
