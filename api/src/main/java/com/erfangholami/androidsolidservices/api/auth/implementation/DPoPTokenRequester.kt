package com.erfangholami.androidsolidservices.api.auth.implementation

import android.util.Log
import com.erfangholami.androidsolidservices.api.resource.implementation.SolidHttpClient
import com.erfangholami.androidsolidservices.shared.http.HTTPAcceptType
import com.erfangholami.androidsolidservices.shared.http.HTTPHeaderName
import com.erfangholami.androidsolidservices.shared.telemetry.Telemetry
import net.openid.appauth.AuthorizationServiceDiscovery
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val AUTH_LOG_TAG = "Authenticator"

private fun tokenHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .retryOnConnectionFailure(false)
    .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
    .build()

internal class DPoPTokenRequester(
    private val http: SolidHttpClient = SolidHttpClient(httpClient = tokenHttpClient()),
) {

    suspend fun request(
        tokenEndpoint: URI,
        bodyParams: Map<String, String>,
        basicAuthHeader: String?,
        discoveryDoc: AuthorizationServiceDiscovery,
        keyId: String?,
    ): DPoPTokenResult {
        val dpop = DPoPGenerator.getInstance(discoveryDoc, keyId)
        val body = formEncode(bodyParams).toByteArray(Charsets.UTF_8)
        var lastFailure: DPoPTokenResult.Failure? = null

        repeat(MAX_ATTEMPTS) { attempt ->
            val headers = buildMap {
                put(HTTPHeaderName.DPOP, dpop.generateProof("POST", tokenEndpoint.toString()))
                if (basicAuthHeader != null) put(HTTPHeaderName.AUTHORIZATION, basicAuthHeader)
            }
            val response = try {
                http.send(
                    method = "POST",
                    uri = tokenEndpoint,
                    contentType = HTTPAcceptType.FORM_URL_ENCODED,
                    accept = HTTPAcceptType.JSON,
                    body = body,
                    headers = headers,
                )
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                Log.w(
                    AUTH_LOG_TAG,
                    "Token endpoint transport failure on attempt=${attempt + 1}: " +
                        "${e.javaClass.simpleName}: ${e.message} — the request may still have " +
                        "reached the server even though no response arrived",
                )
                Telemetry.log(
                    "solid.auth token endpoint transport failure (${e.javaClass.simpleName})",
                )
                return DPoPTokenResult.Failure(statusCode = 0, error = null, errorDescription = e.message)
            }

            response.headers[HTTPHeaderName.DPOP_NONCE]?.let { dpop.updateNonce(tokenEndpoint.toString(), it) }

            if (response.statusCode in 200..299) {
                return runCatching { DPoPTokenResult.Success(JSONObject(response.body)) }
                    .getOrElse {
                        DPoPTokenResult.Failure(response.statusCode, null, "Malformed token response")
                    }
            }

            val (error, description) = parseOAuthError(response.body)
            Telemetry.log(
                "solid.auth token endpoint status=${response.statusCode} error=$error attempt=${attempt + 1}",
            )
            val failure = DPoPTokenResult.Failure(
                statusCode = response.statusCode,
                error = error,
                errorDescription = description,
                retryAfterSeconds = response.headers["Retry-After"]?.trim()?.toLongOrNull(),
            )
            lastFailure = failure

            val canRetryWithNonce = attempt == 0 &&
                response.statusCode == 400 &&
                error == "use_dpop_nonce" &&
                response.headers[HTTPHeaderName.DPOP_NONCE] != null
            if (!canRetryWithNonce) return failure
        }
        return lastFailure!!
    }

    private fun formEncode(params: Map<String, String>): String =
        params.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }

    private fun parseOAuthError(body: String): Pair<String?, String?> =
        try {
            val json = JSONObject(body)
            json.optString("error").takeIf { it.isNotEmpty() } to
                json.optString("error_description").takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null to null
        }

    private companion object {
        const val MAX_ATTEMPTS = 2
    }
}

internal sealed interface DPoPTokenResult {
    class Success(val json: JSONObject) : DPoPTokenResult
    class Failure(
        val statusCode: Int,
        val error: String?,
        val errorDescription: String?,
        val retryAfterSeconds: Long? = null,
    ) : DPoPTokenResult
}
