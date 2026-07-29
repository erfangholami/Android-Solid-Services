package com.erfangholami.androidsolidservices.api.resource

import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.resource.implementation.SolidResourceManagerImplementation
import com.erfangholami.androidsolidservices.shared.model.resource.AccessProbe
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidSourceReference
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.result.SolidErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.util.encodeUriString
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.InputStream

/**
 * Performs authenticated CRUD operations on Solid pod resources on behalf of a specific user.
 *
 * All operations require the user identified by `webId` to have an active, authorized
 * [Authenticator] session.  Results are wrapped in [SolidResult] so callers can
 * distinguish HTTP errors from unexpected exceptions without catching throwables.
 *
 * Resource locations are passed as plain `String` IRIs (matching `webId`); the library encodes
 * them at the boundary via [encodeUriString] (idempotent), so callers store and pass decoded
 * identifiers and never construct or pre-encode a java.net.URI themselves.
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
     * @param webId The WebID of the authenticated user making the request.
     * @param uri   The URI of the resource to HEAD.
     * @return [SolidResult.Success] with [SolidMetadata], or a [SolidResult.Failure] carrying a typed [SolidError].
     */
    public suspend fun head(
        webId: String,
        uri: String,
    ): SolidResult<SolidMetadata>

    /**
     * Reports whether a resource exists at [uri].
     *
     * A HEAD that resolves (2xx) → `Success(true)`; a `404 Not Found` → `Success(false)`.
     * Any other outcome (403, auth, network, 5xx) is *indeterminate* and surfaces as
     * [SolidResult.Failure] rather than being collapsed to `false`, so callers don't
     * mistake "couldn't tell" for "absent".
     *
     * @param webId The WebID of the authenticated user making the request.
     * @param uri   The URI to probe.
     */
    public suspend fun exists(webId: String, uri: String): SolidResult<Boolean> =
        when (val head = head(webId, uri)) {
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
     * @param webId The WebID of the authenticated user making the request.
     * @param containerUri The container to ensure, including any missing parents.
     */
    public suspend fun ensureContainer(webId: String, containerUri: String): SolidResult<Unit> {
        when (val head = head(webId, containerUri)) {
            is SolidResult.Success -> return SolidResult.Success(Unit)
            is SolidResult.Failure ->
                if (head.error.code != SolidErrorCode.NOT_FOUND) return SolidResult.Failure(head.error)
        }
        parentContainerOfUri(containerUri)?.let { parent ->
            val parentResult = ensureContainer(webId, parent)
            if (parentResult is SolidResult.Failure) return parentResult
        }
        return create(webId, SolidContainer(containerUri)).map { }
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
     * @param webId The WebID of the authenticated user making the request.
     * @param uri   The resource whose access to probe.
     */
    public suspend fun probeAccess(webId: String, uri: String): SolidResult<AccessProbe> {
        val metadata = when (val head = head(webId, uri)) {
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
        val owner = metadata.ownerUri
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
     * Lists the resources directly contained in the container at [containerUri].
     *
     * This is a **single** GET: each [SolidSourceReference] is built from the container's
     * own representation (which Solid servers SHOULD enrich with `stat:size` /
     * `dcterms:modified` / `rdf:type`), so it avoids the 1-GET-plus-N-HEAD fan-out of
     * heading every child. Pass [enrichWithHead] to additionally HEAD each child — bounded
     * to a few concurrent requests — filling [SolidSourceReference.headMetadata] for servers
     * that don't enrich the listing; leave it off (default) for the cheap single call.
     *
     * @param webId The WebID of the authenticated user making the request.
     * @param containerUri The container to list (trailing `/`).
     * @param enrichWithHead When `true`, HEAD each child (bounded concurrency) for full metadata.
     */
    public suspend fun listContainer(
        webId: String,
        containerUri: String,
        enrichWithHead: Boolean = false,
    ): SolidResult<List<SolidSourceReference>> {
        val children = when (val r = read(webId, containerUri, SolidContainer::class.java)) {
            is SolidResult.Success -> r.value.getContained()
            is SolidResult.Failure -> return SolidResult.Failure(r.error)
        }
        if (!enrichWithHead || children.isEmpty()) return SolidResult.Success(children)
        val enriched = coroutineScope {
            children.chunked(CONTAINER_FANOUT_LIMIT).flatMap { batch ->
                batch.map { ref ->
                    async {
                        head(webId, ref.identifier).getOrNull()
                            ?.let { ref.copy(headMetadata = it) } ?: ref
                    }
                }.awaitAll()
            }
        }
        return SolidResult.Success(enriched)
    }

    /**
     * Copies the resource (or whole container tree) at [sourceUri] to [destinationUri].
     *
     * Server-agnostic — it reads each leaf's bytes and re-`PUT`s them verbatim (preserving
     * content-type for both RDF and binary resources) rather than relying on the non-standard
     * `COPY` verb; a container is recreated and its children copied (bounded concurrency).
     *
     * @return [SolidResult.Success] with [destinationUri] on a fully-copied tree, or the first
     *   [SolidResult.Failure] encountered (a partially-copied tree may remain — the copy is not
     *   transactional).
     */
    public suspend fun copy(
        webId: String,
        sourceUri: String,
        destinationUri: String,
    ): SolidResult<String> = when (val result = copyTree(webId, sourceUri, destinationUri)) {
        is SolidResult.Success -> SolidResult.Success(encodeUriString(destinationUri).toString())
        is SolidResult.Failure -> result
    }

    /**
     * Moves the resource (or container tree) at [sourceUri] to [destinationUri]: a [copy]
     * followed by a [delete] of the source. **Not transactional** — if the copy succeeds but
     * the source delete fails, both locations exist and the failure is returned so the caller
     * can retry the delete.
     */
    public suspend fun move(
        webId: String,
        sourceUri: String,
        destinationUri: String,
    ): SolidResult<String> = when (val copied = copy(webId, sourceUri, destinationUri)) {
        is SolidResult.Failure -> copied
        is SolidResult.Success -> when (val deleted = delete(webId, sourceUri)) {
            is SolidResult.Success -> SolidResult.Success(encodeUriString(destinationUri).toString())
            is SolidResult.Failure -> deleted
        }
    }

    /**
     * Renames the resource (or container) at [sourceUri] to [newName], keeping it in the same
     * parent container — a [move] to the sibling URI. Returns the source URI unchanged when it
     * has no parent (a storage root can't be renamed).
     */
    public suspend fun rename(
        webId: String,
        sourceUri: String,
        newName: String,
    ): SolidResult<String> {
        val isContainer = sourceUri.endsWith("/")
        val parent = parentContainerOfUri(sourceUri) ?: return SolidResult.Success(encodeUriString(sourceUri).toString())
        val name = if (isContainer && !newName.endsWith("/")) "$newName/" else newName
        return move(webId, sourceUri, "$parent$name")
    }

    /**
     * Reads the resource at [uri] as an unbuffered [StreamingResource] — its body is exposed
     * as a live stream rather than materialised into a `ByteArray`, so large downloads don't
     * sit in memory. Track download progress by counting bytes as you read the stream. The
     * caller **must** [StreamingResource.close] it (the network response stays open until then).
     *
     * The default implementation falls back to a buffered [read]; the production manager
     * overrides it to stream straight off the network.
     *
     * @param webId The WebID of the authenticated user making the request.
     * @param uri   The resource to read.
     */
    public suspend fun readStream(webId: String, uri: String): SolidResult<StreamingResource> =
        when (val r = read(webId, uri, SolidNonRDFResource::class.java)) {
            is SolidResult.Success -> {
                val res = r.value
                val length = res.getSize().let { if (it > 0) it else -1L }
                SolidResult.Success(StreamingResource(uri, res.getContentType(), length, res.getEntity()) {})
            }
            is SolidResult.Failure -> SolidResult.Failure(r.error)
        }

    /**
     * Writes a resource at [uri] by streaming its body from [openSource] rather than holding
     * it all in memory, invoking [onProgress] as bytes are sent.
     *
     * [openSource] must return a **fresh** stream each time it is called: the request may be
     * re-sent (DPoP-nonce priming, token refresh), and each attempt re-opens the source. Pass
     * [contentLength] when known (enables a definite `Content-Length` and a total in
     * [onProgress]); omit it for chunked transfer. [ifMatch] applies the usual conditional-write
     * precondition.
     *
     * The default implementation buffers [openSource] and delegates to [putRaw]; the production
     * manager overrides it to stream straight to the network.
     *
     * @param onProgress Called with (bytes written so far, total or `null` when unknown).
     * @param openSource Factory returning a fresh body stream on each call.
     */
    public suspend fun writeStream(
        webId: String,
        uri: String,
        contentType: String,
        contentLength: Long? = null,
        ifMatch: String? = null,
        onProgress: ((bytesWritten: Long, total: Long?) -> Unit)? = null,
        openSource: () -> InputStream,
    ): SolidResult<Unit> {
        val bytes = openSource().use { it.readBytes() }
        onProgress?.invoke(bytes.size.toLong(), bytes.size.toLong())
        return putRaw(webId, uri, contentType, bytes, ifMatch, null)
    }

    /**
     * Reads a resource from the pod.
     * @param webId The WebID of the authenticated user making the request.
     * @param resource The URI of the resource to read.
     * @param clazz The expected resource type (e.g. [com.erfangholami.androidsolidservices.shared.model.resource.RDFResource]).
     * @return [SolidResult.Success] with the resource, or a [SolidResult.Failure] carrying a typed [SolidError].
     */
    public suspend fun <T : Resource> read(
        webId: String,
        resource: String,
        clazz: Class<T>,
    ): SolidResult<T>

    /**
     * Creates a new resource on the pod via conditional PUT (`If-None-Match: *`).
     *
     * Fails with 409 Conflict if a resource already exists at the target URI.
     *
     * @param webId The WebID of the authenticated user making the request.
     * @param resource The resource to create; its identifier determines the target URI.
     * @return [SolidResult.Success] with the created resource.
     */
    public suspend fun <T : Resource> create(
        webId: String,
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
     * @param webId    The WebID of the authenticated user making the request.
     * @param newResource The updated resource; its identifier determines the target URI.
     * @param ifMatch  See above. Defaults to `null` (unconditional PUT).
     * @param ifUnmodifiedSince Optional `Last-Modified` value for a weak-ETag fallback; see above.
     * @return [SolidResult.Success] with the updated resource.
     */
    public suspend fun <T : Resource> update(
        webId: String,
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
     * @param webId   The WebID of the authenticated user making the request.
     * @param uri     The URI of the RDF resource to patch.
     * @param patch   The patch to apply.
     * @param ifMatch Optional ETag for a conditional PATCH.
     * @return [SolidResult.Success] with [Unit] on success.
     */
    public suspend fun patch(
        webId: String,
        uri: String,
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
     * @param webId     The WebID of the authenticated user making the request.
     * @param uri       The URI of the RDF resource to patch.
     * @param n3Body    The full `text/n3` patch document body.
     * @param ifMatch   Optional ETag for a conditional PATCH.
     * @return [SolidResult.Success] with [Unit] on success.
     */
    public suspend fun patchRaw(
        webId: String,
        uri: String,
        n3Body: String,
        ifMatch: String? = null,
    ): SolidResult<Unit>

    /**
     * Deletes a resource or container from the pod.
     *
     * When [resource] is a container (or its URI ends with `/`), all contained resources
     * are deleted recursively before the container itself is removed.
     *
     * @param webId The WebID of the authenticated user making the request.
     * @param resource The resource to delete.
     * @return [SolidResult.Success] with the deleted resource.
     */
    public suspend fun <T : Resource> delete(
        webId: String,
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
     * @param webId The WebID of the authenticated user making the request.
     * @param resourceUri The URI of the resource or container to delete.
     * @param ifMatch Optional ETag for a conditional delete of a single resource.
     * @return [SolidResult.Success] with `true` on success.
     */
    public suspend fun delete(
        webId: String,
        resourceUri: String,
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
        uri: String,
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
    public suspend fun headPublic(uri: String): SolidResult<SolidMetadata>

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
     * @param webId       The WebID of the authenticated user.
     * @param uri         Target resource URI.
     * @param contentType Media type sent on `Content-Type`.
     * @param body        Bytes to send as the request body.
     * @param ifMatch     `null` → unconditional; `"*"` → require existence;
     *                    ETag → optimistic concurrency. Same semantics as
     *                    [update].
     * @param linkHeader  Optional `Link:` header (e.g. for typed PUTs).
     */
    public suspend fun putRaw(
        webId: String,
        uri: String,
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
     * @param webId The WebID of the authenticated user making the request.
     * @param uri The container URI to POST to.
     * @param contentType The media type of [body].
     * @param body The bytes to send.
     * @param additionalHeaders Extra HTTP headers (e.g. `Slug`).
     * @return [SolidResult.Success] with the server-allocated
     *   resource's `Location` URI on 2xx (may be null if the server didn't
     *   return one), or a [SolidResult.Failure] carrying a typed [SolidError].
     */
    public suspend fun post(
        webId: String,
        uri: String,
        contentType: String,
        body: ByteArray,
        additionalHeaders: Map<String, String> = emptyMap(),
    ): SolidResult<String?>

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
     * @param webId The WebID of the authenticated user making the request.
     * @param containerUri The container to POST the new member into.
     * @param resource The resource to create; its content and content-type are sent,
     *   its identifier supplies only a `Slug` hint.
     * @return [SolidResult.Success] with the server-allocated member URI
     *   (its `Location`), which may be `null` if the server did not return one.
     */
    public suspend fun <T : Resource> createInContainer(
        webId: String,
        containerUri: String,
        resource: T,
    ): SolidResult<String?>

    private suspend fun copyTree(webId: String, source: String, dest: String): SolidResult<Unit> {
        if (!source.endsWith("/")) {
            val resource = when (val read = read(webId, source, SolidNonRDFResource::class.java)) {
                is SolidResult.Success -> read.value
                is SolidResult.Failure -> return SolidResult.Failure(read.error)
            }
            val bytes = resource.getEntity().use { it.readBytes() }
            return putRaw(webId, dest, resource.getContentType(), bytes)
        }
        val ensured = ensureContainer(webId, dest)
        if (ensured is SolidResult.Failure) return ensured
        val children = when (val list = listContainer(webId, source)) {
            is SolidResult.Success -> list.value
            is SolidResult.Failure -> return SolidResult.Failure(list.error)
        }
        val sourceStr = source
        val destStr = dest.let { if (it.endsWith("/")) it else "$it/" }
        val results = coroutineScope {
            children.chunked(CONTAINER_FANOUT_LIMIT).flatMap { batch ->
                batch.map { child ->
                    async {
                        val rel = child.identifier.removePrefix(sourceStr)
                        copyTree(webId, child.identifier, "$destStr$rel")
                    }
                }.awaitAll()
            }
        }
        return results.firstOrNull { it is SolidResult.Failure } ?: SolidResult.Success(Unit)
    }
}

private const val CONTAINER_FANOUT_LIMIT = 8

private fun parentContainerOfUri(uri: String): String? {
    val schemeEnd = uri.indexOf("://")
    if (schemeEnd < 0) return null
    val trimmed = uri.trimEnd('/')
    val lastSlash = trimmed.lastIndexOf('/')
    if (lastSlash <= schemeEnd + 2) return null
    return trimmed.substring(0, lastSlash + 1)
}
