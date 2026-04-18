package com.alexsiri7.unreminder.service.sentry

import io.sentry.android.core.SentryAndroidOptions
import org.junit.Assert.*
import org.junit.Test

class SentryOptionsBuilderTest {

    private fun buildOptions(
        dsn: String = "https://key@sentry.io/123",
        isDebug: Boolean = false,
        appId: String = "com.alexsiri7.unreminder",
        versionName: String = "1.0.0",
        versionCode: Int = 1
    ): SentryAndroidOptions {
        val opts = SentryAndroidOptions()
        applyOptions(opts, dsn, isDebug, appId, versionName, versionCode)
        return opts
    }

    @Test fun `dsn is set`() = assertEquals("https://key@sentry.io/123", buildOptions().dsn)
    @Test fun `release format`() = assertEquals("com.alexsiri7.unreminder@1.0.0+1", buildOptions().release)
    @Test fun `debug environment`() = assertEquals("debug", buildOptions(isDebug = true).environment)
    @Test fun `release environment`() = assertEquals("release", buildOptions(isDebug = false).environment)
    @Test fun `traces sample rate is zero`() = assertEquals(0.0, buildOptions().tracesSampleRate!!, 0.0)
    @Test fun `pii not sent`() = assertFalse(buildOptions().isSendDefaultPii)
}
