package com.erfangholami.androidsolidservices.shared.model.contacts;

import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBook;

/** One-way callback delivering a single AddressBook result from a contacts module operation. */
interface IASSContactModuleAddressBookCallback {
    oneway void onResult(in @nullable AddressBook addressBook);
    oneway void onError(int errorCode, String errorMessage);
}
