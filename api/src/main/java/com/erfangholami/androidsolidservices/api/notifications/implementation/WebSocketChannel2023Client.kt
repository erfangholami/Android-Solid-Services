package com.erfangholami.androidsolidservices.api.notifications.implementation

import com.erfangholami.androidsolidservices.api.auth.implementation.AuthSession
import com.erfangholami.androidsolidservices.api.notifications.RawNotification
import com.erfangholami.androidsolidservices.shared.http.HTTPHeaderName
import com.erfangholami.androidsolidservices.shared.model.resource.RDFResource
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.util.encodeUri
import com.erfangholami.androidsolidservices.shared.vocab.Notify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URI

/**
 * Negotiates and streams a Solid `WebSocketChannel2023` notification channel.
 *
 * Spec: https://solid.github.io/notifications/websocket-channel-2023
 *
 * [negotiate] POSTs an authenticated channel request to the pod's subscription service and reads
 * back the `notify:receiveFrom` WebSocket URL; [connect] opens that WebSocket and emits each
 * pushed frame — decoded exactly like a polled inbox notification — through a cold [Flow] whose
 * lifetime bounds the socket's.
 */
internal class WebSocketChannel2023Client(
    private val auth: AuthSession,
    private val ioDispatcher: CoroutineDispatcher,
    private val httpClient: OkHttpClient = defaultWebSocketClient(),
) {

    /** Creates a channel for [topic] via [subscriptionService], returning the `receiveFrom` URL. */
    suspend fun negotiate(webId: String, subscriptionService: URI, topic: URI): URI {
        val requestJson = channelRequestBody(topic)
        var didForceRefresh = false
        repeat(MAX_ATTEMPTS) {
            val headers = auth.getAuthHeaders(webId, "POST", subscriptionService.toString())
            val request = Request.Builder()
                .url(encodeUri(subscriptionService).toString())
                .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                .post(requestJson.toByteArray(Charsets.UTF_8).toRequestBody(LD_JSON.toMediaTypeOrNull()))
                .build()
            val response = httpClient.newCall(request).execute()
            val code = response.code
            val wwwAuth = response.header(HTTPHeaderName.WWW_AUTHENTICATE) ?: ""
            response.header(HTTPHeaderName.DPOP_NONCE)?.let {
                auth.updateDPoPNonce(webId, subscriptionService.toString(), it)
            }
            val body = response.body?.string().orEmpty()
            response.close()
            when {
                code in 200..299 ->
                    return extractReceiveFrom(body)
                        ?: throw SolidError.fromHttp(code, "channel response has no notify:receiveFrom").asException()

                code == 401 && !didForceRefresh &&
                    !wwwAuth.contains("use_dpop_nonce", true) -> {
                    auth.getLastTokenResponse(webId, forceRefresh = true)
                    didForceRefresh = true
                }

                code == 401 -> Unit // nonce now recorded (or refreshed) — retry

                else -> throw SolidError.fromHttp(code, body.take(BODY_EXCERPT)).asException()
            }
        }
        throw SolidError.fromHttp(500, "WebSocketChannel2023: channel negotiation exhausted retries").asException()
    }

    /** Opens the [receiveFrom] WebSocket and streams decoded notifications until collection stops. */
    fun connect(webId: String, receiveFrom: URI): Flow<RawNotification> = callbackFlow {
        val headers = runCatching { auth.getAuthHeaders(webId, "GET", receiveFrom.toString()) }
            .getOrDefault(emptyMap())
        val request = Request.Builder()
            .url(receiveFrom.toString())
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .build()
        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                decodeFrame(text, receiveFrom.toString())?.let { trySend(it) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                channel.close()
            }
        }
        val webSocket = httpClient.newWebSocket(request, listener)
        awaitClose { webSocket.cancel() }
    }.flowOn(ioDispatcher)

    private fun channelRequestBody(topic: URI): String =
        """
        {
          "@context": ["https://www.w3.org/ns/solid/notifications-context/v1"],
          "type": "WebSocketChannel2023",
          "topic": "${topic}"
        }
        """.trimIndent()

    private fun extractReceiveFrom(body: String): URI? {
        val json = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
        if (json != null) {
            (json["receiveFrom"]?.jsonPrimitive)?.contentOrNull?.let { return it.toUriOrNull() }
            (json[Notify.RECEIVE_FROM] as? JsonArray)?.firstOrNull()?.jsonObject
                ?.get("@id")?.jsonPrimitive?.contentOrNull?.let { return it.toUriOrNull() }
        }
        return runCatching {
            RDFResource.parseJsonLd(body).firstOrNull { it.predicate == Notify.RECEIVE_FROM }?.`object`
        }.getOrNull()?.toUriOrNull()
    }

    private fun decodeFrame(text: String, base: String): RawNotification? {
        val quads = runCatching { RDFResource.parseJsonLd(text, base) }.getOrNull()
            ?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { RawNotificationParser.parse(base, quads) }.getOrNull()
    }

    private fun String.toUriOrNull(): URI? = runCatching { URI.create(this) }.getOrNull()

    private companion object {
        const val LD_JSON = "application/ld+json"
        const val MAX_ATTEMPTS = 3
        const val BODY_EXCERPT = 512

        fun defaultWebSocketClient(): OkHttpClient =
            OkHttpClient.Builder()
                .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
    }
}
