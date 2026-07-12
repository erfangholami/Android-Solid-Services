package com.erfangholami.androidsolidservices.api.resource

import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.resource.implementation.SolidResourceManagerImplementation
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.result.SolidErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import java.net.URI

/**
 * Performs authenticated CRUD operations on Solid pod resources on behalf of a specific user.
 *
 * All operations require the user identified by `webid` to have an active, authorized
 * [Authenticator] session.  Results are wrapped in [SolidResult] so callers can
 * distinguish HTTP errors from unexpected exceptions without catching throwables.
 *
 * Obtain an instance via [SolidResourceManager.getInstance].
 */
public interface SolidResourceManager {

    public companion object {
        /**
         * Returns the application-scoped singleton [SolidResourceManager].
         * @param authenticator Any [Authenticator]; The one used for authentication.
         */
        public fun getInstance(authenticator: Authenticator): SolidResourceManager =
            SolidResourceManagerImplementation.getInstance(authenticator)

        /**
         * Turns HTTP request/response tracing on or off on the shared
         * [okhttp3.OkHttpClient]-backed client used by every CRUD method on
         * this interface. Off by default.
         *
         * When on, every Solid HTTP call logs to `android.util.Log` with tag
         * `SolidHttp`: `→ METHOD URI` for the request and `← STATUS METHOD
         * URI [— body excerpt]` for the response (body excerpt included for
         * non-2xx only). Intended for diagnosing sharing-pipeline failures
         * where the user only sees a bare 401/403/404; should be left off
         * in production builds.
         */
        public fun setHttpTrace(enabled: Boolean): Unit =
            SolidResourceManagerImplementation.setHttpTrace(enabled)
    }

    /**
     * Fetches only the HTTP headers for the resource at [uri] via HTTP HEAD.
     *
     * Returns [SolidMetadata] with all Solid-relevant response headers: ETag, Content-Type,
     * Content-Length, WAC-Allow, Allow, all Link relations (acl, describedby, type,
     * storageDescription), Accept-Patch/Post/Put, Last-Modified, and WWW-Authenticate.
     *
     * No response body is transferred. Ideal for caching checks, permission discovery,
     * and auxiliary resource IRI resolution before committing to a full GET.
     *
     * @param webid The WebID of the authenticated user making the request.
     * @param uri   The URI of the resource to HEAD.
     * @return [SolidResult.Success] with [SolidMetadata], or a [SolidResult.Failure] carrying a typed [SolidError].
     */
    public suspend fun head(
        webid: String,
        uri: URI,
    ): SolidResult<SolidMetadata>

    /**
     * Reports whether a resource exists at [uri].
     *
     * A HEAD that resolves (2xx) → `Success(true)`; a `404 Not Found` → `Success(false)`.
     * Any other outcome (403, auth, network, 5xx) is *indeterminate* and surfaces as
     * [SolidResult.Failure] rather than being collapsed to `false`, so callers don't
     * mistake "couldn't tell" for "absent".
     *
     * @param webid The WebID of the authenticated user making the request.
     * @param uri   The URI to probe.
     */
    public suspend fun exists(webid: String, uri: URI): SolidResult<Boolean> =
        when (val head = head(webid, uri)) {
            is SolidResult.Success -> SolidResult.Success(true)
            is SolidResult.Failure ->
                if (head.error.code == SolidErrorCode.NOT_FOUND) SolidResult.Success(false)
                else SolidResult.Failure(head.error)
        }

    /**
     * Ensures the container at [containerUri] exists, creating it — and any missing
     * ancestor containers, bottom-up — as LDP BasicContainers. Idempotent: a no-op when
     * the container is already present. This covers servers that do not auto-create
     * intermediate containers on `PUT`; the recursion stops at the first existing
     * ancestor (the storage root always exists).
     *
     * [containerUri] should be a container URI (trailing `/`).
     *
     * @param webid The WebID of the authenticated user making the request.
     * @param containerUri The container to ensure, including any missing parents.
     */
    public suspend fun ensureContainer(webid: String, containerUri: URI): SolidResult<Unit> {
        when (val head = head(webid, containerUri)) {
            is SolidResult.Success -> return SolidResult.Success(Unit)
            is SolidResult.Failure ->
                if (head.error.code != SolidErrorCode.NOT_FOUND) return SolidResult.Failure(head.error)
        }
        parentContainerOfUri(containerUri)?.let { parent ->
            val parentResult = ensureContainer(webid, parent)
            if (parentResult is SolidResult.Failure) return parentResult
        }
        return create(webid, SolidContainer(containerUri)).map { }
    }

