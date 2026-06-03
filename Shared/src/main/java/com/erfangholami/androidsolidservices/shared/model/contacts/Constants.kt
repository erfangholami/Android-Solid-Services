package com.erfangholami.androidsolidservices.shared.model.contacts

/** Path suffix appended to a storage root when allocating a contacts container. */
public const val CONTACTS_DIRECTORY_SUFFIX: String = "contacts/"

/** Sub-directory suffix used for individual contact resources inside the contacts container. */
public const val PEOPLE_DIRECTORY_SUFFIX: String = "Person/"

/** Sub-directory suffix used for group resources inside the contacts container. */
public const val GROUP_DIRECTORY_SUFFIX: String = "Group/"

/** File name (with fragment) of the address-book root document within its container. */
public const val INDEX_FILE_NAME: String = "index.ttl#this"

/** File name of the people (name-email) index document. */
public const val PEOPLE_FILE_NAME: String = "people.ttl"

/** File name of the groups index document. */
public const val GROUPS_FILE_NAME: String = "groups.ttl"