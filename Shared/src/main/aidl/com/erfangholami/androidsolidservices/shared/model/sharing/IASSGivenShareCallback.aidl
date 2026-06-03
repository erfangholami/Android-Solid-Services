package com.erfangholami.androidsolidservices.shared.model.sharing;

import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare;

/** One-way callback delivering a single GivenShare (an outbound share the user owns) from a sharing operation. */
interface IASSGivenShareCallback {
    oneway void onResult(in @nullable GivenShare share);
    oneway void onError(int errorCode, String errorMessage);
}
