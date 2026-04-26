package net.interstellarai.unreminder.service.sentry

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
    options.isAnrEnabled = false
}
