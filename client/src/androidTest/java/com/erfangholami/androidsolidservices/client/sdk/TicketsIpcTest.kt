package com.erfangholami.androidsolidservices.client.sdk

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.erfangholami.androidsolidservices.client.internal.fakes.CallLog
import com.erfangholami.androidsolidservices.client.internal.fakes.FakeSdk
import com.erfangholami.androidsolidservices.client.internal.fakes.Fixtures
import com.erfangholami.androidsolidservices.client.internal.fakes.assertArgs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives every [SolidTicketsDataModule] operation across a real binder.
 *
 * `createTicket` takes eight parameters, five of them optional and three of those `String?` in a
 * row, which makes it the widest surface in the SDK for an argument to land in the wrong slot.
 */
@RunWith(AndroidJUnit4::class)
class TicketsIpcTest {

    @get:Rule
    val sdk = FakeSdk()

    private val tickets: SolidTicketsDataModule
        get() = SolidTicketsDataModule.getInstance(sdk.context)

    @Test
    fun listTickets_returns_the_index(): Unit = runBlocking {
        assertEquals(Fixtures.TICKET_LIST, tickets.listTickets(Fixtures.WEB_ID))
        sdk.recorded("listTickets").assertArgs("webId" to Fixtures.WEB_ID)
    }

    @Test
    fun getTicket_returns_the_full_document(): Unit = runBlocking {
        val ticket = tickets.getTicket(Fixtures.WEB_ID, Fixtures.TICKET)

        assertEquals(Fixtures.TICKET_MODEL, ticket)
        assertEquals(Fixtures.NEW_TICKET.barcodes, ticket?.barcodes)
        sdk.recorded("getTicket").assertArgs("ticketUri" to Fixtures.TICKET)
    }

    @Test
    fun createTicket_carries_every_optional_in_its_own_slot(): Unit = runBlocking {
        tickets.createTicket(
            webId = Fixtures.WEB_ID,
            newTicket = Fixtures.NEW_TICKET,
            storage = Fixtures.STORAGE,
            artifact = Fixtures.PNG_BYTES,
            artifactContentType = "application/vnd.apple.pkpass",
            images = Fixtures.TICKET_IMAGES,
            isPrivate = false,
            container = Fixtures.CONTAINER,
        )

        val call = sdk.recorded("createTicket")
        call.assertArgs(
            "webId" to Fixtures.WEB_ID,
            "newTicket" to Fixtures.NEW_TICKET,
            "storage" to Fixtures.STORAGE,
            "artifact" to Fixtures.PNG_BYTES,
            "artifactContentType" to "application/vnd.apple.pkpass",
            "isPrivate" to false,
            "container" to Fixtures.CONTAINER,
        )
        assertEquals(
            "{logo=${CallLog.describe(Fixtures.PNG_BYTES)}, icon=${CallLog.describe(Fixtures.PNG_BYTES)}, " +
                "strip=${CallLog.NULL}, thumbnail=${CallLog.NULL}, footer=${CallLog.NULL}, " +
                "background=${CallLog.NULL}}",
            call.arg("images"),
        )
    }

    @Test
    fun createTicket_defaults_leave_every_optional_null_and_the_ticket_private(): Unit = runBlocking {
        tickets.createTicket(Fixtures.WEB_ID, Fixtures.NEW_TICKET)

        sdk.recorded("createTicket").assertArgs(
            "storage" to null,
            "artifact" to null,
            "artifactContentType" to null,
            "images" to null,
            "isPrivate" to true,
            "container" to null,
        )
    }

    @Test
    fun updateTicket_sends_the_uri_then_the_replacement(): Unit = runBlocking {
        tickets.updateTicket(Fixtures.WEB_ID, Fixtures.TICKET, Fixtures.NEW_TICKET)

        sdk.recorded("updateTicket").assertArgs(
            "ticketUri" to Fixtures.TICKET,
            "updated" to Fixtures.NEW_TICKET,
        )
    }

    @Test
    fun putTicketArtifact_carries_the_bytes_and_type(): Unit = runBlocking {
        tickets.putTicketArtifact(
            webId = Fixtures.WEB_ID,
            ticketUri = Fixtures.TICKET,
            artifact = Fixtures.PNG_BYTES,
            artifactContentType = "application/vnd.apple.pkpass",
        )

        sdk.recorded("putTicketArtifact").assertArgs(
            "ticketUri" to Fixtures.TICKET,
            "artifact" to Fixtures.PNG_BYTES,
            "artifactContentType" to "application/vnd.apple.pkpass",
            "images" to null,
        )
    }

    @Test
    fun deleteTicket_sends_the_uri(): Unit = runBlocking {
        assertEquals(Fixtures.TICKET_MODEL, tickets.deleteTicket(Fixtures.WEB_ID, Fixtures.TICKET))
        sdk.recorded("deleteTicket").assertArgs("ticketUri" to Fixtures.TICKET)
    }

    @Test
    fun getTicketArtifact_returns_the_bytes_unchanged(): Unit = runBlocking {
        val artifact = tickets.getTicketArtifact(Fixtures.WEB_ID, Fixtures.ARTIFACT)

        assertEquals(Fixtures.ARTIFACT, artifact?.uri)
        assertTrue(Fixtures.PNG_BYTES.contentEquals(artifact?.bytes))
        sdk.recorded("getTicketArtifact").assertArgs("artifactUri" to Fixtures.ARTIFACT)
    }

    @Test
    fun a_service_error_arrives_as_the_mapped_exception_type(): Unit = runBlocking {
        val thrown = runCatching { tickets.listTickets(Fixtures.FAILING_WEB_ID) }.exceptionOrNull()

        assertTrue(
            "expected NullWebIdException, got $thrown",
            thrown is SolidException.SolidResourceException.NullWebIdException,
        )
    }
}
