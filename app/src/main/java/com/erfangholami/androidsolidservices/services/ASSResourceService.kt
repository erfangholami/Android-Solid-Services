package com.erfangholami.androidsolidservices.services

import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.di.IoDispatcher
import com.erfangholami.androidsolidservices.domain.repository.AuthRepository
import com.erfangholami.androidsolidservices.domain.usecase.AccessCheck
import com.erfangholami.androidsolidservices.domain.usecase.CheckResourceAccessUseCase
import com.erfangholami.androidsolidservices.services.dispatch.dispatchNetwork
import com.erfangholami.androidsolidservices.services.dispatch.dispatchUnit
import com.erfangholami.androidsolidservices.services.dispatch.handle
import com.erfangholami.androidsolidservices.shared.IASSBooleanCallback
import com.erfangholami.androidsolidservices.shared.IASSResourceService
import com.erfangholami.androidsolidservices.shared.IASSStringCallback
import com.erfangholami.androidsolidservices.shared.IASSUnitCallback
import com.erfangholami.androidsolidservices.shared.error.ExceptionsErrorCode
import com.erfangholami.androidsolidservices.shared.error.ExceptionsErrorCode.NULL_WEBID
import com.erfangholami.androidsolidservices.shared.model.resource.IASSAccessProbeCallback
import com.erfangholami.androidsolidservices.shared.model.resource.IASSContainerCallback
import com.erfangholami.androidsolidservices.shared.model.resource.IASSSolidMetadataCallback
import com.erfangholami.androidsolidservices.shared.model.resource.IASSSolidNonRdfResourceCallback
import com.erfangholami.androidsolidservices.shared.model.resource.IASSSolidRdfResourceCallback
import com.erfangholami.androidsolidservices.shared.model.resource.IASSSourceReferenceListCallback
import com.erfangholami.androidsolidservices.shared.model.resource.IASSStreamCallback
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class ASSResourceService : LifecycleService() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var resourceManager: SolidResourceManager

    @Inject
    lateinit var checkResourceAccess: CheckResourceAccessUseCase

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    private val binder = object : IASSResourceService.Stub() {

        private fun guard(
            webId: String,
            onError: (Int, String) -> Unit,
            onAllowed: () -> Unit,
        ) {
            val callerPackage = packageManager.getNameForUid(getCallingUid())
            when (val check = checkResourceAccess(callerPackage, webId)) {
                AccessCheck.Allowed -> onAllowed()
                is AccessCheck.Denied -> onError(check.code, check.message)
            }
        }

        override fun getWebId(webId: String, callback: IASSSolidRdfResourceCallback) {
            guard(webId, callback::onError) {
                val profileWebId = authRepository.getProfile(webId).webId
                if (profileWebId != null) {
                    callback.onResult(profileWebId)
                } else {
                    callback.onError(NULL_WEBID, "WebID is null.")
                }
            }
        }

        override fun head(webId: String, resourceUrl: String, callback: IASSSolidMetadataCallback) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchNetwork(
                    ioDispatcher,
                    callback::onError,
                    callback::onResult
                ) {
                    resourceManager.head(webId, resourceUrl)
                }
            }
        }

        override fun readContainer(webId: String, containerUrl: String, callback: IASSContainerCallback) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchNetwork(
                    ioDispatcher,
                    callback::onError,
                    callback::onResult
                ) {
                    resourceManager.read(
                        webId,
                        containerUrl,
                        SolidContainer::class.java
                    )
                }
            }
        }

        override fun create(
            webId: String,
            resource: SolidNonRDFResource,
            callback: IASSSolidNonRdfResourceCallback
        ) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchNetwork(
                    ioDispatcher,
                    callback::onError,
                    { callback.onResult(resource) }) {
                    resourceManager.create(webId, resource)
                }
            }
        }

        override fun createRdf(webId: String, resource: SolidRDFResource, callback: IASSSolidRdfResourceCallback) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchNetwork(
                    ioDispatcher,
                    callback::onError,
                    { callback.onResult(resource) }) {
                    resourceManager.create(webId, resource)
                }
            }
        }

        override fun read(webId: String, resourceUrl: String, callback: IASSSolidNonRdfResourceCallback) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchNetwork(
                    ioDispatcher,
                    callback::onError,
                    callback::onResult
                ) {
                    resourceManager.read(
                        webId,
                        resourceUrl,
                        SolidNonRDFResource::class.java
                    )
                }
            }
        }

        override fun readRdf(webId: String, resourceUrl: String, callback: IASSSolidRdfResourceCallback) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchNetwork(
                    ioDispatcher,
                    callback::onError,
                    callback::onResult
                ) {
                    resourceManager.read(
                        webId,
                        resourceUrl,
                        SolidRDFResource::class.java
                    )
                }
            }
        }

        override fun update(
            webId: String,
            resource: SolidNonRDFResource,
            ifMatch: String?,
            callback: IASSSolidNonRdfResourceCallback,
        ) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchNetwork(
                    ioDispatcher,
                    callback::onError,
                    { callback.onResult(resource) }) {
                    resourceManager.update(webId, resource, ifMatch)
                }
            }
        }

        override fun updateRdf(
            webId: String,
            resource: SolidRDFResource,
            ifMatch: String?,
            callback: IASSSolidRdfResourceCallback,
        ) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchNetwork(
                    ioDispatcher,
                    callback::onError,
                    { callback.onResult(resource) }) {
                    resourceManager.update(webId, resource, ifMatch)
                }
            }
        }

        override fun patch(webId: String, resourceUrl: String, patchBody: String, callback: IASSUnitCallback) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchUnit(ioDispatcher, callback::onError, callback::onResult) {
                    resourceManager.patchRaw(webId, resourceUrl, patchBody)
                }
            }
        }

        override fun delete(
            webId: String,
            resource: SolidNonRDFResource,
            callback: IASSSolidNonRdfResourceCallback
        ) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchNetwork(
                    ioDispatcher,
                    callback::onError,
                    { callback.onResult(resource) }) {
                    resourceManager.delete(webId, resource)
                }
            }
        }

        override fun deleteRdf(webId: String, resource: SolidRDFResource, callback: IASSSolidRdfResourceCallback) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchNetwork(
                    ioDispatcher,
                    callback::onError,
                    { callback.onResult(resource) }) {
                    resourceManager.delete(webId, resource)
                }
            }
        }

        override fun deleteContainer(webId: String, containerUrl: String, callback: IASSUnitCallback) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchNetwork(
                    ioDispatcher,
                    callback::onError,
                    { callback.onResult() }) {
                    resourceManager.delete(webId, containerUrl)
                }
            }
        }

        override fun exists(webId: String, uri: String, callback: IASSBooleanCallback) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                    resourceManager.exists(webId, uri)
                }
            }
        }

        override fun ensureContainer(
            webId: String,
            containerUri: String,
            callback: IASSUnitCallback,
        ) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchUnit(ioDispatcher, callback::onError, { callback.onResult() }) {
                    resourceManager.ensureContainer(webId, containerUri)
                }
            }
        }

        override fun probeAccess(webId: String, uri: String, callback: IASSAccessProbeCallback) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                    resourceManager.probeAccess(webId, uri)
                }
            }
        }

        override fun listContainer(
            webId: String,
            containerUri: String,
            enrichWithHead: Boolean,
            callback: IASSSourceReferenceListCallback,
        ) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                    resourceManager.listContainer(webId, containerUri, enrichWithHead)
                }
            }
        }

        override fun copy(
            webId: String,
            sourceUri: String,
            destinationUri: String,
            callback: IASSStringCallback,
        ) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                    resourceManager.copy(webId, sourceUri, destinationUri)
                }
            }
        }

        override fun move(
            webId: String,
            sourceUri: String,
            destinationUri: String,
            callback: IASSStringCallback,
        ) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                    resourceManager.move(webId, sourceUri, destinationUri)
                }
            }
        }

        override fun rename(
            webId: String,
            sourceUri: String,
            newName: String,
            callback: IASSStringCallback,
        ) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                    resourceManager.rename(webId, sourceUri, newName)
                }
            }
        }

        override fun readPublicRdf(uri: String, callback: IASSSolidRdfResourceCallback) {
            lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                resourceManager.readPublic(uri, SolidRDFResource::class.java)
            }
        }

        override fun readPublic(uri: String, callback: IASSSolidNonRdfResourceCallback) {
            lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                resourceManager.readPublic(uri, SolidNonRDFResource::class.java)
            }
        }

        override fun headPublic(uri: String, callback: IASSSolidMetadataCallback) {
            lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
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
            callback: IASSUnitCallback,
        ) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchUnit(ioDispatcher, callback::onError, { callback.onResult() }) {
                    resourceManager.putRaw(webId, uri, contentType, body, ifMatch, linkHeader)
                }
            }
        }

        override fun post(
            webId: String,
            uri: String,
            contentType: String,
            body: ByteArray,
            additionalHeaders: Bundle?,
            callback: IASSStringCallback,
        ) {
            guard(webId, callback::onError) {
                val headers = additionalHeaders
                    ?.let { bundle ->
                        bundle.keySet().associateWith { key -> bundle.getString(key).orEmpty() }
                    }
                    ?: emptyMap()
                lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                    resourceManager.post(webId, uri, contentType, body, headers)
                }
            }
        }

        override fun createInContainer(
            webId: String,
            containerUri: String,
            resource: SolidNonRDFResource,
            callback: IASSStringCallback,
        ) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                    resourceManager.createInContainer(webId, containerUri, resource)
                }
            }
        }

        override fun createInContainerRdf(
            webId: String,
            containerUri: String,
            resource: SolidRDFResource,
            callback: IASSStringCallback,
        ) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                    resourceManager.createInContainer(webId, containerUri, resource)
                }
            }
        }

        override fun readStream(webId: String, uri: String, callback: IASSStreamCallback) {
            guard(webId, callback::onError) {
                lifecycleScope.launch(ioDispatcher) {
                    when (val opened = resourceManager.readStream(webId, uri)) {
                        is SolidResult.Failure ->
                            opened.handle({}, callback::onError)

                        is SolidResult.Success -> {
                            val body = opened.value
                            val pipe = ParcelFileDescriptor.createPipe()
                            val readEnd = pipe[0]
                            val writeEnd = pipe[1]

                            readEnd.use { callback.onResult(it, body.contentType, body.contentLength) }

                            launch(ioDispatcher) {
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
            }
        }

        override fun writeStream(
            webId: String,
            uri: String,
            contentType: String,
            contentLength: Long,
            source: ParcelFileDescriptor,
            ifMatch: String?,
            callback: IASSUnitCallback,
        ) {
            guard(webId, callback::onError) {
                lifecycleScope.launch(ioDispatcher) {
                    val spool = File.createTempFile("ass-upload-", null, cacheDir)
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
                        ).handle({ callback.onResult() }, callback::onError)
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        callback.onError(
                            ExceptionsErrorCode.UNKNOWN,
                            t.message ?: t.toString(),
                        )
                    } finally {
                        spool.delete()
                    }
                }
            }
        }
    }
}
