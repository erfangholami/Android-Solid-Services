package com.erfangholami.androidsolidservices.data.remote

import com.erfangholami.androidsolidservices.shared.model.contacts.ContactData
import com.erfangholami.androidsolidservices.shared.model.contacts.Email
import com.erfangholami.androidsolidservices.shared.model.contacts.FullContact
import com.erfangholami.androidsolidservices.shared.model.contacts.NewContact
import com.erfangholami.androidsolidservices.shared.model.contacts.PhoneNumber
import com.erfangholami.androidsolidservices.shared.model.contacts.SolidContact
import com.erfangholami.androidsolidservices.shared.model.contacts.contactData

fun SolidContact.toFullContact(): FullContact = FullContact(
    uri = uri,
    fullName = fullName,
    emailAddresses = data.emails.map { Email("mailto:${it.address}") },
    phoneNumbers = data.phones.map { PhoneNumber("tel:${it.number}") },
)

fun NewContact.toContactData(): ContactData = contactData {
    fullName = name
    if (this@toContactData.email.isNotBlank()) email(this@toContactData.email)
    if (this@toContactData.phoneNumber.isNotBlank()) phone(this@toContactData.phoneNumber)
}
