package com.erfangholami.androidsolidservices.client.sdk

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.erfangholami.androidsolidservices.client.internal.fakes.FakeSdk
import com.erfangholami.androidsolidservices.client.internal.fakes.Fixtures
import com.erfangholami.androidsolidservices.client.internal.fakes.assertArgs
import com.erfangholami.androidsolidservices.client.internal.fakes.nonRdfArg
import com.erfangholami.androidsolidservices.client.internal.fakes.rdfArg
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.AccessProbe
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStream

/**
 * Drives every [SolidResourceClient] method across a real binder into a fake hosted in another
 * process, and checks both halves of each call: the result comes back rebuilt, and the arguments
 * arrived in the slots they were sent in.
 *
 * The second half is what a returned value cannot tell you. `copy(webId, source, destination)` and
 * `copy(webId, destination, source)` are indistinguishable from the caller's side — the fake's
 * record is the only thing that separates them.
 */
@RunWith(AndroidJUnit4::class)
class ResourceClientIpcTest {

    @get:Rule
    val sdk = FakeSdk()

    private val client: SolidResourceClient
        get() = SolidResourceClient.getInstance(sdk.context) { true }

    @Test
    fun getWebId_returns_a_reconstructed_WebId(): Unit = runBlocking {
        val webId = client.getWebId(Fixtures.WEB_ID)

        assertEquals(Fixtures.WEB_ID, webId.getIdentifier())
        assertEquals(Fixtures.QUADS.size, webId.getAllQuads().size)
        sdk.recorded("getWebId").assertArgs("webId" to Fixtures.WEB_ID)
    }

    @Test
    fun head_returns_metadata_and_sends_the_resource_url(): Unit = runBlocking {
        val metadata = client.head(Fixtures.WEB_ID, Fixtures.RESOURCE)

        assertEquals(Fixtures.METADATA, metadata)
        sdk.recorded("head").assertArgs(
            "webId" to Fixtures.WEB_ID,
            "resourceUrl" to Fixtures.RESOURCE,
        )
    }

    @Test
    fun read_dispatches_an_RDF_class_to_readRdf(): Unit = runBlocking {
        val resource = client.read(Fixtures.WEB_ID, Fixtures.RESOURCE, SolidRDFResource::class.java)

        assertEquals(Fixtures.RESOURCE, resource.getIdentifier())
        sdk.recorded("readRdf").assertArgs(
            "webId" to Fixtures.WEB_ID,
            "resourceUrl" to Fixtures.RESOURCE,
        )
    }

    @Test
    fun read_dispatches_a_binary_class_to_read(): Unit = runBlocking {
        val resource = client.read(Fixtures.WEB_ID, Fixtures.BINARY, SolidNonRDFResource::class.java)

        assertEquals(Fixtures.BINARY, resource.getIdentifier())
        sdk.recorded("read").assertArgs(
            "webId" to Fixtures.WEB_ID,
            "resourceUrl" to Fixtures.BINARY,
        )
    }

    @Test
    fun read_rebuilds_an_RDF_subclass_the_service_never_saw(): Unit = runBlocking {
        val container = client.read(Fixtures.WEB_ID, Fixtures.CONTAINER, SolidContainer::class.java)

        assertTrue(container is SolidContainer)
        assertEquals(Fixtures.CONTAINER, container.getIdentifier())
    }

    @Test
    fun read_rebuilds_a_binary_subclass_the_service_never_saw(): Unit = runBlocking {
        val binary = client.read(Fixtures.WEB_ID, Fixtures.BINARY, SampleBinary::class.java)

        assertTrue(binary is SampleBinary)
        assertEquals(Fixtures.BINARY, binary.getIdentifier())
        assertEquals("image/png", binary.getContentType())
    }

    @Test
    fun read_rejects_a_class_that_is_neither_RDF_nor_binary(): Unit = runBlocking {
        val thrown = runCatching {
            client.read(Fixtures.WEB_ID, Fixtures.RESOURCE, NotAResource::class.java)
        }.exceptionOrNull()

        assertTrue(
            "expected IllegalArgumentException, got $thrown",
            thrown is IllegalArgumentException,
        )
    }

