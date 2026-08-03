package com.erfangholami.androidsolidservices.api.notifications.implementation

import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.auth.SolidSession
import com.erfangholami.androidsolidservices.api.notifications.NotificationTransport
import com.erfangholami.androidsolidservices.api.notifications.RawNotification
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.util.encodeUriString
import com.erfangholami.androidsolidservices.shared.vocab.Notify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.net.URI

internal class NotificationTransportImplementation private constructor(
    private val rm: SolidResourceManager,
    private val discovery: InboxDiscovery,
    private val ioDispatcher: CoroutineDispatcher,
    auth: SolidSession?,
) : NotificationTransport {

    private val wsClient: WebSocketChannel2023Client? =
        auth?.let { WebSocketChannel2023Client(it, ioDispatcher) }

    internal val hasAuth: Boolean get() = wsClient != null

    companion object {
        private const val SLUG_HEADER = "Slug"

        @Volatile
        private var instance: NotificationTransportImplementation? = null

        fun getInstance(authenticator: Authenticator): NotificationTransport {
            instance?.takeIf { it.hasAuth }?.let { return it }
            return synchronized(this) {
                instance?.takeIf { it.hasAuth } ?: create(
                    SolidResourceManager.getInstance(authenticator),
                    auth = authenticator,
                ).also { instance = it }
            }
        }

        fun getInstance(resourceManager: SolidResourceManager): NotificationTransport =
            instance ?: synchronized(this) {
                instance ?: create(resourceManager).also { instance = it }
            }

        internal fun resetForTest() {
            instance = null
        }

        fun create(
            resourceManager: SolidResourceManager,
            discovery: InboxDiscovery = InboxDiscovery(resourceManager),
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
            auth: SolidSession? = null,
        ): NotificationTransportImplementation =
            NotificationTransportImplementation(resourceManager, discovery, ioDispatcher, auth)
    }

    override suspend fun discoverInbox(webId: String): SolidResult<String?> =
        withContext(ioDispatcher) {
            try {
                SolidResult.Success(discovery.resolveOwnInbox(webId))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                SolidResult.Failure(SolidError.fromThrowable(e))
            }
        }

    override suspend fun post(
        webId: String,
        inbox: String,
        contentType: String,
        body: ByteArray,
        slug: String?,
    ): SolidResult<String?> = withContext(ioDispatcher) {
        try {
            val headers = if (slug != null) mapOf(SLUG_HEADER to slug) else emptyMap()
            when (val r = rm.post(webId, encodeUriString(inbox).toString(), contentType, body, headers)) {
                is SolidResult.Success -> SolidResult.Success(r.value?.toString())
                is SolidResult.Failure -> r
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            SolidResult.Failure(SolidError.fromThrowable(e))
        }
    }

    override suspend fun list(
        webId: String,
        inbox: String,
    ): SolidResult<List<RawNotification>> = withContext(ioDispatcher) {
        try {
            val container = when (
                val r = rm.read(webId, encodeUriString(inbox).toString(), SolidContainer::class.java)
            ) {
                is SolidResult.Success -> r.value
                is SolidResult.Failure -> return@withContext r
            }
            val items = container.getContained().mapNotNull {
                runCatching { encodeUriString(it.identifier) }.getOrNull()
            }
            SolidResult.Success(items.mapNotNull { readRaw(webId, it) })
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            SolidResult.Failure(SolidError.fromThrowable(e))
        }
    }

    override suspend fun read(
        webId: String,
        notificationUri: String,
    ): SolidResult<RawNotification> = withContext(ioDispatcher) {
        try {
            val uri = encodeUriString(notificationUri)
            when (val r = rm.read(webId, uri.toString(), SolidRDFResource::class.java)) {
                is SolidResult.Success ->
                    SolidResult.Success(
                        RawNotificationParser.parse(uri.toString(), r.value.getAllQuads()),
                    )

                is SolidResult.Failure -> r
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            SolidResult.Failure(SolidError.fromThrowable(e))
        }
    }

    override suspend fun delete(
        webId: String,
        notificationUri: String,
    ): SolidResult<Boolean> = withContext(ioDispatcher) {
        try {
            rm.delete(webId, encodeUriString(notificationUri).toString())
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            SolidResult.Failure(SolidError.fromThrowable(e))
        }
    }

    private suspend fun readRaw(webId: String, item: URI): RawNotification? =
        runCatching {
            val resource = rm.read(webId, item.toString(), SolidRDFResource::class.java).getOrThrow()
            RawNotificationParser.parse(item.toString(), resource.getAllQuads())
        }.getOrNull()

    override suspend fun subscribe(
        webId: String,
        resourceUri: String,
    ): SolidResult<Flow<RawNotification>> = withContext(ioDispatcher) {
        try {
            val client = wsClient
                ?: return@withContext SolidResult.Failure(
                    SolidError.fromHttp(501, "subscribe requires a NotificationTransport built from an Authenticator"),
                )
            val topic = encodeUriString(resourceUri)
            val service = discoverWebSocketSubscription(webId, topic)
                ?: return@withContext SolidResult.Failure(
                    SolidError.fromHttp(501, "pod advertises no WebSocketChannel2023 subscription service"),
                )
            val receiveFrom = client.negotiate(webId, service, topic)
            SolidResult.Success(client.connect(webId, receiveFrom))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            SolidResult.Failure(SolidError.fromThrowable(e))
        }
    }

    private suspend fun discoverWebSocketSubscription(webId: String, topic: URI): URI? {
        val metadata = (rm.head(webId, topic.toString()) as? SolidResult.Success)?.value ?: return null
        val storageDescription = metadata.storageDescriptionUri ?: return null
        val quads = (rm.read(webId, storageDescription.toString(), SolidRDFResource::class.java) as? SolidResult.Success)
            ?.value?.getAllQuads() ?: return null
        val services = quads.filter { it.predicate == Notify.SUBSCRIPTION }.map { it.`object` }
        val wsService = services.firstOrNull { service ->
            quads.any {
                it.subject == service && it.predicate == Notify.CHANNEL_TYPE &&
                    it.`object` == Notify.WEB_SOCKET_CHANNEL_2023
            }
        } ?: return null
        return runCatching { URI.create(wsService) }.getOrNull()
    }
}
