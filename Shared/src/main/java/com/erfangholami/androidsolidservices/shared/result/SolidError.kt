package com.erfangholami.androidsolidservices.shared.result

/**
 * Machine-readable classification of a [SolidError].
 *
 * Unlike a human message, a code is stable and safe to branch on, log, and
 * localize. Every [SolidError] variant reports exactly one of these.
 */
public enum class SolidErrorCode {
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    METHOD_NOT_ALLOWED,
    CONFLICT,
    PRECONDITION_FAILED,
    RATE_LIMITED,
    SERVER_ERROR,
    UNEXPECTED_RESPONSE,

    NETWORK,
    TIMEOUT,
    TLS,
    MALFORMED,
    CANCELLED,
    NOT_AUTHENTICATED,

    ACCESS_DENIED,
    ACCESS_INDETERMINATE,
    NO_INBOX,
    INBOX_UNAUTHORIZED,
    INBOX_FORBIDDEN,
    NOTIFICATION_DELIVERY,
    IMPERSONATION_DETECTED,
    STALE_ACL,
    UNSUPPORTED_AUTH_BACKEND,

    UNKNOWN,
}

/**
 * The single failure type for every operation in the library.
 *
 * A `SolidError` carries a stable machine [code], a developer-facing [message]
 * (diagnostic — not for end users; branch on [code] and localize that), the
 * originating [httpStatus] when a server responded, a [retryable] hint, and the
 * underlying [cause] when one exists. It replaces the library's former mix of
 * per-feature result types, thrown domain exceptions, nullable returns, and
 * `Success(empty)`-on-failure.
 *
 * HTTP responses map to an error exactly once via [fromHttp]; local throwables
 * via [fromThrowable].
 */
public sealed class SolidError {

    /** Stable classification; safe to branch on and localize. */
    public abstract val code: SolidErrorCode

    /** Developer-facing diagnostic description. Not an end-user string. */
    public abstract val message: String

    /** The HTTP status that produced this error, or `null` for local/transport errors. */
    public open val httpStatus: Int? = null

    /**
     * `true` when retrying the same request may succeed on its own (transient
     * server/transport conditions). A `false` value does not mean "never retry" —
     * e.g. [PreconditionFailed] needs a fresh ETag first (branch on [code]).
     */
    public open val retryable: Boolean = false

    /** The underlying throwable, when this error wraps one. */
    public open val cause: Throwable? = null

    /** 401 — no valid credentials for the target (distinct from [NotAuthenticated], a missing local session). */
    public data class Unauthorized(
        override val message: String = "Unauthorized (401).",
    ) : SolidError() {
        override val code: SolidErrorCode get() = SolidErrorCode.UNAUTHORIZED
        override val httpStatus: Int get() = 401
    }

    /** 403 — authenticated but not allowed. */
    public data class Forbidden(
        override val message: String = "Forbidden (403).",
    ) : SolidError() {
        override val code: SolidErrorCode get() = SolidErrorCode.FORBIDDEN
        override val httpStatus: Int get() = 403
    }

    /** 404 / 410 — the resource does not exist. */
    public data class NotFound(
        override val message: String = "Not found (404).",
        override val httpStatus: Int = 404,
    ) : SolidError() {
        override val code: SolidErrorCode get() = SolidErrorCode.NOT_FOUND
    }

    /** 405 — the server does not allow this method on the resource. */
    public data class MethodNotAllowed(
        override val message: String = "Method not allowed (405).",
    ) : SolidError() {
        override val code: SolidErrorCode get() = SolidErrorCode.METHOD_NOT_ALLOWED
        override val httpStatus: Int get() = 405
    }

    /** 409 — the request conflicts with the resource's current state. */
    public data class Conflict(
        override val message: String = "Conflict (409).",
    ) : SolidError() {
        override val code: SolidErrorCode get() = SolidErrorCode.CONFLICT
        override val httpStatus: Int get() = 409
    }

    /**
     * 412 — an `If-Match` / `If-None-Match` precondition failed (the resource
     * changed since it was read). Re-read to obtain a fresh ETag, then retry.
     */
    public data class PreconditionFailed(
        override val message: String = "Precondition failed (412).",
    ) : SolidError() {
        override val code: SolidErrorCode get() = SolidErrorCode.PRECONDITION_FAILED
        override val httpStatus: Int get() = 412
    }

