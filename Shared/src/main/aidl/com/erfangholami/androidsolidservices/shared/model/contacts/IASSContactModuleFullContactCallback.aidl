package com.erfangholami.androidsolidservices.shared.model.contacts;

import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBook;
import com.erfangholami.androidsolidservices.shared.model.contacts.FullContact;

/** One-way callback delivering a FullContact (contact with all detail fields) from a contacts module operation. */
interface IASSContactModuleFullContactCallback {
    oneway void onResult(in @nullable FullContact fullContact);
    oneway void onError(int errorCode, String errorMessage);
}
