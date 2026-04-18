package com.alexsiri7.unreminder.service.sentry

import io.sentry.android.core.SentryAndroidOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentryOptionsBuilderTest {

    @Test
    fun `non-empty DSN - dsn field set correctly`() {
        val options = SentryAndroidOptions()
        applyOptions(options, dsn = "https://key@sentry.io/123", isDebug = false, appId = "com.example", versionName = "1.0.0", versionCode = 1)
        assertEquals("https://key@sentry.io/123", options.dsn)
    }

    @Test
    fun `debug flag true - environment is debug`() {
        val options = SentryAndroidOptions()
        applyOptions(options, dsn = "https://key@sentry.io/123", isDebug = true, appId = "com.example", versionName = "1.0.0", versionCode = 1)
        assertEquals("debug", options.environment)
    }

    @Test
    fun `debug flag false - environment is release`() {
        val options = SentryAndroidOptions()
        applyOptions(options, dsn = "https://key@sentry.io/123", isDebug = false, appId = "com.example", versionName = "1.0.0", versionCode = 1)
        assertEquals("release", options.environment)
    }

    @Test
    fun `release string uses appId versionName versionCode`() {
        val options = SentryAndroidOptions()
        applyOptions(options, dsn = "https://key@sentry.io/123", isDebug = false, appId = "com.alexsiri7.unreminder", versionName = "2.3.1", versionCode = 42)
        assertEquals("com.alexsiri7.unreminder@2.3.1+42", options.release)
    }

    @Test
    fun `traces sample rate is zero`() {
        val options = SentryAndroidOptions()
        applyOptions(options, dsn = "https://key@sentry.io/123", isDebug = false, appId = "com.example", versionName = "1.0.0", versionCode = 1)
        assertEquals(0.0, options.tracesSampleRate)
    }

    @Test
    fun `PII and attachments all disabled`() {
        val options = SentryAndroidOptions().apply {
            isSendDefaultPii = true
            isAttachScreenshot = true
            isAttachViewHierarchy = true
        }
        applyOptions(options, dsn = "https://key@sentry.io/123", isDebug = false, appId = "com.example", versionName = "1.0.0", versionCode = 1)
        assertFalse(options.isSendDefaultPii)
        assertFalse(options.isAttachScreenshot)
        assertFalse(options.isAttachViewHierarchy)
    }

    @Test
    fun `blank DSN - should not init sentry`() {
        assertFalse(shouldInitSentry(""))
    }

    @Test
    fun `whitespace-only DSN - should not init sentry`() {
        assertFalse(shouldInitSentry("   "))
    }

    @Test
    fun `non-blank DSN - should init sentry`() {
        assertTrue(shouldInitSentry("https://key@sentry.io/123"))
    }
}
