package com.alexsiri7.unreminder.service.sentry

import io.sentry.android.core.SentryAndroidOptions

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
    options.tracesSampleRate = 0.0
    options.isSendDefaultPii = false
    options.isAttachScreenshot = false
    options.isAttachViewHierarchy = false
}
