package com.erfangholami.androidsolidservices.shared.model.contacts;

/**
 * AIDL Parcelable declarations for the address-book / group types.
 *
 * Only types named directly in an AIDL signature need a declaration. The summary and value
 * types these carry (Contact, Email, PhoneNumber, Name) travel nested inside their parent's
 * parcel and need none.
 *
 * The V1 contact model (NewContact / FullContact) is gone: the contacts module now speaks
 * the same ContactData / SolidContact model over IPC as it does in-process, so those types
 * no longer cross the boundary and are no longer declared.
 */
parcelable AddressBook;
parcelable AddressBookList;
parcelable Group;
parcelable FullGroup;
