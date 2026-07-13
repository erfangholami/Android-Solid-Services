package com.erfangholami.androidsolidservices.shared;

import com.erfangholami.androidsolidservices.shared.model.resource.IASSSolidNonRdfResourceCallback;
import com.erfangholami.androidsolidservices.shared.model.resource.IASSSolidRdfResourceCallback;
import com.erfangholami.androidsolidservices.shared.model.resource.IASSSolidMetadataCallback;
import com.erfangholami.androidsolidservices.shared.model.resource.IASSContainerCallback;
import com.erfangholami.androidsolidservices.shared.IASSBooleanCallback;
import com.erfangholami.androidsolidservices.shared.IASSStringCallback;
import com.erfangholami.androidsolidservices.shared.model.resource.IASSAccessProbeCallback;
import com.erfangholami.androidsolidservices.shared.model.resource.IASSSourceReferenceListCallback;
import com.erfangholami.androidsolidservices.shared.model.resource.IASSStreamCallback;
import com.erfangholami.androidsolidservices.shared.IASSUnitCallback;
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource;
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource;
import com.erfangholami.androidsolidservices.shared.model.resource.AccessProbe;
import com.erfangholami.androidsolidservices.shared.model.resource.SolidSourceReference;

/**
 * AIDL IPC contract for Solid pod resource operations. Provides cross-process access to the
 * full resource surface — CRUD, head, patch, container listing, the derived container verbs
 * (exists / ensureContainer / probeAccess / listContainer / copy / move / rename) and
 * streaming reads and writes. Results are delivered via per-operation one-way callbacks.
 * Third-party apps normally use the higher-level client SDK rather than binding here directly.
 *
 * Two design notes:
 *
 * 1. The *derived* verbs are executed SERVER-SIDE. They are composed from several HTTP calls
 *    (a recursive copy walks a whole tree), so running them in the service costs one IPC
 *    round trip instead of N.
 *
 * 2. Streaming does NOT parcel the body — it would not survive the ~1 MB Binder transaction
 *    limit. Bytes travel through a pipe as a ParcelFileDescriptor, so a multi-megabyte
 *    download or upload never has to be materialised in memory on either side.
 */
interface IASSResourceService {
    void getWebId(String webId, IASSSolidRdfResourceCallback callback);

    void head(String webId, String resourceUrl, IASSSolidMetadataCallback callback);

    void create(String webId, in SolidNonRDFResource resource, IASSSolidNonRdfResourceCallback callback);
    void createRdf(String webId, in SolidRDFResource resource, IASSSolidRdfResourceCallback callback);

    void read(String webId, String resourceUrl, IASSSolidNonRdfResourceCallback callback);
    void readRdf(String webId, String resourceUrl, IASSSolidRdfResourceCallback callback);
    void readContainer(String webId, String containerUrl, IASSContainerCallback callback);

    /** ifMatch is the ETag of the current server version; the update is rejected with a 412 if it does not match. */
    void update(String webId, in SolidNonRDFResource resource, String ifMatch, IASSSolidNonRdfResourceCallback callback);
    /** ifMatch is the ETag of the current server version; the update is rejected with a 412 if it does not match. */
    void updateRdf(String webId, in SolidRDFResource resource, String ifMatch, IASSSolidRdfResourceCallback callback);
    /** Applies an N3 Patch to the resource; patchBody must be a valid text/n3 patch document. */
    void patch(String webId, String resourceUrl, String patchBody, IASSUnitCallback callback);

    void delete(String webId, in SolidNonRDFResource resource, IASSSolidNonRdfResourceCallback callback);
    void deleteRdf(String webId, in SolidRDFResource resource, IASSSolidRdfResourceCallback callback);
    void deleteContainer(String webId, String containerUrl, IASSUnitCallback callback);

    // ---- Derived verbs, executed server-side (one round trip instead of N) ----

    /** true if the resource exists; a 404 is `false`, anything indeterminate is an error. */
    void exists(String webId, String uri, IASSBooleanCallback callback);

    /** Creates the container and any missing ancestors, bottom-up. Idempotent. */
    void ensureContainer(String webId, String containerUri, IASSUnitCallback callback);

    /**
     * Reports the access the user effectively holds, from the resource's WAC-Allow header.
     * An indeterminate outcome arrives on onError — it is NOT AccessProbe.Denied.
     */
    void probeAccess(String webId, String uri, IASSAccessProbeCallback callback);

    /**
     * Lists a container's direct children in a single GET. Pass enrichWithHead to also HEAD
     * each child (bounded concurrency) for servers that don't enrich the listing.
     */
    void listContainer(
        String webId,
        String containerUri,
        boolean enrichWithHead,
        IASSSourceReferenceListCallback callback
    );

    /** Copies a resource, or a whole container tree, to destinationUri. Not transactional. */
    void copy(String webId, String sourceUri, String destinationUri, IASSStringCallback callback);

    /** Copy followed by a delete of the source. Not transactional. */
    void move(String webId, String sourceUri, String destinationUri, IASSStringCallback callback);

    /** Moves a resource to a sibling name in the same container. */
    void rename(String webId, String sourceUri, String newName, IASSStringCallback callback);

    // ---- Unauthenticated reads ----

    /** Reads a world-readable resource with no Authorization header (e.g. a foreign WebID doc). */
    void readPublicRdf(String uri, IASSSolidRdfResourceCallback callback);

    /** Reads a world-readable binary with no Authorization header. */
    void readPublic(String uri, IASSSolidNonRdfResourceCallback callback);

    /** HEADs a world-readable resource with no Authorization header. */
    void headPublic(String uri, IASSSolidMetadataCallback callback);

    // ---- Raw writes ----

    /** PUTs an opaque body. ifMatch: null = unconditional, "*" = must exist, ETag = CAS. */
    void putRaw(
        String webId,
        String uri,
        String contentType,
        in byte[] body,
        @nullable String ifMatch,
        @nullable String linkHeader,
        IASSUnitCallback callback
    );

    /** POSTs an opaque body to a container; returns the server-allocated Location. */
    void post(
        String webId,
        String uri,
        String contentType,
        in byte[] body,
        in @nullable Bundle additionalHeaders,
        IASSStringCallback callback
    );

    /**
     * Creates a member by POSTing to the container, letting the server allocate the URI.
     * Needs only Append (not Write), so an add-only recipient can contribute to a shared
     * container. Returns the allocated Location.
     */
    void createInContainer(
        String webId,
        String containerUri,
        in SolidNonRDFResource resource,
        IASSStringCallback callback
    );

    /** RDF flavour of createInContainer. */
    void createInContainerRdf(
        String webId,
        String containerUri,
        in SolidRDFResource resource,
        IASSStringCallback callback
    );

    // ---- Streaming (bytes travel through a pipe, never through a parcel) ----

    /**
     * Opens the resource body as a live stream. The service pipes the bytes across; the
     * caller reads the returned descriptor and must close it.
     */
    void readStream(String webId, String uri, IASSStreamCallback callback);

    /**
     * Writes a resource by streaming its body from the supplied descriptor.
     *
     * Pass contentLength = -1 when unknown. The service spools the incoming bytes to a
     * temporary file before uploading, because a pipe can only be read once and the HTTP
     * request may legitimately be re-sent (DPoP-nonce priming, token refresh).
     */
    void writeStream(
        String webId,
        String uri,
        String contentType,
        long contentLength,
        in ParcelFileDescriptor source,
        @nullable String ifMatch,
        IASSUnitCallback callback
    );
}
