package com.erfangholami.androidsolidservices.shared.model.resource;

import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata;

/** Callback delivering resource metadata (headers, ETag, content-type) from a HEAD request. */
interface IASSSolidMetadataCallback {
    void onResult(in SolidMetadata result);
    void onError(int errorCode, String errorMessage);
}