    /** 429 — rate limited; retry after a delay. */
    public data class RateLimited(
        override val message: String = "Too many requests (429).",
    ) : SolidError() {
        override val code: SolidErrorCode get() = SolidErrorCode.RATE_LIMITED
        override val httpStatus: Int get() = 429
        override val retryable: Boolean get() = true
    }

    /** 5xx — a server-side failure; typically transient. */
    public data class ServerError(
        val status: Int,
        override val message: String = "Server error ($status).",
    ) : SolidError() {
        override val code: SolidErrorCode get() = SolidErrorCode.SERVER_ERROR
        override val httpStatus: Int get() = status
        override val retryable: Boolean get() = true
    }

    /** A non-success status with no more specific mapping. */
    public data class UnexpectedResponse(
        val status: Int,
        override val message: String = "Unexpected response ($status).",
    ) : SolidError() {
        override val code: SolidErrorCode get() = SolidErrorCode.UNEXPECTED_RESPONSE
        override val httpStatus: Int get() = status
        override val retryable: Boolean get() = status in 500..599
    }

    /** A network I/O failure before a response was obtained; retryable. */
    public data class Network(
        override val cause: Throwable? = null,
        override val message: String = "Network error.",
    ) : SolidError() {
        override val code: SolidErrorCode get() = SolidErrorCode.NETWORK
        override val retryable: Boolean get() = true
    }

    /** A request or connection timed out; retryable. */
    public data class Timeout(
        override val cause: Throwable? = null,
        override val message: String = "Timed out.",
    ) : SolidError() {
        override val code: SolidErrorCode get() = SolidErrorCode.TIMEOUT
        override val retryable: Boolean get() = true
    }

    /** A TLS/certificate failure. */
    public data class Tls(
        override val cause: Throwable? = null,
        override val message: String = "TLS failure.",
    ) : SolidError() {
        override val code: SolidErrorCode get() = SolidErrorCode.TLS
    }

    /** A response body (or local payload) could not be parsed/serialized. */
    public data class Malformed(
        override val message: String = "Malformed data.",
        override val cause: Throwable? = null,
    ) : SolidError() {
        override val code: SolidErrorCode get() = SolidErrorCode.MALFORMED
    }

    /** The operation was cancelled (coroutine cancellation). */
    public data object Cancelled : SolidError() {
        override val code: SolidErrorCode get() = SolidErrorCode.CANCELLED
        override val message: String get() = "Cancelled."
    }

    /** No authorized local session exists for the WebID — complete sign-in first. */
    public data class NotAuthenticated(
        override val message: String = "Not authenticated. Complete sign-in first.",
    ) : SolidError() {
        override val code: SolidErrorCode get() = SolidErrorCode.NOT_AUTHENTICATED
    }

    /** Definitive no-access to a resource; the UI can offer to send an access request. */
    public data class AccessDenied(
        val resourceUri: String,
        val ownerWebId: String? = null,
        override val message: String = "No access to $resourceUri.",
    ) : SolidError() {
        override val code: SolidErrorCode get() = SolidErrorCode.ACCESS_DENIED
    }

    /** Access could not be authoritatively determined (transient); retry rather than deny. */
    public data class AccessIndeterminate(
        val resourceUri: String,
        override val message: String = "Could not verify access to $resourceUri right now.",
    ) : SolidError() {
        override val code: SolidErrorCode get() = SolidErrorCode.ACCESS_INDETERMINATE
        override val retryable: Boolean get() = true
    }

    /** The target agent's profile advertises no `ldp:inbox`, so a notification can't be delivered. */
    public data class NoInbox(
        val targetWebId: String,
        override val message: String = "No inbox advertised for $targetWebId.",
    ) : SolidError() {
        override val code: SolidErrorCode get() = SolidErrorCode.NO_INBOX
    }

    /** The recipient's inbox returned 401. */
    public data class InboxUnauthorized(
        val inboxUri: String,
        override val message: String = "Inbox at $inboxUri requires authentication (401).",
    ) : SolidError() {
        override val code: SolidErrorCode get() = SolidErrorCode.INBOX_UNAUTHORIZED
        override val httpStatus: Int get() = 401
    }

    /** The recipient's inbox returned 403. */
    public data class InboxForbidden(
        val inboxUri: String,
        override val message: String = "Inbox at $inboxUri forbids this operation (403).",
    ) : SolidError() {
        override val code: SolidErrorCode get() = SolidErrorCode.INBOX_FORBIDDEN
        override val httpStatus: Int get() = 403
    }

