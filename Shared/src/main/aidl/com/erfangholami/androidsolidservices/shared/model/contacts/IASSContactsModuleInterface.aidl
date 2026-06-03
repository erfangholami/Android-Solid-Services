package com.erfangholami.androidsolidservices.shared.model.contacts;

import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBook;
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBookList;
import com.erfangholami.androidsolidservices.shared.model.contacts.Contact;
import com.erfangholami.androidsolidservices.shared.model.contacts.NewContact;
import com.erfangholami.androidsolidservices.shared.model.contacts.FullContact;
import com.erfangholami.androidsolidservices.shared.model.contacts.Email;
import com.erfangholami.androidsolidservices.shared.model.contacts.PhoneNumber;
import com.erfangholami.androidsolidservices.shared.model.contacts.Name;
import com.erfangholami.androidsolidservices.shared.model.contacts.Group;
import com.erfangholami.androidsolidservices.shared.model.contacts.FullGroup;
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleAddressBookListCallback;
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleAddressBookCallback;
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleFullContactCallback;
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleFullGroupCallback;

/**
 * AIDL sub-interface for the Solid Contacts data module. Provides cross-process operations
 * for managing address books, contacts, and groups stored on a Solid pod. Returned by
 * IASSDataModulesService; results are delivered via one-way callbacks.
 */
interface IASSContactsModuleInterface {

    void getAddressBooks(String webId, IASSContactModuleAddressBookListCallback callback);

    void createAddressBook(
            String webId,
            String title,
            boolean isPrivate,
            IASSContactModuleAddressBookCallback callback,
            @nullable String storage,
            @nullable String ownerWebId,
            @nullable String container
    );

    void getAddressBook(String webId, String uri, IASSContactModuleAddressBookCallback callback);

    void deleteAddressBook(
        String webId,
        String uri,
        @nullable String ownerWebId,
        IASSContactModuleAddressBookCallback callback
    );

    void createNewContact(
        String webId,
        String addressBookUri,
        in NewContact newContact,
        in List<String> groupUris,
        IASSContactModuleFullContactCallback callback
    );

    void getContact(
        String webId,
        String contactUri,
        IASSContactModuleFullContactCallback callback
    );

    void renameContact(
         String webId,
         String contactUri,
         String newName,
         IASSContactModuleFullContactCallback callback
    );

    void addNewPhoneNumber(
        String webId,
        String contactUri,
        String newPhoneNumber,
        IASSContactModuleFullContactCallback callback
    );

    void addNewEmailAddress(
        String webId,
        String contactUri,
        String newEmailAddress,
        IASSContactModuleFullContactCallback callback
    );

    void removePhoneNumber(
        String webId,
        String contactUri,
        String phoneNumber,
        IASSContactModuleFullContactCallback callback
    );

    void removeEmailAddress(
        String webId,
        String contactUri,
        String emailAddress,
        IASSContactModuleFullContactCallback callback
    );

    void deleteContact(
        String webId,
        String addressBookUri,
        String contactUri,
        IASSContactModuleFullContactCallback callback
    );

    void createNewGroup(
         String webId,
         String addressBookUri,
         String title,
         in List<String> contactUris,
         IASSContactModuleFullGroupCallback callback
    );

    void getGroup(
        String webId,
        String groupUri,
        IASSContactModuleFullGroupCallback callback
    );

    void deleteGroup(
        String webId,
        String addressBookUri,
        String groupUri,
        IASSContactModuleFullGroupCallback callback
    );

    void addContactToGroup(
        String webId,
        String contactUri,
        String groupUri,
        IASSContactModuleFullGroupCallback callback
    );

    void removeContactFromGroup(
        String webId,
        String contactUri,
        String groupUri,
        IASSContactModuleFullGroupCallback callback
    );
}