package com.erfangholami.androidsolidservices.host.binder

import android.os.Bundle
import android.os.ParcelFileDescriptor
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.host.HostSession
import com.erfangholami.androidsolidservices.host.access.AccessCheck
import com.erfangholami.androidsolidservices.host.access.AccessGuard
import com.erfangholami.androidsolidservices.host.access.Requirement
import com.erfangholami.androidsolidservices.host.access.VerbAccess
import com.erfangholami.androidsolidservices.host.access.VerbTarget
import com.erfangholami.androidsolidservices.host.dispatch.deliverError
import com.erfangholami.androidsolidservices.host.dispatch.deliverResult
import com.erfangholami.androidsolidservices.host.dispatch.dispatchAcknowledged
import com.erfangholami.androidsolidservices.host.dispatch.dispatchAnswering
import com.erfangholami.androidsolidservices.host.dispatch.dispatchBoolean
import com.erfangholami.androidsolidservices.host.dispatch.dispatchParcelable
import com.erfangholami.androidsolidservices.host.dispatch.dispatchParcelableList
import com.erfangholami.androidsolidservices.host.dispatch.dispatchString
import com.erfangholami.androidsolidservices.host.dispatch.handle
import com.erfangholami.androidsolidservices.shared.IASSParcelableCallback
import com.erfangholami.androidsolidservices.shared.IASSParcelableListCallback
import com.erfangholami.androidsolidservices.shared.IASSResourceService
import com.erfangholami.androidsolidservices.shared.ipc.IpcEnvelope
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.result.ExceptionsErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * The `IASSResourceService` binder: the full resource surface, each verb guarded on the
 * resource it names at the level [VerbAccess] gives it.
 *
 * The two derived container verbs `copy` and `move` carry two requirements, one per end. The
 * three public reads carry none: they send no credentials and fetch what any app could fetch
 * itself. Streaming never parcels a body: a read pipes the bytes across, and a write spools
 * them into [spoolDirectory] first because the HTTP request may legitimately be re-sent.
 */
