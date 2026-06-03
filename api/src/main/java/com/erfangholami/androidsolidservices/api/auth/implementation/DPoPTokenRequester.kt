package com.erfangholami.androidsolidservices.api.auth.implementation

import com.erfangholami.androidsolidservices.api.resource.implementation.SolidHttpClient
import com.erfangholami.androidsolidservices.shared.http.HTTPAcceptType
import com.erfangholami.androidsolidservices.shared.http.HTTPHeaderName
import net.openid.appauth.AuthorizationServiceDiscovery
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder

/**
 * Performs a DPoP-bound token request against an authorization server's token endpoint, handling
 * the [RFC 9449](https://datatracker.ietf.org/doc/html/rfc9449) nonce challenge that AppAuth's
 * `performTokenRequest` cannot: it reads the `DPoP-Nonce` response header and retries once when the
 * server answers `400 use_dpop_nonce`.
 *
 * This exists because AppAuth never surfaces a token response's HTTP headers to its callers, so the
 * authorization-server nonce is invisible through it — which makes silent token refresh impossible
 * on servers that enforce a token-endpoint nonce (e.g. Inrupt ESS).
 */
internal class DPoPTokenRequester(
    private val http: SolidHttpClient = SolidHttpClient(),
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
            } catch (e: Exception) {
                // Transport failure (no HTTP response). Recoverable — must not invalidate the session.
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
