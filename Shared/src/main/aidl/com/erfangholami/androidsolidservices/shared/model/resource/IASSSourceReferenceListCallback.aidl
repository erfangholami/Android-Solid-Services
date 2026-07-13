package com.erfangholami.androidsolidservices.shared.model.resource;

import com.erfangholami.androidsolidservices.shared.model.resource.SolidSourceReference;

/** One-way callback delivering the entries of a container listing. */
interface IASSSourceReferenceListCallback {
    oneway void onResult(in @nullable List<SolidSourceReference> entries);
    oneway void onError(int errorCode, String errorMessage);
}
