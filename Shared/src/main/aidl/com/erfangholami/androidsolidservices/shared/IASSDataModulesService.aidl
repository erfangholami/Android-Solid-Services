package com.erfangholami.androidsolidservices.shared;

import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactsModuleInterface;
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

/**
 * AIDL IPC contract for Solid data modules. Returns sub-interfaces for each data module
 * (currently Contacts). Third-party apps normally use the higher-level client SDK rather
 * than binding here directly.
 */
interface IASSDataModulesService {

    IASSContactsModuleInterface getContactsDataModuleInterface();
}