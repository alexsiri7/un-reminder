package com.alexsiri7.unreminder.service.sentry

import android.content.Context
import android.content.SharedPreferences
import io.sentry.Sentry

/**
 * Fires a "launch-smoke" Sentry message once per install per versionCode.
 *
 * Purpose: give us a verifiable Sentry event on the first launch after install/upgrade so we
 * can confirm the DSN, source-context upload, and release tag plumbing for each new APK.
 * Subsequent launches on the same versionCode are no-ops so real users aren't spammed.
 */
object LaunchSmokeTest {

    private const val PREFS_NAME = "sentry_launch_smoke"
    private const val KEY_LAST_REPORTED_VERSION_CODE = "last_reported_version_code"
    private const val UNSET = -1

    /**
     * Fires the smoke event for [versionCode] if not already reported. Uses [Sentry.captureMessage]
     * by default; the [capture] and [store] parameters exist for dependency injection in tests.
     *
     * @return true if a message was fired, false if already reported for this versionCode.
     */
    fun maybeFire(
        context: Context,
        versionName: String,
        versionCode: Int,
        capture: (String) -> Unit = { Sentry.captureMessage(it) },
        store: SmokeStore = SharedPrefsSmokeStore(context)
    ): Boolean {
        val last = store.lastReportedVersionCode()
        if (last == versionCode) return false
        capture("launch-smoke v$versionName+$versionCode")
        store.setLastReportedVersionCode(versionCode)
        return true
    }

    /** Pluggable storage surface so tests can avoid Android framework types. */
    interface SmokeStore {
        fun lastReportedVersionCode(): Int
        fun setLastReportedVersionCode(value: Int)
    }

    private class SharedPrefsSmokeStore(context: Context) : SmokeStore {
        private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        override fun lastReportedVersionCode(): Int =
            prefs.getInt(KEY_LAST_REPORTED_VERSION_CODE, UNSET)

        override fun setLastReportedVersionCode(value: Int) {
            prefs.edit().putInt(KEY_LAST_REPORTED_VERSION_CODE, value).apply()
        }
    }
}