    /** A non-auth failure delivering a notification to an inbox. */
    public data class NotificationDelivery(
        val inboxUri: String,
        val status: Int? = null,
        override val message: String = "Notification delivery to $inboxUri failed" +
            (status?.let { " (HTTP $it)" } ?: "") + ".",
    ) : SolidError() {
        override val code: SolidErrorCode get() = SolidErrorCode.NOTIFICATION_DELIVERY
        override val httpStatus: Int? get() = status
    }

    /** An inbound notification's actor doesn't match the resource owner; it was dropped. */
    public data class ImpersonationDetected(
        val notificationUri: String,
        val claimedActor: String,
        val actualOwner: String? = null,
        override val message: String =
            "Notification $notificationUri claims actor=$claimedActor but owner is " +
                "${actualOwner ?: "unknown"}.",
    ) : SolidError() {
        override val code: SolidErrorCode get() = SolidErrorCode.IMPERSONATION_DETECTED
    }

    /** A conditional ACL/ACR write was rejected (412) by a concurrent change; re-read and retry. */
    public data class StaleAcl(
        val aclUri: String,
        override val message: String = "ACL at $aclUri changed concurrently (412).",
    ) : SolidError() {
        override val code: SolidErrorCode get() = SolidErrorCode.STALE_ACL
        override val httpStatus: Int get() = 412
    }

    /** The resource's access-control backend isn't supported. */
    public data class UnsupportedAuthBackend(
        val resourceUri: String,
        val backend: String,
        override val message: String = "Auth backend $backend not supported for $resourceUri.",
    ) : SolidError() {
        override val code: SolidErrorCode get() = SolidErrorCode.UNSUPPORTED_AUTH_BACKEND
    }

    /** Anything with no more specific mapping. */
    public data class Unknown(
        override val message: String = "Unknown error.",
        override val cause: Throwable? = null,
    ) : SolidError() {
        override val code: SolidErrorCode get() = SolidErrorCode.UNKNOWN
    }

    /** Wraps this error in a throwable for `getOrThrow`-style propagation, preserving [cause]. */
    public fun asException(): SolidResultException = SolidResultException(this)

    public companion object {
        /**
         * Maps an HTTP status (and optional server-provided [detail]) to the
         * matching error — the single place status codes are classified.
         */
        public fun fromHttp(status: Int, detail: String? = null): SolidError {
            val msg = detail?.takeIf { it.isNotBlank() }
            return when (status) {
                401 -> Unauthorized(msg ?: "Unauthorized (401).")
                403 -> Forbidden(msg ?: "Forbidden (403).")
                404, 410 -> NotFound(msg ?: "Not found ($status).", httpStatus = status)
                405 -> MethodNotAllowed(msg ?: "Method not allowed (405).")
                409 -> Conflict(msg ?: "Conflict (409).")
                412 -> PreconditionFailed(msg ?: "Precondition failed (412).")
                429 -> RateLimited(msg ?: "Too many requests (429).")
                in 500..599 -> ServerError(status, msg ?: "Server error ($status).")
                else -> UnexpectedResponse(status, msg ?: "Unexpected response ($status).")
            }
        }

        /**
         * Maps a thrown [throwable] to the matching error — timeouts, TLS,
         * network I/O, and cancellation are recognized; anything else becomes
         * [Unknown]. [kotlinx.coroutines.CancellationException] should be
         * rethrown by callers before reaching here, but is mapped to [Cancelled]
         * defensively.
         */
        public fun fromThrowable(throwable: Throwable): SolidError {
            val name = throwable.javaClass.name
            return when {
                name == "kotlinx.coroutines.CancellationException" ||
                    throwable is java.util.concurrent.CancellationException -> Cancelled

                throwable is java.net.SocketTimeoutException ||
                    throwable is java.util.concurrent.TimeoutException ->
                    Timeout(throwable, throwable.message ?: "Timed out.")

                throwable is javax.net.ssl.SSLException ->
                    Tls(throwable, throwable.message ?: "TLS failure.")

                throwable is java.net.UnknownHostException ||
                    throwable is java.net.ConnectException ||
                    throwable is java.io.IOException ->
                    Network(throwable, throwable.message ?: "Network error.")

                else -> Unknown(throwable.message ?: throwable.javaClass.simpleName, throwable)
            }
        }
    }
}

/** Throwable wrapper for a [SolidError], used to propagate a failure through `getOrThrow`. */
public class SolidResultException(
    public val error: SolidError,
) : Exception(error.message, error.cause)
