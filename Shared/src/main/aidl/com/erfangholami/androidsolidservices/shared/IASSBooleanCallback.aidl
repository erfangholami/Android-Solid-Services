package com.erfangholami.androidsolidservices.shared;

/** One-way callback delivering a boolean outcome (e.g. from `exists`). */
interface IASSBooleanCallback {
    oneway void onResult(boolean value);
    oneway void onError(int errorCode, String errorMessage);
}
