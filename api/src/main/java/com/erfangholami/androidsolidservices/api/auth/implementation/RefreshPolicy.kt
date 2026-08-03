package com.erfangholami.androidsolidservices.api.auth.implementation

import com.erfangholami.androidsolidservices.api.auth.Profile
import com.erfangholami.androidsolidservices.shared.telemetry.Telemetry
import net.openid.appauth.AuthorizationException
import java.util.concurrent.ConcurrentHashMap

private const val REFRESH_COALESCE_MS = 5_000L

private const val REFRESH_LEAD_MS = 60_000L

private const val FORCED_REFRESH_COOLDOWN_MS = 60_000L

private const val RATE_LIMIT_BACKOFF_MS = 60_000L

private const val RATE_LIMIT_BACKOFF_MIN_MS = 10_000L

private const val RATE_LIMIT_BACKOFF_MAX_MS = 300_000L

/**
 * Every *decision* about refreshing, separated from the machinery that performs one: expiry
 * lead time, result coalescing, the forced-refresh cooldown, and the 429 backoff window. The
 * coordinator asks; this answers. Being a plain clock-driven class, the whole policy is
 * unit-testable without a token endpoint.
 */
internal class RefreshPolicy(
    private val now: () -> Long,
) {
    private val recentRefresh = ConcurrentHashMap<String, Pair<Long, Profile>>()

    private val lastForcedRefresh = ConcurrentHashMap<String, Long>()

    private val rateLimitedUntil = ConcurrentHashMap<String, Long>()

    fun forget(webId: String) {
        recentRefresh.remove(webId)
        lastForcedRefresh.remove(webId)
        rateLimitedUntil.remove(webId)
    }

    fun forgetAll() {
        recentRefresh.clear()
        lastForcedRefresh.clear()
        rateLimitedUntil.clear()
    }

    fun shouldSkip(webId: String, profile: Profile, forceRefresh: Boolean): Boolean = when {
        SessionState.of(profile).isDead -> true
        !forceRefresh && !needsTokenRefresh(profile) -> true
        isRateLimited(webId, profile) -> true
        forceRefresh && !needsTokenRefresh(profile) && isForcedRefreshOnCooldown(webId) -> true
        else -> false
    }

    fun coalescedResult(webId: String): Profile? {
        val (at, refreshed) = recentRefresh[webId] ?: return null
        return refreshed.takeIf { now() - at < REFRESH_COALESCE_MS }
    }

    fun noteSuccess(webId: String, refreshed: Profile) {
        recentRefresh[webId] = now() to refreshed
    }

    fun noteForced(webId: String) {
        lastForcedRefresh[webId] = now()
    }

    fun recordRateLimit(webId: String, retryAfterSeconds: Long?) {
        val backoff = retryAfterSeconds?.times(1000)
            ?.coerceIn(RATE_LIMIT_BACKOFF_MIN_MS, RATE_LIMIT_BACKOFF_MAX_MS)
            ?: RATE_LIMIT_BACKOFF_MS
        rateLimitedUntil[webId] = now() + backoff
        Telemetry.log("solid.auth token endpoint returned 429; backing off ${backoff / 1000}s")
    }

    fun needsTokenRefresh(profile: Profile): Boolean {
        val expirationTime =
            profile.authState.lastTokenResponse?.accessTokenExpirationTime ?: return true
        return (now() + REFRESH_LEAD_MS) > expirationTime
    }

    fun isAccessTokenHardExpired(profile: Profile): Boolean {
        val expirationTime =
            profile.authState.lastTokenResponse?.accessTokenExpirationTime ?: return true
        return now() >= expirationTime
    }

    fun isTerminalRefreshError(exception: AuthorizationException?): Boolean {
        val error = exception?.error ?: return false
        return error == SessionErrors.INVALID_GRANT || error == SessionErrors.INVALID_CLIENT
    }

    private fun isRateLimited(webId: String, profile: Profile): Boolean {
        val until = rateLimitedUntil[webId] ?: return false
        if (now() >= until) {
            rateLimitedUntil.remove(webId)
            return false
        }
        return !isAccessTokenHardExpired(profile)
    }

    private fun isForcedRefreshOnCooldown(webId: String): Boolean {
        val lastForced = lastForcedRefresh[webId] ?: return false
        if (now() - lastForced >= FORCED_REFRESH_COOLDOWN_MS) return false
        Telemetry.log("solid.auth forced refresh suppressed (token still valid, cooldown active)")
        return true
    }
}
