package com.erfangholami.androidsolidservices.shared.model.contacts;

import com.erfangholami.androidsolidservices.shared.model.contacts.SolidContact;

/** One-way callback delivering a SolidContact (a contact and its full ContactData) from a contacts module operation. */
interface IASSContactModuleSolidContactCallback {
    oneway void onResult(in @nullable SolidContact contact);
    oneway void onError(int errorCode, String errorMessage);
}