    /**
     * Reports the access the current user effectively holds on [uri], read from the
     * `WAC-Allow` header of a HEAD.
     *
     * `Success(`[AccessProbe.Accessible]`)` when reachable (carrying the granted modes and
     * `solid:owner`); `Success(`[AccessProbe.Denied]`)` for a definitive `403`/`404`; and
     * [SolidResult.Failure] for an *indeterminate* outcome (401 refresh blip, 5xx, transport
     * error) — a caller must not treat that as denial (e.g. keep, don't prune, stored rows).
     * A reachable resource that advertises no `WAC-Allow` is reported as View access.
     *
     * @param webid The WebID of the authenticated user making the request.
     * @param uri   The resource whose access to probe.
     */
    public suspend fun probeAccess(webid: String, uri: URI): SolidResult<AccessProbe> {
        val metadata = when (val head = head(webid, uri)) {
            is SolidResult.Success -> head.value
            is SolidResult.Failure ->
                return if (head.error.code == SolidErrorCode.FORBIDDEN ||
                    head.error.code == SolidErrorCode.NOT_FOUND
                ) {
                    SolidResult.Success(AccessProbe.Denied)
                } else {
                    SolidResult.Failure(head.error)
                }
        }
        val owner = metadata.ownerUri?.toString()
        val wac = metadata.wacAllow
            ?: return SolidResult.Success(AccessProbe.Accessible(setOf(ShareMode.READ), owner))
        val combined = wac.userModes + wac.publicModes
        val modes = buildSet {
            if ("read" in combined) add(ShareMode.READ)
            if ("append" in combined) add(ShareMode.APPEND)
            if ("write" in combined) add(ShareMode.WRITE)
        }
        return SolidResult.Success(
            if (modes.isEmpty()) AccessProbe.Denied else AccessProbe.Accessible(modes, owner),
        )
    }

    /**
     * Reads a resource from the pod.
     * @param webid The WebID of the authenticated user making the request.
     * @param resource The URI of the resource to read.
     * @param clazz The expected resource type (e.g. [com.erfangholami.androidsolidservices.shared.model.resource.RDFResource]).
     * @return [SolidResult.Success] with the resource, or a [SolidResult.Failure] carrying a typed [SolidError].
     */
    public suspend fun <T : Resource> read(
        webid: String,
        resource: URI,
        clazz: Class<T>,
    ): SolidResult<T>

    /**
     * Creates a new resource on the pod via conditional PUT (`If-None-Match: *`).
     *
     * Fails with 409 Conflict if a resource already exists at the target URI.
     *
     * @param webid The WebID of the authenticated user making the request.
     * @param resource The resource to create; its identifier determines the target URI.
     * @return [SolidResult.Success] with the created resource.
     */
    public suspend fun <T : Resource> create(
        webid: String,
        resource: T
    ): SolidResult<T>

    /**
     * Writes a resource via HTTP PUT.
     *
     * For RDF resources, prefer [patch] when only a subset of triples changes — it is
     * atomic and avoids a full read-modify-write cycle. Use [update] when you have the
     * complete new representation.
     *
     * The [ifMatch] argument controls the precondition header (RFC 7232) sent with the
     * PUT, and unlike [create], `update` does **not** require the resource to already
     * exist:
     *  - `null`  → no precondition; the PUT overwrites if present, creates if not. Use
     *    this for "upsert" semantics, and for endpoints (e.g. Inrupt's ACR endpoint)
     *    that reject bootstrap PUTs carrying `If-None-Match: *`.
     *  - `"*"`   → `If-Match: *`; the PUT only succeeds if the resource already exists.
     *    Use this for pure updates where you want to fail fast if it was deleted.
     *  - an ETag string → `If-Match: "<etag>"`; full optimistic concurrency, fails with
     *    `412 Precondition Failed` if the resource was modified since you read it.
     *
     * A 412 response is surfaced as-is on the result (no longer masked to 404).
     *
     * [ifUnmodifiedSince] is a coarser fallback for servers that only emit **weak**
     * ETags (e.g. Node Solid Server), where [ifMatch] can never match: pass the
     * `Last-Modified` value from the read to send `If-Unmodified-Since`, giving
     * one-second-granularity optimistic concurrency instead of none. It is applied
     * only when [ifMatch] is `null`; a strong ETag always takes precedence.
     *
     * @param webid    The WebID of the authenticated user making the request.
     * @param newResource The updated resource; its identifier determines the target URI.
     * @param ifMatch  See above. Defaults to `null` (unconditional PUT).
     * @param ifUnmodifiedSince Optional `Last-Modified` value for a weak-ETag fallback; see above.
     * @return [SolidResult.Success] with the updated resource.
     */
    public suspend fun <T : Resource> update(
        webid: String,
        newResource: T,
        ifMatch: String? = null,
        ifUnmodifiedSince: String? = null,
    ): SolidResult<T>

