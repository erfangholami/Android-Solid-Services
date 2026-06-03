package com.erfangholami.androidsolidservices.shared.model.sharing;

import com.erfangholami.androidsolidservices.shared.model.sharing.AccessGrant;

/** One-way callback delivering the list of pending access grants for the user's WebID. */
interface IASSAccessGrantListCallback {
    oneway void onResult(in List<AccessGrant> grants);
    oneway void onError(int errorCode, String errorMessage);
}
