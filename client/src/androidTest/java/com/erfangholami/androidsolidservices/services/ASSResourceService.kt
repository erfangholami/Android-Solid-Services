package com.erfangholami.androidsolidservices.services

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.erfangholami.androidsolidservices.client.internal.fakes.CallLog
import com.erfangholami.androidsolidservices.client.internal.fakes.Fixtures
import com.erfangholami.androidsolidservices.shared.IASSParcelableCallback
import com.erfangholami.androidsolidservices.shared.IASSParcelableListCallback
import com.erfangholami.androidsolidservices.shared.IASSResourceService
import com.erfangholami.androidsolidservices.shared.error.ExceptionsErrorCode
import com.erfangholami.androidsolidservices.shared.ipc.IpcEnvelope
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource

/**
 * Stands in for the ASS app's resource service, hosted in `:fakeass`.
 *
 * It carries the **production fully-qualified name** on purpose: `ServiceConnector` builds its
 * Intent from a fixed class name and only the package is redirectable, so a fake is reachable
 * only if it answers to the same FQCN.
 *
 * Every method records its arguments through [CallLog] before answering, so a test can assert that
 * what the SDK sent is what arrived — the failure mode a returned value cannot reveal. Passing
 * [Fixtures.FAILING_WEB_ID] as the WebID switches any method to its error path.
 */
class ASSResourceService : Service() {

    private val binder = object : IASSResourceService.Stub() {

        override fun getWebId(webId: String?, callback: IASSParcelableCallback?) {
            record("getWebId", "webId" to webId)
            callback.rdf(webId) { Fixtures.rdfResource(Fixtures.WEB_ID) }
        }

        override fun head(
            webId: String?,
            resourceUrl: String?,
            callback: IASSParcelableCallback?,
        ) {
            record("head", "webId" to webId, "resourceUrl" to resourceUrl)
            callback.metadata(webId)
        }

        override fun create(
            webId: String?,
            resource: SolidNonRDFResource?,
            callback: IASSParcelableCallback?,
        ) {
            record("create", "webId" to webId, "resource" to resource.describe())
            callback.nonRdf(webId)
        }

        override fun createRdf(
            webId: String?,
            resource: SolidRDFResource?,
            callback: IASSParcelableCallback?,
        ) {
            record("createRdf", "webId" to webId, "resource" to resource.describe())
            callback.rdf(webId)
        }

        override fun read(
            webId: String?,
            resourceUrl: String?,
            callback: IASSParcelableCallback?,
        ) {
            record("read", "webId" to webId, "resourceUrl" to resourceUrl)
            callback.nonRdf(webId, resourceUrl)
        }

        override fun readRdf(
            webId: String?,
            resourceUrl: String?,
            callback: IASSParcelableCallback?,
        ) {
            record("readRdf", "webId" to webId, "resourceUrl" to resourceUrl)
            callback.rdf(webId) { Fixtures.rdfResource(resourceUrl ?: Fixtures.RESOURCE) }
        }

        override fun readContainer(
            webId: String?,
            containerUrl: String?,
            callback: IASSParcelableCallback?,
        ) {
            record("readContainer", "webId" to webId, "containerUrl" to containerUrl)
            if (failing(webId)) callback?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
            else callback?.onResult(IpcEnvelope.of(Fixtures.container()))
        }

        override fun update(
            webId: String?,
            resource: SolidNonRDFResource?,
            ifMatch: String?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "update",
                "webId" to webId,
                "resource" to resource.describe(),
                "ifMatch" to ifMatch,
            )
            callback.nonRdf(webId)
        }

