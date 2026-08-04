package com.erfangholami.androidsolidservices.api.auth.implementation

import android.util.Log
import com.erfangholami.androidsolidservices.shared.telemetry.Telemetry
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Jwks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap

internal object IdTokenVerifier {

    private const val TAG = "IdTokenVerifier"

    private const val JWKS_CACHE_TTL_MS = 6 * 60 * 60 * 1000L

    private val httpClient = OkHttpClient()

    private data class CachedJwks(val json: JSONObject, val fetchedAt: Long)

    private val jwksCache = ConcurrentHashMap<String, CachedJwks>()

    suspend fun verify(idToken: String, jwksUri: URI): Boolean {
        return try {
            val kid = extractKid(idToken)
            val publicKey = resolveKeyCached(jwksUri, kid)
            if (publicKey == null) {
                Log.w(TAG, "verify: no matching key found in JWKS (kid=$kid)")
                return false
            }
            Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(idToken)
            true
        } catch (e: Exception) {
            Log.w(TAG, "verify: JWT verification failed", e)
            false
        }
    }

    private suspend fun resolveKeyCached(jwksUri: URI, kid: String?): PublicKey? {
        val cacheKey = jwksUri.toString()
        val cached = jwksCache[cacheKey]
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.fetchedAt < JWKS_CACHE_TTL_MS) {
            resolveKey(cached.json, kid)?.let { return it }
        }
        val fetched = fetchJwks(jwksUri)
        if (fetched != null) {
            jwksCache[cacheKey] = CachedJwks(fetched, now)
            return resolveKey(fetched, kid)
        }
        if (cached != null) {
            Log.w(TAG, "resolveKeyCached: JWKS refetch failed; falling back to cached keys")
            Telemetry.log(
                "solid.auth jwks refetch failed for ${jwksUri.host}; " +
                    "using keys cached ${(now - cached.fetchedAt) / 1000}s ago",
            )
            return resolveKey(cached.json, kid)
        }
        Telemetry.log("solid.auth jwks unavailable for ${jwksUri.host} and nothing cached — validation will fail")
        return null
    }

    private suspend fun fetchJwks(jwksUri: URI): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(jwksUri.toString()).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "fetchJwks: HTTP ${response.code} from $jwksUri")
                return@withContext null
            }
            val body = response.body.string()
            if (body.isEmpty()) {
                Log.w(TAG, "fetchJwks: empty body from $jwksUri")
                return@withContext null
            }
            JSONObject(body)
        } catch (e: Exception) {
            Log.w(TAG, "fetchJwks: failed to fetch $jwksUri", e)
            null
        }
    }

    private fun extractKid(idToken: String): String? {
        return try {
            val header = idToken.split(".")[0]
            val decoded = android.util.Base64.decode(
                header,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING,
            )
            JSONObject(String(decoded)).optString("kid").takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "extractKid: failed to parse JWT header", e)
            null
        }
    }

    private fun resolveKey(jwksJson: JSONObject, kid: String?): PublicKey? {
        return try {
            val keys = jwksJson.getJSONArray("keys")
            for (i in 0..<keys.length()) {
                val keyJson = keys.getJSONObject(i)
                if (kid == null || keyJson.optString("kid") == kid) {
                    val jwk = Jwks.parser().build().parse(keyJson.toString())
                    return jwk.toKey() as? PublicKey
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "resolveKey: failed to parse JWK (kid=$kid)", e)
            null
        }
    }
}
