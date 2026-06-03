package com.erfangholami.androidsolidservices.shared.model.resource;

import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer;

/** Callback delivering a SolidContainer (LDP container with its contained resource list) from a resource operation. */
interface IASSContainerCallback {
    void onResult(in SolidContainer result);
    void onError(int errorCode, String errorMessage);
}
