package com.erfangholami.androidsolidservices.shared.model.contacts;

import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBook;
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBookList;

/** One-way callback delivering a list of AddressBook results from a contacts module operation. */
interface IASSContactModuleAddressBookListCallback {
    oneway void onResult(in @nullable AddressBookList addressBookList);
    oneway void onError(int errorCode, String errorMessage);
}
