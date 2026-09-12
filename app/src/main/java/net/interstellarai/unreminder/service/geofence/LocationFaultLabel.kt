package net.interstellarai.unreminder.service.geofence

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes

object LocationFaultLabel {
    /**
     * The short fault label shown in the Settings health row and attached to the geofence
     * Sentry events, which carry no throwable of their own. An [ApiException] is decoded to
     * its status code because "NETWORK_ERROR(7)" names the fault where "ApiException" would
     * not; every other cause reports its simple class name, which survives the release build
     * thanks to the -keepnames rule for Throwable in proguard-rules.pro.
     */
    fun of(cause: Throwable): String = when (cause) {
        is ApiException -> "${CommonStatusCodes.getStatusCodeString(cause.statusCode)}(${cause.statusCode})"
        else -> cause.javaClass.simpleName
    }
}
