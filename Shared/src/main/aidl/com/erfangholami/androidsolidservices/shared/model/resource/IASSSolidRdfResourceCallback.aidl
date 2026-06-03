package com.erfangholami.androidsolidservices.shared.model.resource;

import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource;

/** Callback delivering an RDF Solid resource (Turtle, JSON-LD, or other RDF serialization). */
interface IASSSolidRdfResourceCallback  {
    void onResult(in SolidRDFResource result);
    void onError(int errorCode, String errorMessage);
}