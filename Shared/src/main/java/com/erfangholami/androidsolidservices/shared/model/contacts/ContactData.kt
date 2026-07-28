package com.erfangholami.androidsolidservices.shared.model.contacts

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

public enum class PhoneType { CELL, HOME, WORK, FAX, PAGER, VOICE, TEXT, VIDEO, TEXT_PHONE, OTHER }

public enum class EmailType { HOME, WORK, OTHER }

public enum class AddressType { HOME, WORK, OTHER }

public enum class ImType { HOME, WORK, OTHER }

/**
 * vCard 4.0 `GENDER` (W3C vCard ontology `vcard:hasGender`), mapped to the ontology's gender
 * classes: `vcard:Male`, `vcard:Female`, `vcard:Other`, `vcard:None`, `vcard:Unknown`.
 */
public enum class Gender { MALE, FEMALE, OTHER, NONE, UNKNOWN }

@Parcelize
public data class PhoneEntry(
    val number: String,
    val type: PhoneType = PhoneType.OTHER,
) : Parcelable

@Parcelize
public data class EmailEntry(
    val address: String,
    val type: EmailType = EmailType.OTHER,
) : Parcelable

@Parcelize
public data class AddressEntry(
    val street: String? = null,
    val locality: String? = null,
    val region: String? = null,
    val postalCode: String? = null,
    val countryName: String? = null,
    val poBox: String? = null,
    val type: AddressType = AddressType.OTHER,
) : Parcelable {
    public fun isEmpty(): Boolean = listOf(street, locality, region, postalCode, countryName, poBox)
        .all { it.isNullOrBlank() }
}

@Parcelize
public data class UrlEntry(
    val type: URLType,
    val value: String,
) : Parcelable

/**
 * An instant-messaging handle (vCard `IMPP` / `vcard:hasInstantMessage`). [handle] is
 * stored verbatim — a scheme-qualified URI (`xmpp:alice@example.com`) or a bare
 * protocol handle — so it round-trips whatever a device or `.vcf` provides.
 */
@Parcelize
public data class ImEntry(
    val handle: String,
    val type: ImType = ImType.OTHER,
) : Parcelable

@Parcelize
public data class ContactData(
    val fullName: String? = null,
    val name: Name? = null,
    val nickname: String? = null,
    val phones: List<PhoneEntry> = emptyList(),
    val emails: List<EmailEntry> = emptyList(),
    val impps: List<ImEntry> = emptyList(),
    val addresses: List<AddressEntry> = emptyList(),
    val birthday: String? = null,
    val anniversary: String? = null,
    val organizationName: String? = null,
    val organizationUnit: String? = null,
    val role: String? = null,
    val title: String? = null,
    val note: String? = null,
    val categories: List<String> = emptyList(),
    val gender: Gender? = null,
    val geos: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val urls: List<UrlEntry> = emptyList(),
    val uid: String? = null,
) : Parcelable {

    public fun webId(): String? = urls.firstOrNull { it.type == URLType.WebId }?.value

    public fun effectiveFullName(): String {
        fullName?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        val fromParts = listOfNotNull(
            name?.honorificPrefix,
            name?.givenName,
            name?.additionalName,
            name?.familyName,
            name?.honorificSuffix,
        ).joinToString(" ").trim()
        if (fromParts.isNotBlank()) return fromParts
        nickname?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        emails.firstOrNull { it.address.isNotBlank() }?.let { return it.address.trim() }
        phones.firstOrNull { it.number.isNotBlank() }?.let { return it.number.trim() }
        return ""
    }
}

@DslMarker
public annotation class ContactDataDsl

public fun contactData(block: ContactDataBuilder.() -> Unit): ContactData =
    ContactDataBuilder().apply(block).build()

public fun ContactData.buildUpon(block: ContactDataBuilder.() -> Unit): ContactData =
    ContactDataBuilder(this).apply(block).build()

@ContactDataDsl
public class ContactDataBuilder internal constructor(seed: ContactData? = null) {

    public var fullName: String? = seed?.fullName
    public var nickname: String? = seed?.nickname
    public var birthday: String? = seed?.birthday
    public var anniversary: String? = seed?.anniversary
    public var organizationName: String? = seed?.organizationName
    public var organizationUnit: String? = seed?.organizationUnit
    public var role: String? = seed?.role
    public var title: String? = seed?.title
    public var note: String? = seed?.note
    public var gender: Gender? = seed?.gender
    public var uid: String? = seed?.uid

    private var structuredName: Name? = seed?.name
    private val phones: MutableList<PhoneEntry> = seed?.phones.orEmpty().toMutableList()
    private val emails: MutableList<EmailEntry> = seed?.emails.orEmpty().toMutableList()
    private val impps: MutableList<ImEntry> = seed?.impps.orEmpty().toMutableList()
    private val addresses: MutableList<AddressEntry> = seed?.addresses.orEmpty().toMutableList()
    private val categories: MutableList<String> = seed?.categories.orEmpty().toMutableList()
    private val geos: MutableList<String> = seed?.geos.orEmpty().toMutableList()
    private val languages: MutableList<String> = seed?.languages.orEmpty().toMutableList()
    private val urls: MutableList<UrlEntry> = seed?.urls.orEmpty().toMutableList()

