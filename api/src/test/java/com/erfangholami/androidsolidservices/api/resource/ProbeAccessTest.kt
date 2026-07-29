package com.erfangholami.androidsolidservices.api.resource

import com.erfangholami.androidsolidservices.api.notifications.FakeSolidResourceManager
import com.erfangholami.androidsolidservices.shared.model.access.WacAllow
import com.erfangholami.androidsolidservices.shared.model.resource.AccessProbe
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeAccessTest {

    private val webId = "https://alice.pod/profile/card#me"
    private val uri = "https://bob.pod/shared/doc"

    private fun pod(head: SolidResult<SolidMetadata>) = FakeSolidResourceManager(onHead = { head })

    @Test
    fun `reports the WAC-Allow modes and owner when reachable`() {
        val meta = SolidMetadata.EMPTY.copy(
            wacAllow = WacAllow(userModes = setOf("read", "write"), publicModes = emptySet()),
            ownerUri = webId,
        )

        val probe = runBlocking { pod(SolidResult.Success(meta)).probeAccess(webId, uri).getOrThrow() }

        assertTrue(probe is AccessProbe.Accessible)
        probe as AccessProbe.Accessible
        assertEquals(setOf(ShareMode.READ, ShareMode.WRITE), probe.modes)
        assertEquals(webId, probe.ownerWebId)
    }

    @Test
    fun `a reachable resource without WAC-Allow reports View access`() {
        val probe = runBlocking {
            pod(SolidResult.Success(SolidMetadata.EMPTY)).probeAccess(webId, uri).getOrThrow()
        }
        assertEquals(AccessProbe.Accessible(setOf(ShareMode.READ), null), probe)
    }

    @Test
    fun `403 and 404 are a definitive Denied`() {
        val forbidden = runBlocking {
            pod(SolidResult.Failure(SolidError.fromHttp(403, "no"))).probeAccess(webId, uri).getOrThrow()
        }
        assertEquals(AccessProbe.Denied, forbidden)

        val missing = runBlocking {
            pod(SolidResult.Failure(SolidError.fromHttp(404, "no"))).probeAccess(webId, uri).getOrThrow()
        }
        assertEquals(AccessProbe.Denied, missing)
    }

    @Test
    fun `a 5xx is indeterminate — a Failure, not Denied`() {
        val result = runBlocking {
            pod(SolidResult.Failure(SolidError.fromHttp(503, "down"))).probeAccess(webId, uri)
        }
        assertTrue(result is SolidResult.Failure)
    }

    @Test
    fun `WAC-Allow listing no recognized mode is Denied`() {
        val meta = SolidMetadata.EMPTY.copy(wacAllow = WacAllow(emptySet(), emptySet()))
        val probe = runBlocking { pod(SolidResult.Success(meta)).probeAccess(webId, uri).getOrThrow() }
        assertEquals(AccessProbe.Denied, probe)
    }
}