        override fun updateRdf(
            webId: String?,
            resource: SolidRDFResource?,
            ifMatch: String?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "updateRdf",
                "webId" to webId,
                "resource" to resource.describe(),
                "ifMatch" to ifMatch,
            )
            callback.rdf(webId)
        }

        override fun patch(
            webId: String?,
            resourceUrl: String?,
            patchBody: String?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "patch",
                "webId" to webId,
                "resourceUrl" to resourceUrl,
                "patchBody" to patchBody,
            )
            callback.unit(webId)
        }

        override fun delete(
            webId: String?,
            resource: SolidNonRDFResource?,
            callback: IASSParcelableCallback?,
        ) {
            record("delete", "webId" to webId, "resource" to resource.describe())
            callback.nonRdf(webId)
        }

        override fun deleteRdf(
            webId: String?,
            resource: SolidRDFResource?,
            callback: IASSParcelableCallback?,
        ) {
            record("deleteRdf", "webId" to webId, "resource" to resource.describe())
            callback.rdf(webId)
        }

        override fun deleteContainer(
            webId: String?,
            containerUrl: String?,
            callback: IASSParcelableCallback?,
        ) {
            record("deleteContainer", "webId" to webId, "containerUrl" to containerUrl)
            callback.unit(webId)
        }

        override fun exists(webId: String?, uri: String?, callback: IASSParcelableCallback?) {
            record("exists", "webId" to webId, "uri" to uri)
            if (failing(webId)) callback?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
            else callback?.onResult(IpcEnvelope.ofBoolean(true))
        }

        override fun ensureContainer(
            webId: String?,
            containerUri: String?,
            callback: IASSParcelableCallback?,
        ) {
            record("ensureContainer", "webId" to webId, "containerUri" to containerUri)
            callback.unit(webId)
        }

        override fun probeAccess(
            webId: String?,
            uri: String?,
            callback: IASSParcelableCallback?,
        ) {
            record("probeAccess", "webId" to webId, "uri" to uri)
            if (failing(webId)) callback?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
            else callback?.onResult(IpcEnvelope.of(Fixtures.ACCESS_PROBE))
        }

        override fun listContainer(
            webId: String?,
            containerUri: String?,
            enrichWithHead: Boolean,
            callback: IASSParcelableListCallback?,
        ) {
            record(
                "listContainer",
                "webId" to webId,
                "containerUri" to containerUri,
                "enrichWithHead" to enrichWithHead,
            )
            if (failing(webId)) callback?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
            else callback?.onResult(IpcEnvelope.ofList(Fixtures.SOURCE_REFERENCES))
        }

        override fun copy(
            webId: String?,
            sourceUri: String?,
            destinationUri: String?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "copy",
                "webId" to webId,
                "sourceUri" to sourceUri,
                "destinationUri" to destinationUri,
            )
            callback.string(webId, destinationUri)
        }

        override fun move(
            webId: String?,
            sourceUri: String?,
            destinationUri: String?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "move",
                "webId" to webId,
                "sourceUri" to sourceUri,
                "destinationUri" to destinationUri,
            )
            callback.string(webId, destinationUri)
        }

        override fun rename(
            webId: String?,
            sourceUri: String?,
            newName: String?,
            callback: IASSParcelableCallback?,
        ) {
            record("rename", "webId" to webId, "sourceUri" to sourceUri, "newName" to newName)
            callback.string(webId, Fixtures.DESTINATION)
        }

        override fun readPublicRdf(uri: String?, callback: IASSParcelableCallback?) {
            record("readPublicRdf", "uri" to uri)
            callback.rdf(null) { Fixtures.rdfResource(uri ?: Fixtures.RESOURCE) }
        }

        override fun readPublic(uri: String?, callback: IASSParcelableCallback?) {
            record("readPublic", "uri" to uri)
            callback.nonRdf(null, uri)
        }

        override fun headPublic(uri: String?, callback: IASSParcelableCallback?) {
            record("headPublic", "uri" to uri)
            callback.metadata(null)
        }

        override fun putRaw(
            webId: String?,
            uri: String?,
            contentType: String?,
            body: ByteArray?,
            ifMatch: String?,
            linkHeader: String?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "putRaw",
                "webId" to webId,
                "uri" to uri,
                "contentType" to contentType,
                "body" to body,
                "ifMatch" to ifMatch,
                "linkHeader" to linkHeader,
            )
            callback.unit(webId)
        }

        override fun post(
            webId: String?,
            uri: String?,
            contentType: String?,
            body: ByteArray?,
            additionalHeaders: Bundle?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "post",
                "webId" to webId,
                "uri" to uri,
                "contentType" to contentType,
                "body" to body,
                "additionalHeaders" to additionalHeaders,
            )
            callback.string(webId, Fixtures.RESOURCE)
        }

        override fun createInContainer(
            webId: String?,
            containerUri: String?,
            resource: SolidNonRDFResource?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "createInContainer",
                "webId" to webId,
                "containerUri" to containerUri,
                "resource" to resource.describe(),
            )
            callback.string(webId, Fixtures.BINARY)
        }

        override fun createInContainerRdf(
            webId: String?,
            containerUri: String?,
            resource: SolidRDFResource?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "createInContainerRdf",
                "webId" to webId,
                "containerUri" to containerUri,
                "resource" to resource.describe(),
            )
            callback.string(webId, Fixtures.RESOURCE)
        }

        override fun readStream(webId: String?, uri: String?, callback: IASSParcelableCallback?) {
            record("readStream", "webId" to webId, "uri" to uri)
            if (failing(webId)) {
                callback?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
                return
            }
            val payload = streamPayload(uri)
            val pipe = ParcelFileDescriptor.createPipe()
            Thread {
                runCatching {
                    ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write(payload) }
                }
            }.apply { isDaemon = true }.start()

            pipe[0].use {
                callback?.onResult(
                    IpcEnvelope.ofStream(it, "application/octet-stream", payload.size.toLong()),
                )
            }
        }

        override fun writeStream(
            webId: String?,
            uri: String?,
            contentType: String?,
            contentLength: Long,
            source: ParcelFileDescriptor?,
            ifMatch: String?,
            callback: IASSParcelableCallback?,
        ) {
            val received = source?.let {
                ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
            } ?: ByteArray(0)

            record(
                "writeStream",
                "webId" to webId,
                "uri" to uri,
                "contentType" to contentType,
                "contentLength" to contentLength,
                "ifMatch" to ifMatch,
                "body" to received,
            )
            callback.unit(webId)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun record(method: String, vararg args: Pair<String, Any?>) =
        CallLog.record(applicationContext, method, *args)

    private fun failing(webId: String?) = webId == Fixtures.FAILING_WEB_ID

    private fun IASSParcelableCallback?.rdf(
        webId: String?,
        value: () -> SolidRDFResource = { Fixtures.rdfResource() },
    ) {
        if (failing(webId)) this?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
        else this?.onResult(IpcEnvelope.of(value()))
    }

    private fun IASSParcelableCallback?.nonRdf(webId: String?, identifier: String? = null) {
        if (failing(webId)) this?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
        else this?.onResult(IpcEnvelope.of(Fixtures.nonRdfResource(identifier ?: Fixtures.BINARY)))
    }

    private fun IASSParcelableCallback?.metadata(webId: String?) {
        if (failing(webId)) this?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
        else this?.onResult(IpcEnvelope.of(Fixtures.METADATA))
    }

    private fun IASSParcelableCallback?.unit(webId: String?) {
        if (failing(webId)) this?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
        else this?.onResult(IpcEnvelope.empty())
    }

    private fun IASSParcelableCallback?.string(webId: String?, value: String?) {
        if (failing(webId)) this?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
        else this?.onResult(IpcEnvelope.ofString(value))
    }

    /**
     * Resources arrive as opaque parcels; this pulls out the fields a test can meaningfully assert
     * on. `getEntity()` is deliberately not read — it is a one-shot stream.
     */
    private fun SolidRDFResource?.describe(): String =
        this?.let { "${it.getIdentifier()}|${it.getContentType()}|quads=${it.getAllQuads().size}" }
            ?: CallLog.NULL

    private fun SolidNonRDFResource?.describe(): String =
        this?.let { "${it.getIdentifier()}|${it.getContentType()}|bytes=${it.getEntity().readBytes().size}" }
            ?: CallLog.NULL

    companion object {
        const val ERROR_CODE: Int = ExceptionsErrorCode.NOT_PERMISSION

        /** How many bytes [readStream] pipes back, keyed off the URI so tests can pick a size. */
        const val LARGE_STREAM_URI: String = "https://alice.pod.example/blobs/large.bin"
        const val LARGE_STREAM_SIZE: Int = 5 * 1024 * 1024

        fun streamPayload(uri: String?): ByteArray =
            if (uri == LARGE_STREAM_URI) {
                ByteArray(LARGE_STREAM_SIZE) { (it % 251).toByte() }
            } else {
                Fixtures.PNG_BYTES
            }
    }
}
