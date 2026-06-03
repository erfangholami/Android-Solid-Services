package com.erfangholami.androidsolidservices.shared.model.auth;

/** One-way callback for the Solid login flow; delivers the authorization result and the selected WebID. */
oneway interface IASSLoginCallback {
    void onResult(boolean granted, String selectedWebId);
    void onError(int errorCode, String errorMessage);
}