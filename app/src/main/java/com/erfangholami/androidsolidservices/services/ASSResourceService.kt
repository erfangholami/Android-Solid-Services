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
import com.erfangholami.androidsolidservices.services.dispatch.deliverError
import com.erfangholami.androidsolidservices.services.dispatch.deliverResult
import com.erfangholami.androidsolidservices.services.dispatch.deliverSafely
import com.erfangholami.androidsolidservices.services.dispatch.dispatchAcknowledged
import com.erfangholami.androidsolidservices.services.dispatch.dispatchAnswering
import com.erfangholami.androidsolidservices.services.dispatch.dispatchBoolean
import com.erfangholami.androidsolidservices.services.dispatch.dispatchParcelable
import com.erfangholami.androidsolidservices.services.dispatch.dispatchParcelableList
import com.erfangholami.androidsolidservices.services.dispatch.dispatchString
import com.erfangholami.androidsolidservices.services.dispatch.handle
import com.erfangholami.androidsolidservices.shared.IASSParcelableCallback
import com.erfangholami.androidsolidservices.shared.IASSParcelableListCallback
import com.erfangholami.androidsolidservices.shared.IASSResourceService
import com.erfangholami.androidsolidservices.shared.ipc.IpcEnvelope
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.result.ExceptionsErrorCode
import com.erfangholami.androidsolidservices.shared.result.ExceptionsErrorCode.NULL_WEBID
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
                is AccessCheck.Denied ->
                    deliverSafely("onError") { onError(check.code, check.message) }
            }
        }

        override fun getWebId(webId: String, callback: IASSParcelableCallback) {
            guard(webId, callback::onError) {
                val profileWebId = authRepository.getProfile(webId).webId
                if (profileWebId != null) {
                    callback.deliverResult(IpcEnvelope.of(profileWebId))
                } else {
                    callback.deliverError(NULL_WEBID, "WebID is null.")
                }
            }
        }

        override fun head(webId: String, resourceUrl: String, callback: IASSParcelableCallback) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchParcelable(ioDispatcher, callback) {
                    resourceManager.head(webId, resourceUrl)
                }
            }
        }

        override fun readContainer(webId: String, containerUrl: String, callback: IASSParcelableCallback) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchParcelable(ioDispatcher, callback) {
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
            callback: IASSParcelableCallback
        ) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchAnswering(ioDispatcher, callback, resource) {
                    resourceManager.create(webId, resource)
                }
            }
        }

        override fun createRdf(webId: String, resource: SolidRDFResource, callback: IASSParcelableCallback) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchAnswering(ioDispatcher, callback, resource) {
                    resourceManager.create(webId, resource)
                }
            }
        }

        override fun read(webId: String, resourceUrl: String, callback: IASSParcelableCallback) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchParcelable(ioDispatcher, callback) {
                    resourceManager.read(
                        webId,
                        resourceUrl,
                        SolidNonRDFResource::class.java
                    )
                }
            }
        }

        override fun readRdf(webId: String, resourceUrl: String, callback: IASSParcelableCallback) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchParcelable(ioDispatcher, callback) {
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
            callback: IASSParcelableCallback,
        ) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchAnswering(ioDispatcher, callback, resource) {
                    resourceManager.update(webId, resource, ifMatch)
                }
            }
        }

        override fun updateRdf(
            webId: String,
            resource: SolidRDFResource,
            ifMatch: String?,
            callback: IASSParcelableCallback,
        ) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchAnswering(ioDispatcher, callback, resource) {
                    resourceManager.update(webId, resource, ifMatch)
                }
            }
        }

        override fun patch(webId: String, resourceUrl: String, patchBody: String, callback: IASSParcelableCallback) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchAcknowledged(ioDispatcher, callback) {
                    resourceManager.patchRaw(webId, resourceUrl, patchBody)
                }
            }
        }

        override fun delete(
            webId: String,
            resource: SolidNonRDFResource,
            callback: IASSParcelableCallback
        ) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchAnswering(ioDispatcher, callback, resource) {
                    resourceManager.delete(webId, resource)
                }
            }
        }

        override fun deleteRdf(webId: String, resource: SolidRDFResource, callback: IASSParcelableCallback) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchAnswering(ioDispatcher, callback, resource) {
                    resourceManager.delete(webId, resource)
                }
            }
        }

        override fun deleteContainer(webId: String, containerUrl: String, callback: IASSParcelableCallback) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchAcknowledged(ioDispatcher, callback) {
                    resourceManager.delete(webId, containerUrl)
                }
            }
        }

        override fun exists(webId: String, uri: String, callback: IASSParcelableCallback) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchBoolean(ioDispatcher, callback) {
                    resourceManager.exists(webId, uri)
                }
            }
        }

        override fun ensureContainer(
            webId: String,
            containerUri: String,
            callback: IASSParcelableCallback,
        ) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchAcknowledged(ioDispatcher, callback) {
                    resourceManager.ensureContainer(webId, containerUri)
                }
            }
        }

        override fun probeAccess(webId: String, uri: String, callback: IASSParcelableCallback) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchParcelable(ioDispatcher, callback) {
                    resourceManager.probeAccess(webId, uri)
                }
            }
        }

        override fun listContainer(
            webId: String,
            containerUri: String,
            enrichWithHead: Boolean,
            callback: IASSParcelableListCallback,
        ) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchParcelableList(ioDispatcher, callback) {
                    resourceManager.listContainer(webId, containerUri, enrichWithHead)
                }
            }
        }

        override fun copy(
            webId: String,
            sourceUri: String,
            destinationUri: String,
            callback: IASSParcelableCallback,
        ) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchString(ioDispatcher, callback) {
                    resourceManager.copy(webId, sourceUri, destinationUri)
                }
            }
        }

        override fun move(
            webId: String,
            sourceUri: String,
            destinationUri: String,
            callback: IASSParcelableCallback,
        ) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchString(ioDispatcher, callback) {
                    resourceManager.move(webId, sourceUri, destinationUri)
                }
            }
        }

        override fun rename(
            webId: String,
            sourceUri: String,
            newName: String,
            callback: IASSParcelableCallback,
        ) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchString(ioDispatcher, callback) {
                    resourceManager.rename(webId, sourceUri, newName)
                }
            }
        }

        override fun readPublicRdf(uri: String, callback: IASSParcelableCallback) {
            lifecycleScope.dispatchParcelable(ioDispatcher, callback) {
                resourceManager.readPublic(uri, SolidRDFResource::class.java)
            }
        }

        override fun readPublic(uri: String, callback: IASSParcelableCallback) {
            lifecycleScope.dispatchParcelable(ioDispatcher, callback) {
                resourceManager.readPublic(uri, SolidNonRDFResource::class.java)
            }
        }

        override fun headPublic(uri: String, callback: IASSParcelableCallback) {
            lifecycleScope.dispatchParcelable(ioDispatcher, callback) {
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
            guard(webId, callback::onError) {
                lifecycleScope.dispatchAcknowledged(ioDispatcher, callback) {
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
            callback: IASSParcelableCallback,
        ) {
            guard(webId, callback::onError) {
                val headers = additionalHeaders
                    ?.let { bundle ->
                        bundle.keySet().associateWith { key -> bundle.getString(key).orEmpty() }
                    }
                    ?: emptyMap()
                lifecycleScope.dispatchString(ioDispatcher, callback) {
                    resourceManager.post(webId, uri, contentType, body, headers)
                }
            }
        }

        override fun createInContainer(
            webId: String,
            containerUri: String,
            resource: SolidNonRDFResource,
            callback: IASSParcelableCallback,
        ) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchString(ioDispatcher, callback) {
                    resourceManager.createInContainer(webId, containerUri, resource)
                }
            }
        }

        override fun createInContainerRdf(
            webId: String,
            containerUri: String,
            resource: SolidRDFResource,
            callback: IASSParcelableCallback,
        ) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchString(ioDispatcher, callback) {
                    resourceManager.createInContainer(webId, containerUri, resource)
                }
            }
        }

        override fun readStream(webId: String, uri: String, callback: IASSParcelableCallback) {
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

                            readEnd.use {
                                callback.deliverResult(
                                    IpcEnvelope.ofStream(it, body.contentType, body.contentLength),
                                )
                            }

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
            callback: IASSParcelableCallback,
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
                        ).handle({ callback.deliverResult(IpcEnvelope.empty()) }, callback::deliverError)
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        callback.deliverError(
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
