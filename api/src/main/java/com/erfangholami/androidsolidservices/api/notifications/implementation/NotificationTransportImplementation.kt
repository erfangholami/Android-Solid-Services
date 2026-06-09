package com.erfangholami.androidsolidservices.api.notifications.implementation

import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.notifications.NotificationTransport
import com.erfangholami.androidsolidservices.api.notifications.RawNotification
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.util.encodeUriString
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

internal class NotificationTransportImplementation private constructor(
    private val rm: SolidResourceManager,
    private val discovery: InboxDiscovery,
    private val ioDispatcher: CoroutineDispatcher,
) : NotificationTransport {

    companion object {
        private const val SLUG_HEADER = "Slug"

        @Volatile
        private var INSTANCE: NotificationTransport? = null

        fun getInstance(authenticator: Authenticator): NotificationTransport =
            getInstance(SolidResourceManager.getInstance(authenticator))

        fun getInstance(resourceManager: SolidResourceManager): NotificationTransport =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: create(resourceManager).also { INSTANCE = it }
            }

        fun create(
            resourceManager: SolidResourceManager,
            discovery: InboxDiscovery = InboxDiscovery(resourceManager),
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        ): NotificationTransport =
            NotificationTransportImplementation(resourceManager, discovery, ioDispatcher)
    }

    override suspend fun discoverInbox(webId: String): SolidNetworkResponse<String?> =
        withContext(ioDispatcher) {
            try {
                SolidNetworkResponse.Success(discovery.resolveOwnInbox(webId)?.toString())
            } catch (e: Exception) {
                SolidNetworkResponse.Exception(e)
            }
        }

    override suspend fun post(
        webId: String,
        inbox: String,
        contentType: String,
        body: ByteArray,
        slug: String?,
    ): SolidNetworkResponse<String?> = withContext(ioDispatcher) {
        try {
            val headers = if (slug != null) mapOf(SLUG_HEADER to slug) else emptyMap()
            when (val r = rm.post(webId, encodeUriString(inbox), contentType, body, headers)) {
                is SolidNetworkResponse.Success -> SolidNetworkResponse.Success(r.data?.toString())
                is SolidNetworkResponse.Error -> SolidNetworkResponse.Error(r.errorCode, r.errorMessage)
                is SolidNetworkResponse.Exception -> SolidNetworkResponse.Exception(r.exception)
            }
        } catch (e: Exception) {
            SolidNetworkResponse.Exception(e)
        }
    }

    override suspend fun list(
        webId: String,
        inbox: String,
    ): SolidNetworkResponse<List<RawNotification>> = withContext(ioDispatcher) {
        try {
            val container = when (
                val r = rm.read(webId, encodeUriString(inbox), SolidContainer::class.java)
            ) {
                is SolidNetworkResponse.Success -> r.data
                is SolidNetworkResponse.Error ->
                    return@withContext SolidNetworkResponse.Error(r.errorCode, r.errorMessage)

                is SolidNetworkResponse.Exception ->
                    return@withContext SolidNetworkResponse.Exception(r.exception)
            }
            val items = container.getContained().mapNotNull {
                runCatching { encodeUriString(it.identifier) }.getOrNull()
            }
            SolidNetworkResponse.Success(items.mapNotNull { readRaw(webId, it) })
        } catch (e: Exception) {
            SolidNetworkResponse.Exception(e)
        }
    }

    override suspend fun read(
        webId: String,
        notificationUri: String,
    ): SolidNetworkResponse<RawNotification> = withContext(ioDispatcher) {
        try {
            val uri = encodeUriString(notificationUri)
            when (val r = rm.read(webId, uri, SolidRDFResource::class.java)) {
                is SolidNetworkResponse.Success ->
                    SolidNetworkResponse.Success(
                        RawNotificationParser.parse(uri.toString(), r.data.getAllQuads()),
                    )

                is SolidNetworkResponse.Error -> SolidNetworkResponse.Error(r.errorCode, r.errorMessage)
                is SolidNetworkResponse.Exception -> SolidNetworkResponse.Exception(r.exception)
            }
        } catch (e: Exception) {
            SolidNetworkResponse.Exception(e)
        }
    }

    override suspend fun delete(
        webId: String,
        notificationUri: String,
    ): SolidNetworkResponse<Boolean> = withContext(ioDispatcher) {
        try {
            rm.delete(webId, encodeUriString(notificationUri))
        } catch (e: Exception) {
            SolidNetworkResponse.Exception(e)
        }
    }

    private suspend fun readRaw(webId: String, item: URI): RawNotification? =
        runCatching {
            val resource = rm.read(webId, item, SolidRDFResource::class.java).getOrThrow()
            RawNotificationParser.parse(item.toString(), resource.getAllQuads())
        }.getOrNull()
}
