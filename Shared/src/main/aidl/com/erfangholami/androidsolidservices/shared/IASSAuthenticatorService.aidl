package com.erfangholami.androidsolidservices.shared;

import com.erfangholami.androidsolidservices.shared.IASSParcelableCallback;

/**
 * AIDL IPC contract for Solid authentication. Bound by the Android Solid Services app and
 * consumed across processes to check sign-in state, request login via the browser-based
 * Solid-OIDC flow, and disconnect an account. Results are delivered on the two generic callbacks, IASSParcelableCallback and
 * IASSParcelableListCallback, whose Bundle envelope is described by
 * `shared/ipc/IpcEnvelope.kt`.
 * Third-party apps normally use the higher-level client SDK rather than binding here directly.
 */
interface IASSAuthenticatorService {

    boolean hasLoggedIn();
    boolean isAppAuthorized(String webId);
    void requestLogin(IASSParcelableCallback callback);
    void disconnectFromSolid(String webId, IASSParcelableCallback callback);
}