public class ResourceBinder(
    private val resourceManager: SolidResourceManager,
    private val session: HostSession,
    private val guard: AccessGuard,
    private val scope: CoroutineScope,
    private val spoolDirectory: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : IASSResourceService.Stub() {

    private fun read(webId: String, uri: String) = guard.gate(webId, VerbTarget.Resource(uri), VerbAccess.READ)

    private fun append(webId: String, uri: String) = guard.gate(webId, VerbTarget.Resource(uri), VerbAccess.APPEND)

    private fun write(webId: String, uri: String) = guard.gate(webId, VerbTarget.Resource(uri), VerbAccess.WRITE)

    override fun getWebId(webId: String, callback: IASSParcelableCallback) {
        val gate = guard.gate(webId, VerbTarget.AnyEntry, VerbAccess.READ)
        scope.launch(dispatcher) {
            when (val check = gate()) {
                is AccessCheck.Denied -> callback.deliverError(check.code, check.message)
                AccessCheck.Allowed -> {
                    val document = session.profileDocument(webId)
                    if (document != null) {
                        callback.deliverResult(IpcEnvelope.of(document))
                    } else {
                        callback.deliverError(ExceptionsErrorCode.NULL_WEBID, "WebID is null.")
                    }
                }
            }
        }
    }

    override fun head(webId: String, resourceUrl: String, callback: IASSParcelableCallback) {
        scope.dispatchParcelable(dispatcher, callback, read(webId, resourceUrl)) {
            resourceManager.head(webId, resourceUrl)
        }
    }

    override fun readContainer(webId: String, containerUrl: String, callback: IASSParcelableCallback) {
        scope.dispatchParcelable(dispatcher, callback, read(webId, containerUrl)) {
            resourceManager.read(webId, containerUrl, SolidContainer::class.java)
        }
    }

    override fun create(webId: String, resource: SolidNonRDFResource, callback: IASSParcelableCallback) {
        scope.dispatchAnswering(dispatcher, callback, resource, write(webId, resource.getIdentifier())) {
            resourceManager.create(webId, resource)
        }
    }

    override fun createRdf(webId: String, resource: SolidRDFResource, callback: IASSParcelableCallback) {
        scope.dispatchAnswering(dispatcher, callback, resource, write(webId, resource.getIdentifier())) {
            resourceManager.create(webId, resource)
        }
    }

    override fun read(webId: String, resourceUrl: String, callback: IASSParcelableCallback) {
        scope.dispatchParcelable(dispatcher, callback, read(webId, resourceUrl)) {
            resourceManager.read(webId, resourceUrl, SolidNonRDFResource::class.java)
        }
    }

    override fun readRdf(webId: String, resourceUrl: String, callback: IASSParcelableCallback) {
        scope.dispatchParcelable(dispatcher, callback, read(webId, resourceUrl)) {
            resourceManager.read(webId, resourceUrl, SolidRDFResource::class.java)
        }
    }

    override fun update(
        webId: String,
        resource: SolidNonRDFResource,
        ifMatch: String?,
        callback: IASSParcelableCallback,
    ) {
        scope.dispatchAnswering(dispatcher, callback, resource, write(webId, resource.getIdentifier())) {
            resourceManager.update(webId, resource, ifMatch)
        }
    }

    override fun updateRdf(
        webId: String,
        resource: SolidRDFResource,
        ifMatch: String?,
        callback: IASSParcelableCallback,
    ) {
        scope.dispatchAnswering(dispatcher, callback, resource, write(webId, resource.getIdentifier())) {
            resourceManager.update(webId, resource, ifMatch)
        }
    }

    override fun patch(webId: String, resourceUrl: String, patchBody: String, callback: IASSParcelableCallback) {
        scope.dispatchAcknowledged(dispatcher, callback, write(webId, resourceUrl)) {
            resourceManager.patchRaw(webId, resourceUrl, patchBody)
        }
    }

    override fun delete(webId: String, resource: SolidNonRDFResource, callback: IASSParcelableCallback) {
        scope.dispatchAnswering(dispatcher, callback, resource, write(webId, resource.getIdentifier())) {
            resourceManager.delete(webId, resource)
        }
    }

    override fun deleteRdf(webId: String, resource: SolidRDFResource, callback: IASSParcelableCallback) {
        scope.dispatchAnswering(dispatcher, callback, resource, write(webId, resource.getIdentifier())) {
            resourceManager.delete(webId, resource)
        }
    }

    override fun deleteContainer(webId: String, containerUrl: String, callback: IASSParcelableCallback) {
        scope.dispatchAcknowledged(dispatcher, callback, write(webId, containerUrl)) {
            resourceManager.delete(webId, containerUrl)
        }
    }

    override fun exists(webId: String, uri: String, callback: IASSParcelableCallback) {
        scope.dispatchBoolean(dispatcher, callback, read(webId, uri)) {
            resourceManager.exists(webId, uri)
        }
    }

    override fun ensureContainer(webId: String, containerUri: String, callback: IASSParcelableCallback) {
        scope.dispatchAcknowledged(dispatcher, callback, write(webId, containerUri)) {
            resourceManager.ensureContainer(webId, containerUri)
        }
    }

    override fun probeAccess(webId: String, uri: String, callback: IASSParcelableCallback) {
        scope.dispatchParcelable(dispatcher, callback, read(webId, uri)) {
            resourceManager.probeAccess(webId, uri)
        }
    }

    override fun listContainer(
        webId: String,
        containerUri: String,
        enrichWithHead: Boolean,
        callback: IASSParcelableListCallback,
    ) {
        scope.dispatchParcelableList(dispatcher, callback, read(webId, containerUri)) {
            resourceManager.listContainer(webId, containerUri, enrichWithHead)
        }
    }

    override fun copy(webId: String, sourceUri: String, destinationUri: String, callback: IASSParcelableCallback) {
        val gate = guard.gate(
            webId,
            Requirement(VerbTarget.Resource(sourceUri), VerbAccess.READ),
            Requirement(VerbTarget.Resource(destinationUri), VerbAccess.WRITE),
        )
        scope.dispatchString(dispatcher, callback, gate) {
            resourceManager.copy(webId, sourceUri, destinationUri)
        }
    }

    override fun move(webId: String, sourceUri: String, destinationUri: String, callback: IASSParcelableCallback) {
        val gate = guard.gate(
            webId,
            Requirement(VerbTarget.Resource(sourceUri), VerbAccess.WRITE),
            Requirement(VerbTarget.Resource(destinationUri), VerbAccess.WRITE),
        )
        scope.dispatchString(dispatcher, callback, gate) {
            resourceManager.move(webId, sourceUri, destinationUri)
        }
    }

    override fun rename(webId: String, sourceUri: String, newName: String, callback: IASSParcelableCallback) {
        scope.dispatchString(dispatcher, callback, write(webId, sourceUri)) {
            resourceManager.rename(webId, sourceUri, newName)
        }
    }

    override fun readPublicRdf(uri: String, callback: IASSParcelableCallback) {
        scope.dispatchParcelable(dispatcher, callback) {
            resourceManager.readPublic(uri, SolidRDFResource::class.java)
        }
    }

    override fun readPublic(uri: String, callback: IASSParcelableCallback) {
        scope.dispatchParcelable(dispatcher, callback) {
            resourceManager.readPublic(uri, SolidNonRDFResource::class.java)
        }
    }

    override fun headPublic(uri: String, callback: IASSParcelableCallback) {
        scope.dispatchParcelable(dispatcher, callback) {
            resourceManager.headPublic(uri)
        }
    }

    override fun putRaw(
        webId: String,
        uri: String,
        contentType: String,
        body: ByteArray,
        ifMatch: String?,
        linkHeader: String?,
        callback: IASSParcelableCallback,
    ) {
        scope.dispatchAcknowledged(dispatcher, callback, write(webId, uri)) {
            resourceManager.putRaw(webId, uri, contentType, body, ifMatch, linkHeader)
        }
    }

    override fun post(
        webId: String,
        uri: String,
        contentType: String,
        body: ByteArray,
        additionalHeaders: Bundle?,
        callback: IASSParcelableCallback,
    ) {
        val headers = additionalHeaders
            ?.let { bundle -> bundle.keySet().associateWith { key -> bundle.getString(key).orEmpty() } }
            ?: emptyMap()
        scope.dispatchString(dispatcher, callback, append(webId, uri)) {
            resourceManager.post(webId, uri, contentType, body, headers)
        }
    }

    override fun createInContainer(
        webId: String,
        containerUri: String,
        resource: SolidNonRDFResource,
        callback: IASSParcelableCallback,
    ) {
        scope.dispatchString(dispatcher, callback, append(webId, containerUri)) {
            resourceManager.createInContainer(webId, containerUri, resource)
        }
    }

    override fun createInContainerRdf(
        webId: String,
        containerUri: String,
        resource: SolidRDFResource,
        callback: IASSParcelableCallback,
    ) {
        scope.dispatchString(dispatcher, callback, append(webId, containerUri)) {
            resourceManager.createInContainer(webId, containerUri, resource)
        }
    }

    override fun readStream(webId: String, uri: String, callback: IASSParcelableCallback) {
        val gate = read(webId, uri)
        scope.launch(dispatcher) {
            when (val check = gate()) {
                is AccessCheck.Denied -> callback.deliverError(check.code, check.message)
                AccessCheck.Allowed -> pipeStream(webId, uri, callback)
            }
        }
    }

    private suspend fun pipeStream(webId: String, uri: String, callback: IASSParcelableCallback) {
        when (val opened = resourceManager.readStream(webId, uri)) {
            is SolidResult.Failure -> opened.handle({}, callback::deliverError)

            is SolidResult.Success -> {
                val body = opened.value
                val pipe = ParcelFileDescriptor.createPipe()
                val readEnd = pipe[0]
                val writeEnd = pipe[1]

                readEnd.use {
                    callback.deliverResult(IpcEnvelope.ofStream(it, body.contentType, body.contentLength))
                }

                scope.launch(dispatcher) {
                    runCatching {
                        ParcelFileDescriptor.AutoCloseOutputStream(writeEnd).use { sink ->
                            body.use { it.stream().copyTo(sink) }
                        }
                    }.onFailure { t ->
                        runCatching { writeEnd.closeWithError(t.message ?: "stream failed") }
                    }
                }
            }
        }
    }

    override fun writeStream(
        webId: String,
        uri: String,
        contentType: String,
        contentLength: Long,
        source: ParcelFileDescriptor,
        ifMatch: String?,
        callback: IASSParcelableCallback,
    ) {
        val gate = write(webId, uri)
        scope.launch(dispatcher) {
            when (val check = gate()) {
                is AccessCheck.Denied -> {
                    runCatching { source.close() }
                    callback.deliverError(check.code, check.message)
                }

                AccessCheck.Allowed -> spoolAndUpload(webId, uri, contentType, contentLength, source, ifMatch, callback)
            }
        }
    }

    private suspend fun spoolAndUpload(
        webId: String,
        uri: String,
        contentType: String,
        contentLength: Long,
        source: ParcelFileDescriptor,
        ifMatch: String?,
        callback: IASSParcelableCallback,
    ) {
        val spool = File.createTempFile("host-upload-", null, spoolDirectory)
        try {
            ParcelFileDescriptor.AutoCloseInputStream(source).use { incoming ->
                spool.outputStream().use { out -> incoming.copyTo(out) }
            }
            val length = if (contentLength >= 0) contentLength else spool.length()
            resourceManager.writeStream(
                webId = webId,
                uri = uri,
                contentType = contentType,
                contentLength = length,
                ifMatch = ifMatch,
                openSource = { spool.inputStream() },
            ).handle({ callback.deliverResult(IpcEnvelope.empty()) }, callback::deliverError)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            callback.deliverError(ExceptionsErrorCode.UNKNOWN, t.message ?: t.toString())
        } finally {
            spool.delete()
        }
    }
}
