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

    private val trampolineMessage =
        "List adapter activity trampoline invoked without specifying target intent."

    private fun activityStartFailure(message: String) =
        "Unable to start activity ComponentInfo{net.interstellarai.unreminder/" +
            "androidx.glance.appwidget.action.ActionTrampolineActivity}: " +
            "java.lang.IllegalArgumentException: $message"

    @Test fun `beforeSend drops the Glance list adapter trampoline crash as ActivityThread reports it`() {
        val event = SentryEvent(
            RuntimeException(activityStartFailure(trampolineMessage), IllegalArgumentException(trampolineMessage))
        )
        assertNull(buildOptions().beforeSend!!.execute(event, Hint()))
    }

    @Test fun `beforeSend drops the Glance list adapter trampoline IllegalArgumentException itself`() {
        val event = SentryEvent(IllegalArgumentException(trampolineMessage))
        assertNull(buildOptions().beforeSend!!.execute(event, Hint()))
    }

    @Test fun `beforeSend drops the Glance list adapter trampoline crash for a missing trampoline type`() {
        val missingTypeMessage = "List adapter activity trampoline invoked without trampoline type"
        val event = SentryEvent(
            RuntimeException(activityStartFailure(missingTypeMessage), IllegalArgumentException(missingTypeMessage))
        )
        assertNull(buildOptions().beforeSend!!.execute(event, Hint()))
    }

    @Test fun `beforeSend keeps an unrelated IllegalArgumentException`() {
        val event = SentryEvent(IllegalArgumentException("Receiver not registered: null"))
        assertEquals(event, buildOptions().beforeSend!!.execute(event, Hint()))
    }

    @Test fun `beforeSend keeps the trampoline message on another exception type`() {
        val event = SentryEvent(RuntimeException(trampolineMessage))
        assertEquals(event, buildOptions().beforeSend!!.execute(event, Hint()))
    }

    @Test fun `beforeSend keeps an activity start failure whose cause is not the trampoline IllegalArgumentException`() {
        val event = SentryEvent(
            RuntimeException(activityStartFailure(trampolineMessage), RuntimeException(trampolineMessage))
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
