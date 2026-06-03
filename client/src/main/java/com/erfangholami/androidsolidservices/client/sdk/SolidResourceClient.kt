package com.erfangholami.androidsolidservices.client.sdk

import android.content.Context
import com.erfangholami.androidsolidservices.client.internal.ANDROID_SOLID_SERVICES_CRUD_SERVICE
import com.erfangholami.androidsolidservices.client.internal.CallbackBridge
import com.erfangholami.androidsolidservices.client.internal.ServiceConnector
import com.erfangholami.androidsolidservices.shared.IASSResourceService
import com.erfangholami.androidsolidservices.shared.IASSUnitCallback
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.IASSContainerCallback
import com.erfangholami.androidsolidservices.shared.model.resource.IASSSolidMetadataCallback
import com.erfangholami.androidsolidservices.shared.model.resource.IASSSolidNonRdfResourceCallback
import com.erfangholami.androidsolidservices.shared.model.resource.IASSSolidRdfResourceCallback
import com.erfangholami.androidsolidservices.shared.model.resource.NonRDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.RDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidResource
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.net.URI

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
        private var INSTANCE: SolidResourceClient? = null

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
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SolidResourceClient(context, hasInstalledAndroidSolidServices)
                    .also { INSTANCE = it }
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
        service.head(webId, resourceUrl, object : IASSSolidMetadataCallback.Stub() {
            override fun onResult(result: SolidMetadata) = bridge.onResult(result)
            override fun onError(errorCode: Int, errorMessage: String) = bridge.onError(errorCode, errorMessage)
        })
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
    public suspend fun patch(webId: String, uri: URI, patch: N3Patch): Unit = call { service, bridge ->
        service.patch(webId, uri.toString(), patch.toN3String(), object : IASSUnitCallback.Stub() {
            override fun onResult() = bridge.onResult(Unit)
            override fun onError(errorCode: Int, errorMessage: String) = bridge.onError(errorCode, errorMessage)
        })
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
            service.readContainer(webId, containerUrl, object : IASSContainerCallback.Stub() {
                override fun onResult(result: SolidContainer) = bridge.onResult(result)
                override fun onError(errorCode: Int, errorMessage: String) = bridge.onError(errorCode, errorMessage)
            })
        }

    /**
     * Recursively deletes a container and all of its contents.
     * @param containerUri The URI of the LDP container to delete (must end with `/`).
     * @throws SolidException on failure.
     */
    public suspend fun deleteContainer(webId: String, containerUri: URI): Unit = call { service, bridge ->
        service.deleteContainer(webId, containerUri.toString(), object : IASSUnitCallback.Stub() {
            override fun onResult() = bridge.onResult(Unit)
            override fun onError(errorCode: Int, errorMessage: String) = bridge.onError(errorCode, errorMessage)
        })
    }

    /**
     * Runs [register] once the ASS app is installed and connected; the coroutine resumes with the
     * callback's result or throws the mapped [SolidException].
     */
    private suspend fun <T> call(register: (IASSResourceService, CallbackBridge<T>) -> Unit): T {
        if (!hasInstalledAndroidSolidServices()) {
            throw SolidException.SolidAppNotFoundException()
        }
        return connector.await(register)
    }

    private fun <T : SolidResource> rdfCallback(
        bridge: CallbackBridge<T>,
        reconstruct: (SolidRDFResource) -> T,
    ) = object : IASSSolidRdfResourceCallback.Stub() {
        override fun onResult(result: SolidRDFResource) {
            runCatching { reconstruct(result) }
                .onSuccess { bridge.onResult(it) }
                .onFailure { bridge.onFailure(it) }
        }

        override fun onError(errorCode: Int, errorMessage: String) = bridge.onError(errorCode, errorMessage)
    }

    private fun <T : SolidResource> nonRdfCallback(
        bridge: CallbackBridge<T>,
        reconstruct: (SolidNonRDFResource) -> T,
    ) = object : IASSSolidNonRdfResourceCallback.Stub() {
        override fun onResult(result: SolidNonRDFResource) {
            runCatching { reconstruct(result) }
                .onSuccess { bridge.onResult(it) }
                .onFailure { bridge.onFailure(it) }
        }

        override fun onError(errorCode: Int, errorMessage: String) = bridge.onError(errorCode, errorMessage)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : SolidResource> reconstructRdf(source: SolidRDFResource, clazz: Class<T>): T {
        if (clazz.isInstance(source)) return source as T
        return clazz.getConstructor(
            URI::class.java, String::class.java, List::class.java, SolidHeaders::class.java
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
            URI::class.java, String::class.java, SolidHeaders::class.java, InputStream::class.java
        ).newInstance(
            source.getIdentifier(), source.getContentType(), source.getHeaders(), source.getEntity()
        )
    }
}