    @Test
    fun readContainer_returns_the_container(): Unit = runBlocking {
        val container = client.readContainer(Fixtures.WEB_ID, Fixtures.CONTAINER)

        assertEquals(Fixtures.CONTAINER, container.getIdentifier())
        sdk.recorded("readContainer").assertArgs(
            "webId" to Fixtures.WEB_ID,
            "containerUrl" to Fixtures.CONTAINER,
        )
    }

    @Test
    fun listContainer_passes_the_enrich_flag_through(): Unit = runBlocking {
        val plain = client.listContainer(Fixtures.WEB_ID, Fixtures.CONTAINER)
        sdk.recorded("listContainer").assertArgs("enrichWithHead" to false)

        client.listContainer(Fixtures.WEB_ID, Fixtures.CONTAINER, enrichWithHead = true)
        sdk.recorded("listContainer").assertArgs(
            "webId" to Fixtures.WEB_ID,
            "containerUri" to Fixtures.CONTAINER,
            "enrichWithHead" to true,
        )

        assertEquals(Fixtures.SOURCE_REFERENCES, plain)
    }

    @Test
    fun exists_returns_the_boolean(): Unit = runBlocking {
        assertTrue(client.exists(Fixtures.WEB_ID, Fixtures.RESOURCE))
        sdk.recorded("exists").assertArgs("webId" to Fixtures.WEB_ID, "uri" to Fixtures.RESOURCE)
    }

    @Test
    fun probeAccess_returns_the_sealed_variant_with_its_modes(): Unit = runBlocking {
        val probe = client.probeAccess(Fixtures.WEB_ID, Fixtures.RESOURCE)

        assertEquals(Fixtures.ACCESS_PROBE, probe)
        assertTrue("the Accessible variant must survive as itself", probe is AccessProbe.Accessible)
    }

    @Test
    fun create_dispatches_RDF_and_binary_to_their_own_methods(): Unit = runBlocking {
        client.create(Fixtures.WEB_ID, Fixtures.rdfResource())
        sdk.recorded("createRdf").assertArgs(
            "webId" to Fixtures.WEB_ID,
            "resource" to rdfArg(Fixtures.RESOURCE),
        )

        client.create(Fixtures.WEB_ID, Fixtures.nonRdfResource())
        sdk.recorded("create").assertArgs(
            "webId" to Fixtures.WEB_ID,
            "resource" to nonRdfArg(Fixtures.BINARY),
        )
    }

    @Test
    fun update_carries_the_if_match_etag(): Unit = runBlocking {
        client.update(Fixtures.WEB_ID, Fixtures.rdfResource(), ifMatch = "\"v1\"")
        sdk.recorded("updateRdf").assertArgs(
            "webId" to Fixtures.WEB_ID,
            "resource" to rdfArg(Fixtures.RESOURCE),
            "ifMatch" to "\"v1\"",
        )

        client.update(Fixtures.WEB_ID, Fixtures.nonRdfResource(), ifMatch = "*")
        sdk.recorded("update").assertArgs("ifMatch" to "*")
    }

    @Test
    fun an_omitted_if_match_arrives_as_null_not_an_empty_string(): Unit = runBlocking {
        client.update(Fixtures.WEB_ID, Fixtures.rdfResource())
        sdk.recorded("updateRdf").assertArgs("ifMatch" to null)
    }

    @Test
    fun delete_dispatches_RDF_and_binary_to_their_own_methods(): Unit = runBlocking {
        client.delete(Fixtures.WEB_ID, Fixtures.rdfResource())
        sdk.recorded("deleteRdf").assertArgs("resource" to rdfArg(Fixtures.RESOURCE))

        client.delete(Fixtures.WEB_ID, Fixtures.nonRdfResource())
        sdk.recorded("delete").assertArgs("resource" to nonRdfArg(Fixtures.BINARY))
    }

    @Test
    fun patch_serialises_the_typed_patch_to_n3(): Unit = runBlocking {
        val patch = N3Patch.build {
            insert(Fixtures.RESOURCE, "http://schema.org/name", "http://example.org/x")
            deleteLiteral(Fixtures.RESOURCE, "http://schema.org/name", "old")
        }

        client.patch(Fixtures.WEB_ID, Fixtures.RESOURCE, patch)

        sdk.recorded("patch").assertArgs(
            "webId" to Fixtures.WEB_ID,
            "resourceUrl" to Fixtures.RESOURCE,
            "patchBody" to patch.toN3String(),
        )
    }

