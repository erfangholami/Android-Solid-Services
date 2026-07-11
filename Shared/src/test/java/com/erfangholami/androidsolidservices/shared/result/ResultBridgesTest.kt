package com.erfangholami.androidsolidservices.shared.result

import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Tests the transitional bridges that let the legacy result types and the
 * unified [SolidResult] coexist while features migrate one at a time.
 */
class ResultBridgesTest {

    @Test
    fun `SolidNetworkResponse Success lifts to a SolidResult Success`() {
        assertEquals(SolidResult.Success("x"), SolidNetworkResponse.Success("x").toResult())
    }

    @Test
    fun `SolidNetworkResponse Error lifts to the typed error via fromHttp`() {
        val result = SolidNetworkResponse.Error<String>(412, "stale").toResult()
        assertEquals(SolidErrorCode.PRECONDITION_FAILED, result.errorOrNull()!!.code)
    }

    @Test
    fun `SolidNetworkResponse Exception lifts to the classified transport error`() {
        val result = SolidNetworkResponse.Exception<String>(IOException()).toResult()
        assertEquals(SolidErrorCode.NETWORK, result.errorOrNull()!!.code)
    }

    @Test
    fun `an http-backed failure projects back to a SolidNetworkResponse Error`() {
        val response = SolidResult.Failure(SolidError.Forbidden()).toNetworkResponse<String>()
        assertTrue(response is SolidNetworkResponse.Error)
        assertEquals(403, (response as SolidNetworkResponse.Error).errorCode)
    }

    @Test
    fun `a transport failure projects back to a SolidNetworkResponse Exception`() {
        val response = SolidResult.Failure(SolidError.Network()).toNetworkResponse<String>()
        assertTrue(response is SolidNetworkResponse.Exception)
    }

    @Test
    fun `a success projects back to a SolidNetworkResponse Success`() {
        val response = SolidResult.Success("ok").toNetworkResponse()
        assertEquals("ok", (response as SolidNetworkResponse.Success).data)
    }

    @Test
    fun `DataModuleResult bridges to the unified model`() {
        val share = GivenShare(ShareReceiver.Public, ShareMode.READ, "https://alice.pod/r")
        assertEquals(SolidResult.Success(share), DataModuleResult.Success(share).toResult())
        assertEquals(
            SolidErrorCode.UNKNOWN,
            DataModuleResult.Error<GivenShare>("bad").toResult().errorOrNull()!!.code,
        )
        assertEquals(
            SolidErrorCode.NETWORK,
            DataModuleResult.Exception<GivenShare>(IOException()).toResult().errorOrNull()!!.code,
        )
    }
}
