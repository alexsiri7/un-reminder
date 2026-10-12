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
        val throwable = event.throwable
        if (isMisreportedApiLevelStopReasonCrash(throwable) ||
            isMisreportedApiLevelUnsafeIntentLaunchCrash(throwable) ||
            isListAdapterTrampolineCrash(throwable)
        ) {
            null
        } else {
            event
        }
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

// minSdk is 31 and no app code touches StrictMode; play-services-basement calls the API-31
// VmPolicy.Builder.permitUnsafeIntentLaunch() internally, so this NoSuchMethodError can only come
// from a device whose framework lacks a method its reported SDK level promises (as in #301).
// Nothing in this app can prevent the crash; drop the report so it stops re-opening as a new
// issue (see #474).
private fun isMisreportedApiLevelUnsafeIntentLaunchCrash(throwable: Throwable?): Boolean =
    throwable is NoSuchMethodError &&
        throwable.message?.contains("permitUnsafeIntentLaunch") == true &&
        throwable.message?.contains("StrictMode\$VmPolicy\$Builder") == true

// Glance 1.1.1 only routes a click through ActionTrampolineActivity when the clickable sits inside
// a LazyColumn/LazyVerticalGrid (applyAction() takes the fill-in-intent branch iff
// TranslationContext.isLazyCollectionDescendant()), and DoableHabitWidget has never used one, so
// this IllegalArgumentException can't come from a click this app composed. Nothing in this app can
// prevent the crash; drop the report so it stops re-opening as a new issue (see #432).
// launchTrampolineAction() has two guards (missing target intent, missing trampoline type) whose
// messages share this prefix, so the match covers both. ActivityThread wraps activity start
// failures in a RuntimeException, so the match walks the causes.
private fun isListAdapterTrampolineCrash(throwable: Throwable?): Boolean =
    generateSequence(throwable, Throwable::cause).any {
        it is IllegalArgumentException &&
            it.message?.contains("List adapter activity trampoline invoked without") == true
    }
