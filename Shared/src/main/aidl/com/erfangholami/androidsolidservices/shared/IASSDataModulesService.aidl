package com.erfangholami.androidsolidservices.shared;

import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactsModuleInterface;
import com.erfangholami.androidsolidservices.shared.model.tickets.IASSTicketsModuleInterface;

/**
 * AIDL IPC contract for Solid data modules. Returns one sub-interface per data module
 * (Contacts and Tickets). Third-party apps normally use the higher-level client SDK rather
 * than binding here directly.
 */
interface IASSDataModulesService {

    IASSContactsModuleInterface getContactsDataModuleInterface();

    IASSTicketsModuleInterface getTicketsDataModuleInterface();
}