    /**
     * Applies an N3 Patch to an RDF resource on the pod via HTTP PATCH.
     *
     * This is the preferred method for partial updates to RDF resources — it is atomic
     * and does not require reading the full resource first. Use [N3Patch.build] or
     * [N3Patch.fromDiff] to construct the patch without writing raw N3 strings.
     *
     * Not applicable to [com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFSource] —
     * use [update] for binary resources.
     *
     * Pass [ifMatch] (the ETag from a previous [read] or [head] call) to issue a conditional
     * PATCH that fails with 412 if the resource was modified in the meantime.
     *
     * @param webid   The WebID of the authenticated user making the request.
     * @param uri     The URI of the RDF resource to patch.
     * @param patch   The patch to apply.
     * @param ifMatch Optional ETag for a conditional PATCH.
     * @return [SolidResult.Success] with [Unit] on success.
     */
    public suspend fun patch(
        webid: String,
        uri: URI,
        patch: N3Patch,
        ifMatch: String? = null,
    ): SolidResult<Unit>

    /**
     * Applies a pre-built N3 Patch body to an RDF resource on the pod via HTTP PATCH.
     *
     * Use this overload when the patch document has already been serialised to a `text/n3`
     * string (e.g. when transporting a patch over AIDL IPC and reconstructing it on the
     * service side). Prefer [patch] with a typed [N3Patch] when building patches in-process.
     *
     * Pass [ifMatch] (the ETag from a previous [read] or [head] call) to issue a conditional
     * PATCH that fails with 412 if the resource was modified in the meantime.
     *
     * @param webid     The WebID of the authenticated user making the request.
     * @param uri       The URI of the RDF resource to patch.
     * @param n3Body    The full `text/n3` patch document body.
     * @param ifMatch   Optional ETag for a conditional PATCH.
     * @return [SolidResult.Success] with [Unit] on success.
     */
    public suspend fun patchRaw(
        webid: String,
        uri: URI,
        n3Body: String,
        ifMatch: String? = null,
    ): SolidResult<Unit>

    /**
     * Deletes a resource or container from the pod.
     *
     * When [resource] is a container (or its URI ends with `/`), all contained resources
     * are deleted recursively before the container itself is removed.
     *
     * @param webid The WebID of the authenticated user making the request.
     * @param resource The resource to delete.
     * @return [SolidResult.Success] with the deleted resource.
     */
    public suspend fun <T : Resource> delete(
        webid: String,
        resource: T,
    ): SolidResult<T>

    /**
     * Deletes a resource or container from the pod by URI.
     *
     * When [resourceUri] ends with `/`, the target is treated as a container and all
     * contained resources are deleted recursively before the container itself is removed.
     *
     * Pass [ifMatch] (an ETag from a previous [read] or [head]) for an optimistic-concurrency
     * delete that fails with [SolidError.PreconditionFailed] (HTTP 412) if the resource changed
     * since it was read — so a delete can't silently discard a concurrent edit. [ifMatch] is
     * honoured only for a single (non-container) resource; a recursive container delete can't be
     * performed atomically under one precondition, so it is ignored for container URIs.
     *
     * @param webid The WebID of the authenticated user making the request.
     * @param resourceUri The URI of the resource or container to delete.
     * @param ifMatch Optional ETag for a conditional delete of a single resource.
     * @return [SolidResult.Success] with `true` on success.
     */
    public suspend fun delete(
        webid: String,
        resourceUri: URI,
        ifMatch: String? = null,
    ): SolidResult<Boolean>

    /**
     * Reads a **public** resource without any authentication. Use this for
     * documents that are world-readable by the Solid spec — most notably
     * foreign WebID profile documents — where sending an Authorization /
     * DPoP header would be at best ignored and at worst rejected by the
     * target server (e.g. Inrupt PodSpaces returns 401 for foreign-issuer
     * tokens against `/erfangh`).
     *
     * For accessing the current user's *own* WebID or any access-controlled
     * resource, use [read] instead.
     *
     * @param uri   The URI of the resource to read.
     * @param clazz The expected resource type.
     */
    public suspend fun <T : Resource> readPublic(
        uri: URI,
        clazz: Class<T>,
    ): SolidResult<T>

