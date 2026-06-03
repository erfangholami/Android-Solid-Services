package com.erfangholami.androidsolidservices.shared.model.contacts;

import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBook;
import com.erfangholami.androidsolidservices.shared.model.contacts.FullGroup;

/** One-way callback delivering a FullGroup (group with its member list) from a contacts module operation. */
interface IASSContactModuleFullGroupCallback {
    oneway void onResult(in @nullable FullGroup fullGroup);
    oneway void onError(int errorCode, String errorMessage);
}
