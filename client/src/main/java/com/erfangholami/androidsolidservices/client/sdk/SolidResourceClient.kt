package com.erfangholami.androidsolidservices.client.sdk

import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import com.erfangholami.androidsolidservices.client.internal.ANDROID_SOLID_SERVICES_CRUD_SERVICE
import com.erfangholami.androidsolidservices.client.internal.CallbackBridge
import com.erfangholami.androidsolidservices.client.internal.ServiceConnector
import com.erfangholami.androidsolidservices.shared.IASSResourceService
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import com.erfangholami.androidsolidservices.shared.ipc.parcelable
import com.erfangholami.androidsolidservices.shared.ipc.parcelableList
import com.erfangholami.androidsolidservices.shared.ipc.streamContentLength
import com.erfangholami.androidsolidservices.shared.ipc.streamContentType
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.AccessProbe
import com.erfangholami.androidsolidservices.shared.model.resource.NonRDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.RDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidResource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidSourceReference
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

/**
 * Reads, creates, updates and deletes resources on the authenticated user's Solid pod by
 * communicating with the Android Solid Services app over IPC.
 *
 * Obtain an instance via [Solid.getResourceClient].
 *
 * All public methods are `suspend` functions and must be called from a coroutine. Each returns its
 * result or throws a [SolidException] subtype on failure — including precondition failures
 * ([SolidException.SolidAppNotFoundException], [SolidException.SolidServiceConnectionException]).
 *
 * @see Solid.getResourceClient
 */
