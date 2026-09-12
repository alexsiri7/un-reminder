package net.interstellarai.unreminder.service.geofence

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes

object LocationFaultLabel {
    /**
     * The short fault label shown in the Settings health row, and — since the geofence
     * reporters use `captureMessage` with no throwable attached — the only diagnostic those
     * Sentry events carry.
     *
     * An [ApiException] is decoded to its status code because "NETWORK_ERROR(7)" names the
     * fault where "ApiException" would not; the stable class names `-keepnames` now
     * guarantees do not make that arm redundant, and `LocationFaultLabelTest` and
     * `LocationReconcilerTest` assert the decoded label that `SettingsViewModelTest` expects
     * to reach the health row. Every other cause reports its simple class name, which
     * survives the release build thanks to `-keepnames class * extends java.lang.Throwable`
     * in proguard-rules.pro.
     */
    fun of(cause: Throwable): String = when (cause) {
        is ApiException -> "${CommonStatusCodes.getStatusCodeString(cause.statusCode)}(${cause.statusCode})"
        else -> cause.javaClass.simpleName
    }
}
