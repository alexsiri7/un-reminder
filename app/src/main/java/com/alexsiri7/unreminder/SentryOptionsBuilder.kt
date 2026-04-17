package com.alexsiri7.unreminder

import io.sentry.android.core.SentryAndroidOptions

/**
 * Builds Sentry options from the given parameters.
 *
 * Extracted as a pure function so privacy-sensitive options can be unit-tested
 * without Android framework setup.
 */
fun buildSentryOptions(
    dsn: String,
    isDebug: Boolean,
    appId: String,
    versionName: String,
    versionCode: Int
): SentryAndroidOptions = SentryAndroidOptions().apply {
    this.dsn = dsn
    this.environment = if (isDebug) "debug" else "release"
    this.release = "$appId@$versionName+$versionCode"
    this.tracesSampleRate = 0.0
    this.isSendDefaultPii = false
    this.isAttachScreenshot = false
    this.isAttachViewHierarchy = false
}
