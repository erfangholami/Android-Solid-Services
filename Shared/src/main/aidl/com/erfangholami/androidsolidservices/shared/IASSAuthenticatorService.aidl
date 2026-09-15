package com.erfangholami.androidsolidservices.shared;

import com.erfangholami.androidsolidservices.shared.IASSParcelableCallback;

/**
 * AIDL IPC contract for Solid authentication. Bound by the host app and consumed across
 * processes to check sign-in state, read the calling app's grant, and disconnect an account.
 * Results are delivered on the generic IASSParcelableCallback, whose Bundle envelope is
 * described by `shared/ipc/IpcEnvelope.kt`.
 * Third-party apps normally use the higher-level client SDK rather than binding here directly.
 */
interface IASSAuthenticatorService {

    boolean hasLoggedIn();
    boolean isAppAuthorized(String webId);
    /** The calling app's AppGrant for webId, or an empty answer when it holds none. */
    void getAppGrant(String webId, IASSParcelableCallback callback);
    void disconnectFromSolid(String webId, IASSParcelableCallback callback);
}
