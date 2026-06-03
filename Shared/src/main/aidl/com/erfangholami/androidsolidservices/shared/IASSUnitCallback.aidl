package com.erfangholami.androidsolidservices.shared;
/** Generic one-way callback for operations that succeed without a return value. */
interface IASSUnitCallback {
    void onResult();
    void onError(int errorCode, String errorMessage);
}
