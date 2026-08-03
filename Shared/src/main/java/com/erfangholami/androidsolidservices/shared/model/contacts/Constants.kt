package com.erfangholami.androidsolidservices.shared.model.contacts

import com.erfangholami.androidsolidservices.shared.model.datamodule.DATA_MODULE_ROOT

public const val CONTACTS_SEGMENT: String = "contacts/"

/** Where a new contacts container is allocated: `{storage}datamodule/contacts/`. */
public const val CONTACTS_DIRECTORY_SUFFIX: String = DATA_MODULE_ROOT + CONTACTS_SEGMENT
public const val PEOPLE_DIRECTORY_SUFFIX: String = "Person/"
public const val GROUP_DIRECTORY_SUFFIX: String = "Group/"
public const val INDEX_FILE_NAME: String = "index.ttl#this"
public const val PEOPLE_FILE_NAME: String = "people.ttl"
public const val GROUPS_FILE_NAME: String = "groups.ttl"
