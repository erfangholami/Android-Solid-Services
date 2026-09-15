package com.erfangholami.androidsolidservices.shared.model.datamodule

/**
 * The ids by which a grant names a data module and by which a host resolves the module's
 * containers. One id per module, stable across versions: an id is persisted inside grants.
 */
public object DataModuleId {

    public const val CONTACTS: String = "contacts"

    public const val TICKETS: String = "tickets"
}