public class SolidResourceClient private constructor(
    context: Context,
    private val hasInstalledAndroidSolidServices: () -> Boolean,
) {

    public companion object {
        @Volatile
        private var instance: SolidResourceClient? = null

        /**
         * Returns the application-scoped singleton [SolidResourceClient].
         * @param context Any [Context]; the application context is used internally.
         * @param hasInstalledAndroidSolidServices Returns `true` when the Android Solid Services
         *   app is installed on the device.
         */
        public fun getInstance(
            context: Context,
            hasInstalledAndroidSolidServices: () -> Boolean,
        ): SolidResourceClient =
            instance ?: synchronized(this) {
                instance ?: SolidResourceClient(context, hasInstalledAndroidSolidServices)
                    .also { instance = it }
            }

        /**
         * Drops the singleton and releases its binding, so the next [getInstance] builds a fresh
         * client. Exists only so instrumented tests can rebuild the client against a different
         * service package or install check; nothing in production calls it.
         */
        internal fun resetForTests() {
            synchronized(this) {
                instance?.connector?.unbind()
                instance = null
            }
        }
    }

    private val connector = ServiceConnector(
        context,
        ANDROID_SOLID_SERVICES_CRUD_SERVICE,
        IASSResourceService.Stub::asInterface,
    )

    /**
     * Hot [Flow] of the IPC service connection state.
     * Emits `true` once the bound service connects and `false` if it disconnects.
     */
    public fun resourceServiceConnectionState(): Flow<Boolean> = connector.connectionState

    /**
     * Fetches and parses the WebID document for [webId] from their pod.
     * @throws SolidException on failure.
     */
    public suspend fun getWebId(webId: String): WebId = call { service, bridge ->
        service.getWebId(webId, rdfCallback(bridge) { reconstructRdf(it, WebId::class.java) })
    }

    /**
     * Fetches only the HTTP headers for the resource at [resourceUrl] via HTTP HEAD.
     *
     * Returns [SolidMetadata] with ETag, Content-Type, Content-Length, WAC-Allow, Allow,
     * Link relations (acl, describedby, type, storageDescription), Accept-Patch/Post/Put,
     * Last-Modified, and WWW-Authenticate — with no body transfer.
     *
     * @throws SolidException on failure.
     */
    public suspend fun head(webId: String, resourceUrl: String): SolidMetadata = call { service, bridge ->
        service.head(webId, resourceUrl, requiredBridge(bridge, SolidMetadata::class.java))
    }

    /**
     * Reads a resource from the pod for [webId].
     * @param clazz The expected resource type; must extend [RDFResource] or [NonRDFResource].
     * @throws SolidException on failure.
     */
    public suspend fun <T : SolidResource> read(
        webId: String,
        resourceUrl: String,
        clazz: Class<T>,
    ): T = call { service, bridge ->
        when {
            RDFResource::class.java.isAssignableFrom(clazz) ->
                service.readRdf(webId, resourceUrl, rdfCallback(bridge) { reconstructRdf(it, clazz) })

            NonRDFResource::class.java.isAssignableFrom(clazz) ->
                service.read(webId, resourceUrl, nonRdfCallback(bridge) { reconstructNonRdf(it, clazz) })

            else -> bridge.onFailure(
                IllegalArgumentException("Class must extend RDFResource or NonRDFResource.")
            )
        }
    }

    /**
     * Creates a new resource on the pod at the URI specified by [resource] for [webId].
     * @throws SolidException on failure.
     */
    public suspend fun <T : SolidResource> create(webId: String, resource: T): T = call { service, bridge ->
        @Suppress("UNCHECKED_CAST") val clazz = resource.javaClass as Class<T>
        when (resource) {
            is SolidRDFResource ->
                service.createRdf(webId, resource, rdfCallback(bridge) { reconstructRdf(it, clazz) })

            is SolidNonRDFResource ->
                service.create(webId, resource, nonRdfCallback(bridge) { reconstructNonRdf(it, clazz) })

            else -> bridge.onFailure(
                IllegalArgumentException("Resource must be SolidRDFResource or SolidNonRDFResource.")
            )
        }
    }

    /**
     * Replaces an existing resource on the pod via HTTP PUT.
     *
     * Pass [ifMatch] (the ETag from a previous [read]) to issue a conditional PUT that fails with a
     * 412 error if the resource was modified concurrently. For RDF resources, prefer [patch] when
     * only a subset of triples changes.
     *
     * @throws SolidException on failure.
     */
    public suspend fun <T : SolidResource> update(
        webId: String,
        resource: T,
        ifMatch: String? = null,
    ): T = call { service, bridge ->
        @Suppress("UNCHECKED_CAST") val clazz = resource.javaClass as Class<T>
        when (resource) {
            is SolidRDFResource ->
                service.updateRdf(webId, resource, ifMatch, rdfCallback(bridge) { reconstructRdf(it, clazz) })

            is SolidNonRDFResource ->
                service.update(webId, resource, ifMatch, nonRdfCallback(bridge) { reconstructNonRdf(it, clazz) })

            else -> bridge.onFailure(
                IllegalArgumentException("Resource must be SolidRDFResource or SolidNonRDFResource.")
            )
        }
    }

    /**
     * Applies an N3 Patch to an RDF resource on the pod via HTTP PATCH.
     *
     * This is the preferred method for partial updates to RDF resources — it is atomic and does not
     * require reading the full resource first. Not applicable to [SolidNonRDFResource].
     *
     * @throws SolidException on failure.
     */
    public suspend fun patch(webId: String, uri: String, patch: N3Patch): Unit = call { service, bridge ->
        service.patch(webId, uri, patch.toN3String(), unitBridge(bridge))
    }

    /**
     * Deletes a resource from the pod for [webId].
     * @throws SolidException on failure.
     */
    public suspend fun <T : SolidResource> delete(webId: String, resource: T): T = call { service, bridge ->
        @Suppress("UNCHECKED_CAST") val clazz = resource.javaClass as Class<T>
        when (resource) {
            is SolidRDFResource ->
                service.deleteRdf(webId, resource, rdfCallback(bridge) { reconstructRdf(it, clazz) })

            is SolidNonRDFResource ->
                service.delete(webId, resource, nonRdfCallback(bridge) { reconstructNonRdf(it, clazz) })

            else -> bridge.onFailure(
                IllegalArgumentException("Resource must be SolidRDFResource or SolidNonRDFResource.")
            )
        }
    }

    /**
     * Reads an LDP container from the pod, with each contained resource enriched by its own HTTP
     * HEAD metadata (carried in each `SolidSourceReference.headMetadata`).
     *
     * @throws SolidException on failure.
     */
    public suspend fun readContainer(webId: String, containerUrl: String): SolidContainer =
        call { service, bridge ->
            service.readContainer(webId, containerUrl, requiredBridge(bridge, SolidContainer::class.java))
        }

    /**
     * Recursively deletes a container and all of its contents.
     * @param containerUri The URI of the LDP container to delete (must end with `/`).
     * @throws SolidException on failure.
     */
    public suspend fun deleteContainer(webId: String, containerUri: String): Unit = call { service, bridge ->
        service.deleteContainer(webId, containerUri, unitBridge(bridge))
    }

    private suspend fun <T> call(register: (IASSResourceService, CallbackBridge<T>) -> Unit): T {
        if (!hasInstalledAndroidSolidServices()) {
            throw SolidException.SolidAppNotFoundException()
        }
        return connector.await(register)
    }

    /**
     * Applies an already-serialised N3 Patch document to an RDF resource.
     *
     * Use this when you hold the `text/n3` body itself (e.g. one you received or stored);
     * prefer [patch] with a typed [N3Patch] when you are building the patch in-process.
     *
     * @throws SolidException on failure.
     */
    public suspend fun patchRaw(webId: String, uri: String, n3Body: String): Unit =
        call { service, bridge ->
            service.patch(webId, uri, n3Body, unitBridge(bridge))
        }

    /**
     * Reports whether a resource exists: a `404` is `false`; anything indeterminate (403,
     * auth, network, 5xx) throws rather than being collapsed to `false`.
     */
    public suspend fun exists(webId: String, uri: String): Boolean = call { service, bridge ->
        service.exists(webId, uri, booleanBridge(bridge))
    }

    /** Creates the container and any missing ancestors, bottom-up. Idempotent. */
    public suspend fun ensureContainer(webId: String, containerUri: String): Unit =
        call { service, bridge ->
            service.ensureContainer(webId, containerUri, unitBridge(bridge))
        }

    /**
     * Reports the access the user effectively holds on [uri], from its `WAC-Allow` header.
     *
     * An *indeterminate* outcome (a 401 blip, 5xx, transport error) **throws** — it does not
     * come back as [AccessProbe.Denied]. Never treat a failure here as a denial.
     */
    public suspend fun probeAccess(webId: String, uri: String): AccessProbe =
        call { service, bridge ->
            service.probeAccess(webId, uri, envelopeBridge(bridge) {
                it.parcelable(AccessProbe::class.java) ?: AccessProbe.Denied
            })
        }

    /**
     * Lists a container's direct children in a single GET.
     *
     * @param enrichWithHead Also HEAD each child (bounded concurrency) to fill in metadata
     *   for servers that don't enrich the listing. Costs one request per child.
     */
    public suspend fun listContainer(
        webId: String,
        containerUri: String,
        enrichWithHead: Boolean = false,
    ): List<SolidSourceReference> = call { service, bridge ->
        service.listContainer(
            webId,
            containerUri,
            enrichWithHead,
            envelopeListBridge(bridge) { it.parcelableList(SolidSourceReference::class.java) },
        )
    }

    /** Copies a resource, or a whole container tree, to [destinationUri]. Not transactional. */
    public suspend fun copy(webId: String, sourceUri: String, destinationUri: String): String =
        call { service, bridge ->
            service.copy(webId, sourceUri, destinationUri, stringBridge(bridge))
        }

    /** A [copy] followed by a delete of the source. Not transactional. */
    public suspend fun move(webId: String, sourceUri: String, destinationUri: String): String =
        call { service, bridge ->
            service.move(webId, sourceUri, destinationUri, stringBridge(bridge))
        }

    /** Moves a resource to a sibling name in the same container. */
    public suspend fun rename(webId: String, sourceUri: String, newName: String): String =
        call { service, bridge ->
            service.rename(webId, sourceUri, newName, stringBridge(bridge))
        }

    /**
     * Reads a **public** resource with no `Authorization` header — most usefully a foreign
     * WebID profile document, which a pod may reject when presented a foreign issuer's token.
     */
    public suspend fun <T : SolidResource> readPublic(uri: String, clazz: Class<T>): T =
        call { service, bridge ->
            when {
                RDFResource::class.java.isAssignableFrom(clazz) ->
                    service.readPublicRdf(uri, rdfCallback(bridge) { reconstructRdf(it, clazz) })

                NonRDFResource::class.java.isAssignableFrom(clazz) ->
                    service.readPublic(uri, nonRdfCallback(bridge) { reconstructNonRdf(it, clazz) })

                else -> bridge.onFailure(
                    IllegalArgumentException("Class must extend RDFResource or NonRDFResource.")
                )
            }
        }

    /** HEADs a **public** resource with no `Authorization` header. */
    public suspend fun headPublic(uri: String): SolidMetadata = call { service, bridge ->
        service.headPublic(uri, requiredBridge(bridge, SolidMetadata::class.java))
    }

    /**
     * PUTs an opaque body to [uri].
     *
     * @param ifMatch `null` → unconditional; `"*"` → the resource must already exist; an
     *   ETag → optimistic concurrency (fails with `412` if it changed since you read it).
     */
    public suspend fun putRaw(
        webId: String,
        uri: String,
        contentType: String,
        body: ByteArray,
        ifMatch: String? = null,
        linkHeader: String? = null,
    ): Unit = call { service, bridge ->
        service.putRaw(webId, uri, contentType, body, ifMatch, linkHeader, unitBridge(bridge))
    }

    /** POSTs an opaque body to a container; returns the `Location` the server allocated. */
    public suspend fun post(
        webId: String,
        uri: String,
        contentType: String,
        body: ByteArray,
        additionalHeaders: Map<String, String> = emptyMap(),
    ): String? = call { service, bridge ->
        val bundle = Bundle().apply { additionalHeaders.forEach { (k, v) -> putString(k, v) } }
        service.post(webId, uri, contentType, body, bundle, nullableStringBridge(bridge))
    }

    /**
     * Creates a member inside [containerUri] by POSTing, letting the **server** allocate the
     * child URI — and therefore needing only **Append**, not Write.
     *
     * Prefer this over [create] when writing into a container you were granted *Add* access
     * to: [create] PUTs to a fixed URI and needs Write, so it would be refused.
     *
     * @return the server-allocated `Location`, or `null` if it returned none.
     */
    public suspend fun <T : SolidResource> createInContainer(
        webId: String,
        containerUri: String,
        resource: T,
    ): String? = call { service, bridge ->
        when (resource) {
            is SolidRDFResource ->
                service.createInContainerRdf(webId, containerUri, resource, nullableStringBridge(bridge))

            is SolidNonRDFResource ->
                service.createInContainer(webId, containerUri, resource, nullableStringBridge(bridge))

            else -> bridge.onFailure(
                IllegalArgumentException("Resource must be a SolidRDFResource or SolidNonRDFResource.")
            )
        }
    }

    /**
     * Opens the resource at [uri] as a live stream.
     *
     * The caller **must** close the returned [SolidStream] — use it in a `use { }` block.
     */
    public suspend fun readStream(webId: String, uri: String): SolidStream =
        call { service, bridge ->
            service.readStream(webId, uri, envelopeBridge(bridge) { envelope ->
                SolidStream(
                    envelope.streamContentType(),
                    envelope.streamContentLength(),
                    requireNotNull(envelope.parcelable(ParcelFileDescriptor::class.java)) {
                        "the service opened a stream but sent no file descriptor"
                    },
                )
            })
        }

    /**
     * Writes the resource at [uri] by streaming [source] across, rather than buffering it.
     *
     * [source] is read once and closed by this call. Pass [contentLength] when you know it
     * (`-1` otherwise). [ifMatch] applies the usual conditional-write precondition.
     */
    public suspend fun writeStream(
        webId: String,
        uri: String,
        contentType: String,
        source: InputStream,
        contentLength: Long = -1L,
        ifMatch: String? = null,
    ): Unit = call { service, bridge ->
        val pipe = ParcelFileDescriptor.createPipe()
        val readEnd = pipe[0]
        val writeEnd = pipe[1]

        Thread {
            runCatching {
                ParcelFileDescriptor.AutoCloseOutputStream(writeEnd).use { sink ->
                    source.use { it.copyTo(sink) }
                }
            }.onFailure { t ->
                runCatching { writeEnd.closeWithError(t.message ?: "upload failed") }
            }
        }.apply { isDaemon = true }.start()

        readEnd.use { fd ->
            service.writeStream(
                webId,
                uri,
                contentType,
                contentLength,
                fd,
                ifMatch,
                unitBridge(bridge),
            )
        }
    }

    private fun <T : Parcelable> requiredBridge(bridge: CallbackBridge<T>, expected: Class<T>) =
        envelopeBridge(bridge) { envelope ->
            requireNotNull(envelope.parcelable(expected)) {
                "the service answered with no ${expected.simpleName}"
            }
        }

    private fun <T : SolidResource> rdfCallback(
        bridge: CallbackBridge<T>,
        reconstruct: (SolidRDFResource) -> T,
    ) = envelopeBridge(bridge) { envelope ->
        reconstruct(
            requireNotNull(envelope.parcelable(SolidRDFResource::class.java)) {
                "the service answered with no RDF resource"
            },
        )
    }

    private fun <T : SolidResource> nonRdfCallback(
        bridge: CallbackBridge<T>,
        reconstruct: (SolidNonRDFResource) -> T,
    ) = envelopeBridge(bridge) { envelope ->
        reconstruct(
            requireNotNull(envelope.parcelable(SolidNonRDFResource::class.java)) {
                "the service answered with no binary resource"
            },
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : SolidResource> reconstructRdf(source: SolidRDFResource, clazz: Class<T>): T {
        if (clazz.isInstance(source)) return source as T
        return clazz.getConstructor(
            String::class.java, String::class.java, List::class.java, SolidHeaders::class.java
        ).newInstance(
            source.getIdentifier(),
            source.getContentType(),
            source.getAllQuads(),
            source.getHeaders()
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : SolidResource> reconstructNonRdf(source: SolidNonRDFResource, clazz: Class<T>): T {
        if (clazz.isInstance(source)) return source as T
        return clazz.getConstructor(
            String::class.java, String::class.java, InputStream::class.java, SolidHeaders::class.java
        ).newInstance(
            source.getIdentifier(), source.getContentType(), source.getEntity(), source.getHeaders()
        )
    }
}
