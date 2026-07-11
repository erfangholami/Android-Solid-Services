package com.erfangholami.androidsolidservices.shared.model.contacts

public fun addressBookUriForContact(contactUri: String): String? {
    val personIndex = contactUri.lastIndexOf("/${PEOPLE_DIRECTORY_SUFFIX}")
    if (personIndex < 0) return null
    return "${contactUri.substring(0, personIndex + 1)}${INDEX_FILE_NAME}"
}
