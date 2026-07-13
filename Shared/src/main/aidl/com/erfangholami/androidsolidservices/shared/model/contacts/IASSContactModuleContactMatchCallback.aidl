package com.erfangholami.androidsolidservices.shared.model.contacts;

import com.erfangholami.androidsolidservices.shared.model.contacts.ContactMatch;

/** One-way callback delivering the result of an exact-WebID contact lookup. */
interface IASSContactModuleContactMatchCallback {
    oneway void onResult(in @nullable ContactMatch match);
    oneway void onError(int errorCode, String errorMessage);
}
