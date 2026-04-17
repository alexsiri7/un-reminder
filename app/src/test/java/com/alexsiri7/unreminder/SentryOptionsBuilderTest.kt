package com.alexsiri7.unreminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SentryOptionsBuilderTest {

    @Test
    fun `release string is formatted correctly`() {
        val opts = buildSentryOptions("https://key@sentry.io/1", false, "com.example", "1.2.3", 42)
        assertEquals("com.example@1.2.3+42", opts.release)
    }

    @Test
    fun `PII options are off`() {
        val opts = buildSentryOptions("https://key@sentry.io/1", false, "com.example", "1.0", 1)
        assertFalse(opts.isSendDefaultPii)
        assertFalse(opts.isAttachScreenshot)
        assertFalse(opts.isAttachViewHierarchy)
    }

    @Test
    fun `traces sample rate is zero`() {
        val opts = buildSentryOptions("https://key@sentry.io/1", false, "com.example", "1.0", 1)
        assertEquals(0.0, opts.tracesSampleRate!!, 0.0)
    }

    @Test
    fun `environment is release when not debug`() {
        val opts = buildSentryOptions("https://key@sentry.io/1", false, "com.example", "1.0", 1)
        assertEquals("release", opts.environment)
    }

    @Test
    fun `environment is debug when debug flag set`() {
        val opts = buildSentryOptions("https://key@sentry.io/1", true, "com.example", "1.0", 1)
        assertEquals("debug", opts.environment)
    }

    @Test
    fun `dsn is set correctly`() {
        val dsn = "https://publickey@o123.ingest.sentry.io/456"
        val opts = buildSentryOptions(dsn, false, "com.example", "1.0", 1)
        assertEquals(dsn, opts.dsn)
    }
}
