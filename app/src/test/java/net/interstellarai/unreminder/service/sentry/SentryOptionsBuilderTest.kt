package net.interstellarai.unreminder.service.sentry

import io.sentry.android.core.SentryAndroidOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentryOptionsBuilderTest {

    private fun buildOptions(
        dsn: String = "https://key@sentry.io/123",
        isDebug: Boolean = false,
        appId: String = "net.interstellarai.unreminder",
        versionName: String = "1.0.0",
        versionCode: Int = 1
    ): SentryAndroidOptions {
        val opts = SentryAndroidOptions()
        applyOptions(opts, dsn, isDebug, appId, versionName, versionCode)
        return opts
    }

    @Test fun `dsn is set`() = assertEquals("https://key@sentry.io/123", buildOptions().dsn)
    @Test fun `release format`() = assertEquals("net.interstellarai.unreminder@1.0.0+1", buildOptions().release)
    @Test fun `debug environment`() = assertEquals("debug", buildOptions(isDebug = true).environment)
    @Test fun `release environment`() = assertEquals("release", buildOptions(isDebug = false).environment)
    @Test fun `traces sample rate is zero`() = assertEquals(0.0, buildOptions().tracesSampleRate!!, 0.0)
    @Test fun `pii not sent`() = assertFalse(buildOptions().isSendDefaultPii)
    @Test fun `screenshots not attached`() = assertFalse(buildOptions().isAttachScreenshot)
    @Test fun `view hierarchy not attached`() = assertFalse(buildOptions().isAttachViewHierarchy)
    @Test fun `anr detection disabled`() = assertFalse(buildOptions().isAnrEnabled)
    @Test fun `shouldInitSentry returns false for blank dsn`() = assertFalse(shouldInitSentry(""))
    @Test fun `shouldInitSentry returns false for whitespace dsn`() = assertFalse(shouldInitSentry("   "))
    @Test fun `shouldInitSentry returns true for non-blank dsn`() = assertTrue(shouldInitSentry("https://key@sentry.io/123"))
}
