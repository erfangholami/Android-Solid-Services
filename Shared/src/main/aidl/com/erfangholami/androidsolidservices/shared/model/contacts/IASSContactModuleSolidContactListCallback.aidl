package com.erfangholami.androidsolidservices.shared.model.contacts;

import com.erfangholami.androidsolidservices.shared.model.contacts.SolidContactList;

/** One-way callback delivering an address book's contacts (a SolidContactList). */
interface IASSContactModuleSolidContactListCallback {
    oneway void onResult(in @nullable SolidContactList contactList);
    oneway void onError(int errorCode, String errorMessage);
}
