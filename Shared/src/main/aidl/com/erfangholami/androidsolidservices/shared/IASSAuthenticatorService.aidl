package com.erfangholami.androidsolidservices.shared;

import com.erfangholami.androidsolidservices.shared.model.auth.IASSLoginCallback;
import com.erfangholami.androidsolidservices.shared.model.auth.IASSLogoutCallback;

/**
 * AIDL IPC contract for Solid authentication. Bound by the Android Solid Services app and
 * consumed across processes to check sign-in state, request login via the browser-based
 * Solid-OIDC flow, and disconnect an account. Results are delivered via one-way callbacks.
 * Third-party apps normally use the higher-level client SDK rather than binding here directly.
 */
interface IASSAuthenticatorService {

    boolean hasLoggedIn();
    boolean isAppAuthorized(String webId);
    void requestLogin(IASSLoginCallback callback);
    void disconnectFromSolid(String webId, IASSLogoutCallback callback);
}