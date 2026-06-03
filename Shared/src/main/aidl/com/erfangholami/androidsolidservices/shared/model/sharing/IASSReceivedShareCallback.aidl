package com.erfangholami.androidsolidservices.shared.model.sharing;

import com.erfangholami.androidsolidservices.shared.model.sharing.ReceivedShare;

/** One-way callback delivering a single ReceivedShare (an inbound share another user granted to this user). */
interface IASSReceivedShareCallback {
    oneway void onResult(in @nullable ReceivedShare share);
    oneway void onError(int errorCode, String errorMessage);
}
