package com.erfangholami.androidsolidservices.api.auth.implementation

import android.util.Log
import com.erfangholami.androidsolidservices.api.resource.implementation.SolidHttpClient
import com.erfangholami.androidsolidservices.shared.http.HTTPAcceptType
import com.erfangholami.androidsolidservices.shared.http.HTTPHeaderName
import net.openid.appauth.AuthorizationServiceDiscovery
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder

private const val AUTH_LOG_TAG = "Authenticator"

private fun tracedTokenHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .addNetworkInterceptor { chain ->
        Log.i(AUTH_LOG_TAG, "AuthTrace: wire → ${chain.request().method} ${chain.request().url}")
        chain.proceed(chain.request())
    }
    .build()

internal class DPoPTokenRequester(
    private val http: SolidHttpClient = SolidHttpClient(httpClient = tracedTokenHttpClient()),
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
                    "AuthTrace: tokenEndpoint transport failure on attempt=${attempt + 1}: " +
                        "${e.javaClass.simpleName}: ${e.message} — NOTE: the request may still have " +
                        "reached the server (and spent the refresh token) even though no response arrived",
                )
                return DPoPTokenResult.Failure(statusCode = 0, error = null, errorDescription = e.message)
            }

            response.headers[HTTPHeaderName.DPOP_NONCE]?.let { dpop.updateNonce(tokenEndpoint.toString(), it) }

            if (response.statusCode in 200..299) {
                Log.i(AUTH_LOG_TAG, "AuthTrace: tokenEndpoint attempt=${attempt + 1} status=${response.statusCode}")
                return runCatching { DPoPTokenResult.Success(JSONObject(response.body)) }
                    .getOrElse {
                        DPoPTokenResult.Failure(response.statusCode, null, "Malformed token response")
                    }
            }

            val (error, description) = parseOAuthError(response.body)
            Log.w(
                AUTH_LOG_TAG,
                "AuthTrace: tokenEndpoint attempt=${attempt + 1} status=${response.statusCode} " +
                    "error=$error desc=$description nonceHeader=${response.headers[HTTPHeaderName.DPOP_NONCE] != null}",
            )
            val failure = DPoPTokenResult.Failure(response.statusCode, error, description)
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
    ) : DPoPTokenResult
}
