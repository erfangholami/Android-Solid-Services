package com.erfangholami.androidsolidservices.shared;

import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactsModuleInterface;
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBook;
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBookList;
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactData;
import com.erfangholami.androidsolidservices.shared.model.contacts.SolidContact;
import com.erfangholami.androidsolidservices.shared.model.contacts.SolidContactList;
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactPhoto;
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactMatch;
import com.erfangholami.androidsolidservices.shared.model.contacts.Group;
import com.erfangholami.androidsolidservices.shared.model.contacts.FullGroup;
import com.erfangholami.androidsolidservices.shared.model.tickets.IASSTicketsModuleInterface;
import com.erfangholami.androidsolidservices.shared.model.tickets.Ticket;
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket;
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketList;
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketArtifact;

/**
 * AIDL IPC contract for Solid data modules. Returns one sub-interface per data module
 * (Contacts and Tickets). Third-party apps normally use the higher-level client SDK rather
 * than binding here directly.
 */
interface IASSDataModulesService {

    IASSContactsModuleInterface getContactsDataModuleInterface();

    IASSTicketsModuleInterface getTicketsDataModuleInterface();
}