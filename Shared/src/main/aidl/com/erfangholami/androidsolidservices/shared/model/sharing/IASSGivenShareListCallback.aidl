package com.erfangholami.androidsolidservices.shared.model.sharing;

import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare;

/** One-way callback delivering the list of GivenShare records (all outbound shares the user owns). */
interface IASSGivenShareListCallback {
    oneway void onResult(in List<GivenShare> shares);
    oneway void onError(int errorCode, String errorMessage);
}