    @Test
    fun patchRaw_sends_the_body_verbatim(): Unit = runBlocking {
        val body = "@prefix solid: <http://www.w3.org/ns/solid/terms#> .\n_:p a solid:InsertDeletePatch ."

        client.patchRaw(Fixtures.WEB_ID, Fixtures.RESOURCE, body)

        sdk.recorded("patch").assertArgs("patchBody" to body)
    }

    @Test
    fun ensureContainer_and_deleteContainer_send_their_uris(): Unit = runBlocking {
        client.ensureContainer(Fixtures.WEB_ID, Fixtures.CONTAINER)
        sdk.recorded("ensureContainer").assertArgs("containerUri" to Fixtures.CONTAINER)

        client.deleteContainer(Fixtures.WEB_ID, Fixtures.CONTAINER)
        sdk.recorded("deleteContainer").assertArgs("containerUrl" to Fixtures.CONTAINER)
    }

    @Test
    fun putRaw_carries_the_body_content_type_and_both_optional_headers(): Unit = runBlocking {
        client.putRaw(
            webId = Fixtures.WEB_ID,
            uri = Fixtures.RESOURCE,
            contentType = "text/turtle",
            body = Fixtures.PNG_BYTES,
            ifMatch = "\"v1\"",
            linkHeader = "<http://www.w3.org/ns/ldp#BasicContainer>; rel=\"type\"",
        )

        sdk.recorded("putRaw").assertArgs(
            "webId" to Fixtures.WEB_ID,
            "uri" to Fixtures.RESOURCE,
            "contentType" to "text/turtle",
            "body" to Fixtures.PNG_BYTES,
            "ifMatch" to "\"v1\"",
            "linkHeader" to "<http://www.w3.org/ns/ldp#BasicContainer>; rel=\"type\"",
        )
    }

    @Test
    fun putRaw_leaves_the_optional_headers_null_when_not_given(): Unit = runBlocking {
        client.putRaw(Fixtures.WEB_ID, Fixtures.RESOURCE, "text/turtle", Fixtures.PNG_BYTES)
        sdk.recorded("putRaw").assertArgs("ifMatch" to null, "linkHeader" to null)
    }

    @Test
    fun post_turns_the_header_map_into_a_bundle_and_returns_the_location(): Unit = runBlocking {
        val location = client.post(
            webId = Fixtures.WEB_ID,
            uri = Fixtures.CONTAINER,
            contentType = "text/turtle",
            body = Fixtures.PNG_BYTES,
            additionalHeaders = mapOf("Slug" to "first", "Link" to "<x>; rel=\"type\""),
        )

        assertEquals(Fixtures.RESOURCE, location)
        sdk.recorded("post").assertArgs(
            "uri" to Fixtures.CONTAINER,
            "additionalHeaders" to mapOf("Link" to "<x>; rel=\"type\"", "Slug" to "first"),
        )
    }

    @Test
    fun post_with_no_extra_headers_sends_an_empty_bundle(): Unit = runBlocking {
        client.post(Fixtures.WEB_ID, Fixtures.CONTAINER, "text/turtle", Fixtures.PNG_BYTES)
        sdk.recorded("post").assertArgs("additionalHeaders" to emptyMap<String, String>())
    }

    @Test
    fun createInContainer_dispatches_by_resource_kind_and_returns_the_location(): Unit = runBlocking {
        val rdfLocation = client.createInContainer(
            Fixtures.WEB_ID,
            Fixtures.CONTAINER,
            Fixtures.rdfResource(),
        )
        assertEquals(Fixtures.RESOURCE, rdfLocation)
        sdk.recorded("createInContainerRdf").assertArgs(
            "containerUri" to Fixtures.CONTAINER,
            "resource" to rdfArg(Fixtures.RESOURCE),
        )

        val binaryLocation = client.createInContainer(
            Fixtures.WEB_ID,
            Fixtures.CONTAINER,
            Fixtures.nonRdfResource(),
        )
        assertEquals(Fixtures.BINARY, binaryLocation)
        sdk.recorded("createInContainer").assertArgs("resource" to nonRdfArg(Fixtures.BINARY))
    }