    public fun name(block: NameBuilder.() -> Unit) {
        structuredName = NameBuilder(structuredName).apply(block).build()
    }

    public fun phone(number: String, type: PhoneType = PhoneType.OTHER) {
        if (number.isNotBlank()) phones.add(PhoneEntry(number.trim(), type))
    }

    public fun email(address: String, type: EmailType = EmailType.OTHER) {
        if (address.isNotBlank()) emails.add(EmailEntry(address.trim(), type))
    }

    public fun impp(handle: String, type: ImType = ImType.OTHER) {
        if (handle.isNotBlank()) impps.add(ImEntry(handle.trim(), type))
    }

    public fun address(type: AddressType = AddressType.OTHER, block: AddressBuilder.() -> Unit) {
        val entry = AddressBuilder(type).apply(block).build()
        if (!entry.isEmpty()) addresses.add(entry)
    }

    public fun category(value: String) {
        val trimmed = value.trim()
        if (trimmed.isNotBlank() && trimmed !in categories) categories.add(trimmed)
    }

    /**
     * Adds a vCard 4.0 `GEO` position (`vcard:hasGeo`). [value] is kept verbatim — a
     * `geo:` URI (`geo:37.386013,-122.082932`) or a bare `lat,lng` pair, which the RDF
     * codec prefixes with the `geo:` scheme on write.
     */
    public fun geo(value: String) {
        val trimmed = value.trim()
        if (trimmed.isNotBlank() && trimmed !in geos) geos.add(trimmed)
    }

    /**
     * Adds a vCard 4.0 `LANG` contact language (`vcard:hasLanguage`), a BCP-47 tag such
     * as `en` or `fa-IR`. List order carries the vCard preference order. De-duplicated
     * case-insensitively.
     */
    public fun language(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isNotBlank() && languages.none { it.equals(trimmed, ignoreCase = true) }) {
            languages.add(trimmed)
        }
    }

    public fun url(value: String, type: URLType) {
        if (value.isNotBlank()) urls.add(UrlEntry(type, value.trim()))
    }

    public fun webId(webId: String) {
        url(webId, URLType.WebId)
    }

    public fun build(): ContactData = ContactData(
        fullName = fullName?.takeIf { it.isNotBlank() },
        name = structuredName,
        nickname = nickname?.takeIf { it.isNotBlank() },
        phones = phones.toList(),
        emails = emails.toList(),
        impps = impps.toList(),
        addresses = addresses.toList(),
        birthday = birthday?.takeIf { it.isNotBlank() },
        anniversary = anniversary?.takeIf { it.isNotBlank() },
        organizationName = organizationName?.takeIf { it.isNotBlank() },
        organizationUnit = organizationUnit?.takeIf { it.isNotBlank() },
        role = role?.takeIf { it.isNotBlank() },
        title = title?.takeIf { it.isNotBlank() },
        note = note?.takeIf { it.isNotBlank() },
        categories = categories.toList(),
        gender = gender,
        geos = geos.toList(),
        languages = languages.toList(),
        urls = urls.toList(),
        uid = uid?.takeIf { it.isNotBlank() },
    )
}

@ContactDataDsl
public class NameBuilder internal constructor(seed: Name? = null) {

    public var given: String? = seed?.givenName
    public var family: String? = seed?.familyName
    public var additional: String? = seed?.additionalName
    public var prefix: String? = seed?.honorificPrefix
    public var suffix: String? = seed?.honorificSuffix

    internal fun build(): Name? {
        val name = Name(
            familyName = family?.takeIf { it.isNotBlank() },
            givenName = given?.takeIf { it.isNotBlank() },
            additionalName = additional?.takeIf { it.isNotBlank() },
            honorificPrefix = prefix?.takeIf { it.isNotBlank() },
            honorificSuffix = suffix?.takeIf { it.isNotBlank() },
        )
        val isEmpty = name.familyName == null && name.givenName == null &&
                name.additionalName == null && name.honorificPrefix == null &&
                name.honorificSuffix == null
        return if (isEmpty) null else name
    }
}

@ContactDataDsl
public class AddressBuilder internal constructor(private val type: AddressType) {

    public var street: String? = null
    public var locality: String? = null
    public var region: String? = null
    public var postalCode: String? = null
    public var countryName: String? = null
    public var poBox: String? = null

    internal fun build(): AddressEntry = AddressEntry(
        street = street?.takeIf { it.isNotBlank() },
        locality = locality?.takeIf { it.isNotBlank() },
        region = region?.takeIf { it.isNotBlank() },
        postalCode = postalCode?.takeIf { it.isNotBlank() },
        countryName = countryName?.takeIf { it.isNotBlank() },
        poBox = poBox?.takeIf { it.isNotBlank() },
        type = type,
    )
}
