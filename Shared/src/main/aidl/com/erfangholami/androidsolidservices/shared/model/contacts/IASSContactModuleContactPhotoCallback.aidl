package com.erfangholami.androidsolidservices.shared.model.contacts;

import com.erfangholami.androidsolidservices.shared.model.contacts.ContactPhoto;

/**
 * One-way callback delivering a contact's photo bytes.
 *
 * The photo travels inline, so it is subject to the ~1 MB Binder transaction limit; a
 * larger image will fail the IPC call rather than be truncated.
 */
interface IASSContactModuleContactPhotoCallback {
    oneway void onResult(in @nullable ContactPhoto photo);
    oneway void onError(int errorCode, String errorMessage);
}
