package com.erfangholami.androidsolidservices.shared.model.resource;

import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource;

/** Callback delivering a non-RDF Solid resource (binary or arbitrary content-type). */
interface IASSSolidNonRdfResourceCallback  {
    void onResult(in SolidNonRDFResource result);
    void onError(int errorCode, String errorMessage);
}