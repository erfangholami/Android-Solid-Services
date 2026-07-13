package com.erfangholami.androidsolidservices.services

import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.erfangholami.androidsolidservices.di.IoDispatcher
import com.erfangholami.androidsolidservices.domain.repository.AuthRepository
import com.erfangholami.androidsolidservices.domain.repository.SolidResourceRepository
import com.erfangholami.androidsolidservices.domain.usecase.AccessCheck
import com.erfangholami.androidsolidservices.domain.usecase.CheckResourceAccessUseCase
import com.erfangholami.androidsolidservices.services.dispatch.dispatchNetwork
import com.erfangholami.androidsolidservices.services.dispatch.dispatchUnit
import com.erfangholami.androidsolidservices.shared.IASSResourceService
import com.erfangholami.androidsolidservices.shared.IASSUnitCallback
import com.erfangholami.androidsolidservices.shared.error.ExceptionsErrorCode.NULL_WEBID
import com.erfangholami.androidsolidservices.shared.model.resource.IASSContainerCallback
import com.erfangholami.androidsolidservices.shared.model.resource.IASSSolidMetadataCallback
import com.erfangholami.androidsolidservices.shared.model.resource.IASSSolidNonRdfResourceCallback
import com.erfangholami.androidsolidservices.shared.model.resource.IASSSolidRdfResourceCallback
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

@AndroidEntryPoint
class ASSResourceService : LifecycleService() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var solidResourceRepository: SolidResourceRepository

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
                    solidResourceRepository.head(webId, resourceUrl)
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
                    solidResourceRepository.read(
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
                    solidResourceRepository.create(webId, resource)
                }
            }
        }

        override fun createRdf(webId: String, resource: SolidRDFResource, callback: IASSSolidRdfResourceCallback) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchNetwork(
                    ioDispatcher,
                    callback::onError,
                    { callback.onResult(resource) }) {
                    solidResourceRepository.create(webId, resource)
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
                    solidResourceRepository.read(
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
                    solidResourceRepository.read(
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
                    solidResourceRepository.update(webId, resource, ifMatch)
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
                    solidResourceRepository.update(webId, resource, ifMatch)
                }
            }
        }

        override fun patch(webId: String, resourceUrl: String, patchBody: String, callback: IASSUnitCallback) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchUnit(ioDispatcher, callback::onError, callback::onResult) {
                    solidResourceRepository.patchRaw(webId, resourceUrl, patchBody)
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
                    solidResourceRepository.delete(webId, resource)
                }
            }
        }

        override fun deleteRdf(webId: String, resource: SolidRDFResource, callback: IASSSolidRdfResourceCallback) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchNetwork(
                    ioDispatcher,
                    callback::onError,
                    { callback.onResult(resource) }) {
                    solidResourceRepository.delete(webId, resource)
                }
            }
        }

        override fun deleteContainer(webId: String, containerUrl: String, callback: IASSUnitCallback) {
            guard(webId, callback::onError) {
                lifecycleScope.dispatchNetwork(
                    ioDispatcher,
                    callback::onError,
                    { callback.onResult() }) {
                    solidResourceRepository.delete(webId, containerUrl)
                }
            }
        }
    }
}