    @Test
    fun copy_sends_source_then_destination(): Unit = runBlocking {
        val result = client.copy(Fixtures.WEB_ID, Fixtures.RESOURCE, Fixtures.DESTINATION)

        assertEquals(Fixtures.DESTINATION, result)
        sdk.recorded("copy").assertArgs(
            "sourceUri" to Fixtures.RESOURCE,
            "destinationUri" to Fixtures.DESTINATION,
        )
    }

    @Test
    fun move_sends_source_then_destination(): Unit = runBlocking {
        client.move(Fixtures.WEB_ID, Fixtures.RESOURCE, Fixtures.DESTINATION)

        sdk.recorded("move").assertArgs(
            "sourceUri" to Fixtures.RESOURCE,
            "destinationUri" to Fixtures.DESTINATION,
        )
    }

    @Test
    fun rename_sends_the_new_name_not_a_uri(): Unit = runBlocking {
        client.rename(Fixtures.WEB_ID, Fixtures.RESOURCE, "second")

        sdk.recorded("rename").assertArgs(
            "sourceUri" to Fixtures.RESOURCE,
            "newName" to "second",
        )
    }

    @Test
    fun readPublic_takes_no_webId_and_dispatches_by_class(): Unit = runBlocking {
        val profile = client.readPublic(Fixtures.PEER_WEB_ID, WebId::class.java)
        assertEquals(Fixtures.PEER_WEB_ID, profile.getIdentifier())
        sdk.recorded("readPublicRdf").assertArgs("uri" to Fixtures.PEER_WEB_ID)

        client.readPublic(Fixtures.BINARY, SolidNonRDFResource::class.java)
        sdk.recorded("readPublic").assertArgs("uri" to Fixtures.BINARY)
    }

    @Test
    fun headPublic_takes_no_webId(): Unit = runBlocking {
        assertEquals(Fixtures.METADATA, client.headPublic(Fixtures.BINARY))
        sdk.recorded("headPublic").assertArgs("uri" to Fixtures.BINARY)
    }

    @Test
    fun a_service_error_arrives_as_the_mapped_exception_type(): Unit = runBlocking {
        val thrown = runCatching { client.head(Fixtures.FAILING_WEB_ID, Fixtures.RESOURCE) }
            .exceptionOrNull()

        assertTrue(
            "expected NotPermissionException, got $thrown",
            thrown is SolidException.SolidResourceException.NotPermissionException,
        )
        assertEquals(Fixtures.ERROR_MESSAGE, thrown?.message)
    }

    @Test
    fun a_missing_ASS_app_fails_before_any_IPC(): Unit = runBlocking {
        SolidResourceClient.resetForTests()
        val uninstalled = SolidResourceClient.getInstance(sdk.context) { false }

        val thrown = runCatching { uninstalled.exists(Fixtures.WEB_ID, Fixtures.RESOURCE) }
            .exceptionOrNull()

        assertTrue(
            "expected SolidAppNotFoundException, got $thrown",
            thrown is SolidException.SolidAppNotFoundException,
        )
    }

    /** A consumer-defined binary type — the only way to reach `reconstructNonRdf`. */
    class SampleBinary(
        identifier: String,
        contentType: String,
        entity: InputStream,
        headers: SolidHeaders?,
    ) : SolidNonRDFResource(identifier, contentType, entity, headers)

    /** Neither RDF nor binary; `read` must refuse it. */
    class NotAResource : com.erfangholami.androidsolidservices.shared.model.resource.SolidResource {
        override fun getIdentifier(): String = error("never called")
        override fun getContentType(): String = error("never called")
        override fun getHeaders(): SolidHeaders = error("never called")
        override fun getEntity(): InputStream = error("never called")
        override fun getMetadata() = error("never called")
        override fun describeContents(): Int = 0
        override fun writeToParcel(dest: android.os.Parcel, flags: Int) = Unit
        override fun close() = Unit
    }
}
