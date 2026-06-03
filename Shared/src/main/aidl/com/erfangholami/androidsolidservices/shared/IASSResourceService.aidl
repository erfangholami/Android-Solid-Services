package com.erfangholami.androidsolidservices.shared;

import com.erfangholami.androidsolidservices.shared.model.resource.IASSSolidNonRdfResourceCallback;
import com.erfangholami.androidsolidservices.shared.model.resource.IASSSolidRdfResourceCallback;
import com.erfangholami.androidsolidservices.shared.model.resource.IASSSolidMetadataCallback;
import com.erfangholami.androidsolidservices.shared.model.resource.IASSContainerCallback;
import com.erfangholami.androidsolidservices.shared.IASSUnitCallback;
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource;
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource;

/**
 * AIDL IPC contract for Solid pod resource operations. Provides cross-process CRUD access
 * (create, read, update, delete, head, patch, container listing) on Solid resources, both
 * RDF and non-RDF. Results are delivered via per-operation one-way callbacks. Third-party
 * apps normally use the higher-level client SDK rather than binding here directly.
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
}
