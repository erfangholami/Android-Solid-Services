package com.erfangholami.androidsolidservices.shared.model.sharing;

import com.erfangholami.androidsolidservices.shared.model.sharing.ReceivedShare;

/** One-way callback delivering the list of ReceivedShare records (all inbound shares granted to this user). */
interface IASSReceivedShareListCallback {
    oneway void onResult(in List<ReceivedShare> shares);
    oneway void onError(int errorCode, String errorMessage);
}