    /**
     * HEADs a **public** resource without any authentication. Used as a
     * fallback when [readPublic] can't recover the value of interest from
     * the body (e.g. when the inbox link is only advertised in a
     * `Link: rel="http://www.w3.org/ns/ldp#inbox"` header).
     *
     * @param uri The URI of the resource to HEAD.
     */
    public suspend fun headPublic(uri: URI): SolidResult<SolidMetadata>

    /**
     * PUTs an opaque body to [uri] as a DPoP-authenticated user.
     *
     * The high-level [update] is the right entry point most of the time, but
     * some servers (notably Inrupt PodSpaces' ACR endpoint) reject the
     * compacted JSON-LD that [update] produces with a `400 "invalid ACR
     * format"`. For those endpoints the safest cross-server format is
     * `application/n-triples`: no remote contexts, no compaction, no aliases
     * — just `<s> <p> <o> .` lines a Solid server can validate directly.
     *
     * @param webid       The WebID of the authenticated user.
     * @param uri         Target resource URI.
     * @param contentType Media type sent on `Content-Type`.
     * @param body        Bytes to send as the request body.
     * @param ifMatch     `null` → unconditional; `"*"` → require existence;
     *                    ETag → optimistic concurrency. Same semantics as
     *                    [update].
     * @param linkHeader  Optional `Link:` header (e.g. for typed PUTs).
     */
    public suspend fun putRaw(
        webid: String,
        uri: URI,
        contentType: String,
        body: ByteArray,
        ifMatch: String? = null,
        linkHeader: String? = null,
    ): SolidResult<Unit>

    /**
     * POSTs an opaque body to [uri] as a DPoP-authenticated user.
     *
     * Used primarily for LDN inbox writes (`as:Offer`, `as:Undo`,
     * `solidshare:AccessRequest`, `as:Reject`) where the target is a
     * container rather than a specific resource, and the server allocates
     * the new resource's URI.
     *
     * @param webid The WebID of the authenticated user making the request.
     * @param uri The container URI to POST to.
     * @param contentType The media type of [body].
     * @param body The bytes to send.
     * @param additionalHeaders Extra HTTP headers (e.g. `Slug`).
     * @return [SolidResult.Success] with the server-allocated
     *   resource's `Location` URI on 2xx (may be null if the server didn't
     *   return one), or a [SolidResult.Failure] carrying a typed [SolidError].
     */
    public suspend fun post(
        webid: String,
        uri: URI,
        contentType: String,
        body: ByteArray,
        additionalHeaders: Map<String, String> = emptyMap(),
    ): SolidResult<URI?>

    /**
     * Creates a new member inside the container at [containerUri] by POSTing
     * [resource] to the container, letting the **server** allocate the child URI.
     *
     * Prefer this over [create] when writing into a container you do not own but
     * have been granted **Add** (`acl:Append`) access to: [create] issues a PUT to a
     * fixed URI, which requires **Write**, whereas POSTing a new member only requires
     * Append — so an add-only recipient can contribute to a shared container without
     * being able to overwrite existing resources.
     *
     * The [resource]'s identifier is used only to derive a `Slug` hint for the
     * server; the authoritative URI is the one the server returns.
     *
     * @param webid The WebID of the authenticated user making the request.
     * @param containerUri The container to POST the new member into.
     * @param resource The resource to create; its content and content-type are sent,
     *   its identifier supplies only a `Slug` hint.
     * @return [SolidResult.Success] with the server-allocated member URI
     *   (its `Location`), which may be `null` if the server did not return one.
     */
    public suspend fun <T : Resource> createInContainer(
        webid: String,
        containerUri: URI,
        resource: T,
    ): SolidResult<URI?>
}

/**
 * The parent container URI of [uri] (with a trailing `/`), or `null` when [uri] is the
 * storage root or has no parent path segment. Drives [SolidResourceManager.ensureContainer]'s
 * bottom-up recursion.
 */
private fun parentContainerOfUri(uri: URI): URI? {
    val text = uri.toString()
    val schemeEnd = text.indexOf("://")
    if (schemeEnd < 0) return null
    val trimmed = text.trimEnd('/')
    val lastSlash = trimmed.lastIndexOf('/')
    if (lastSlash <= schemeEnd + 2) return null
    return runCatching { URI.create(trimmed.substring(0, lastSlash + 1)) }.getOrNull()
}
