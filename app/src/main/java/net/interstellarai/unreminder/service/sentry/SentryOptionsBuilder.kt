package net.interstellarai.unreminder.service.sentry

import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroidOptions

fun shouldInitSentry(dsn: String): Boolean = dsn.isNotBlank()

fun applyOptions(
    options: SentryAndroidOptions,
    dsn: String,
    isDebug: Boolean,
    appId: String,
    versionName: String,
    versionCode: Int
) {
    options.dsn = dsn
    options.environment = if (isDebug) "debug" else "release"
    options.release = "$appId@$versionName+$versionCode"
    // Privacy-safe defaults: no performance tracing, no PII, no UI snapshots
    options.tracesSampleRate = 0.0
    options.isSendDefaultPii = false
    options.isAttachScreenshot = false
    options.isAttachViewHierarchy = false
    options.isAnrEnabled = false // belt-and-suspenders guard against ANR watchdog crash on sideloaded pre-API-31 devices (see #133)
    options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
        if (isMisreportedApiLevelStopReasonCrash(event.throwable)) null else event
    }
}

// WorkManager only calls JobParameters.getStopReason() behind its own SDK_INT >= 31 guard, so this
// NoSuchMethodError can only reach us from a device whose reported SDK level doesn't match its
// framework. Nothing in this app can prevent the crash there; drop the report so it stops
// re-opening as a new issue (see #301).
private fun isMisreportedApiLevelStopReasonCrash(throwable: Throwable?): Boolean =
    throwable is NoSuchMethodError &&
        throwable.message?.contains("getStopReason") == true &&
        throwable.message?.contains("JobParameters") == true
