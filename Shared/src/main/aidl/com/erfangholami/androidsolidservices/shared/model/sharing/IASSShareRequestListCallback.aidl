package com.erfangholami.androidsolidservices.shared.model.sharing;

import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest;

/** One-way callback delivering the list of pending share requests (inbound requests from others asking for access). */
interface IASSShareRequestListCallback {
    oneway void onResult(in List<ShareRequest> requests);
    oneway void onError(int errorCode, String errorMessage);
}
