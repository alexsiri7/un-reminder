package net.interstellarai.unreminder.service.sentry

import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.android.core.SentryAndroidOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    @Test fun `beforeSend drops the WorkManager getStopReason NoSuchMethodError`() {
        val event = SentryEvent(
            NoSuchMethodError(
                "No virtual method getStopReason()I in class Landroid/app/job/JobParameters; " +
                    "or its super classes (declaration of 'android.app.job.JobParameters' " +
                    "appears in /system/framework/framework.jar)"
            )
        )
        assertNull(buildOptions().beforeSend!!.execute(event, Hint()))
    }

    @Test fun `beforeSend keeps an unrelated NoSuchMethodError`() {
        val event = SentryEvent(
            NoSuchMethodError(
                "No virtual method permitUnsafeIntentLaunch()Landroid/os/StrictMode\$VmPolicy\$Builder; " +
                    "in class Landroid/os/StrictMode\$VmPolicy\$Builder;"
            )
        )
        assertEquals(event, buildOptions().beforeSend!!.execute(event, Hint()))
    }

    @Test fun `beforeSend keeps a NoSuchMethodError naming getStopReason on another class`() {
        val event = SentryEvent(
            NoSuchMethodError(
                "No virtual method getStopReason()I in class Landroid/app/NotificationChannel;"
            )
        )
        assertEquals(event, buildOptions().beforeSend!!.execute(event, Hint()))
    }

    @Test fun `beforeSend keeps a NoSuchMethodError on JobParameters for another method`() {
        val event = SentryEvent(
            NoSuchMethodError(
                "No virtual method getNetwork()Landroid/net/Network; in class Landroid/app/job/JobParameters;"
            )
        )
        assertEquals(event, buildOptions().beforeSend!!.execute(event, Hint()))
    }

    @Test fun `beforeSend keeps unrelated exceptions`() {
        val event = SentryEvent(RuntimeException("boom"))
        assertEquals(event, buildOptions().beforeSend!!.execute(event, Hint()))
    }

    @Test fun `shouldInitSentry returns false for blank dsn`() = assertFalse(shouldInitSentry(""))
    @Test fun `shouldInitSentry returns false for whitespace dsn`() = assertFalse(shouldInitSentry("   "))
    @Test fun `shouldInitSentry returns true for non-blank dsn`() = assertTrue(shouldInitSentry("https://key@sentry.io/123"))
}
