package com.erfangholami.androidsolidservices.shared.model.auth;

/** Callback for the Solid disconnect flow; delivers whether the session was successfully terminated. */
interface IASSLogoutCallback {
     void onResult(boolean granted);
     void onError(int errorCode, String errorMessage);
}