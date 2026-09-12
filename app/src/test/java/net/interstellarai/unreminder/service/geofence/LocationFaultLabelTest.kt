package net.interstellarai.unreminder.service.geofence

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocationFaultLabelTest {

    private class TestFaultException : RuntimeException()

    @Test
    fun `every fault maps to its label`() {
        val cases = listOf(
            ApiException(Status(CommonStatusCodes.NETWORK_ERROR)) to "NETWORK_ERROR(7)",
            ApiException(Status(CommonStatusCodes.API_NOT_CONNECTED)) to "API_NOT_CONNECTED(17)",
            SecurityException("revoked") to "SecurityException",
            IllegalStateException("client gone") to "IllegalStateException",
            TestFaultException() to "TestFaultException",
        )

        cases.forEach { (cause, expected) ->
            assertEquals(expected, LocationFaultLabel.of(cause))
        }
    }
}
