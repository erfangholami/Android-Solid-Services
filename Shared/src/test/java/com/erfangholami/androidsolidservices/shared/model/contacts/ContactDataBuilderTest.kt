package com.erfangholami.androidsolidservices.shared.model.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactDataBuilderTest {

    @Test
    fun `dsl builds the expected snapshot`() {
        val data = contactData {
            fullName = "Jane Doe"
            name {
                given = "Jane"
                family = "Doe"
            }
            nickname = "Janey"
            phone("+31612345678", PhoneType.CELL)
            email("jane@example.com", EmailType.HOME)
            address(AddressType.HOME) {
                street = "Kerkstraat 1"
                locality = "Amsterdam"
            }
            webId("https://jane.example/profile/card#me")
            organizationName = "NLnet"
        }
        assertEquals("Jane Doe", data.fullName)
        assertEquals(Name(familyName = "Doe", givenName = "Jane"), data.name)
        assertEquals(listOf(PhoneEntry("+31612345678", PhoneType.CELL)), data.phones)
        assertEquals(listOf(EmailEntry("jane@example.com", EmailType.HOME)), data.emails)
        assertEquals(
            listOf(
                AddressEntry(
                    street = "Kerkstraat 1",
                    locality = "Amsterdam",
                    type = AddressType.HOME,
                ),
            ),
            data.addresses,
        )
        assertEquals("https://jane.example/profile/card#me", data.webId())
        assertEquals("NLnet", data.organizationName)
    }

    @Test
    fun `dsl captures categories and gender`() {
        val data = contactData {
            fullName = "Jane"
            category("Friends")
            category("Work")
            gender = Gender.FEMALE
        }
        assertEquals(listOf("Friends", "Work"), data.categories)
        assertEquals(Gender.FEMALE, data.gender)
    }

    @Test
    fun `blank categories are trimmed, deduped and dropped`() {
        val data = contactData {
            fullName = "Jane"
            category(" ")
            category("  VIP ")
            category("VIP")
        }
        assertEquals(listOf("VIP"), data.categories)
    }

    @Test
    fun `buildUpon preserves categories and gender`() {
        val base = contactData {
            fullName = "Jane"
            category("Friends")
            gender = Gender.FEMALE
        }
        val extended = base.buildUpon { category("Work") }
        assertEquals(listOf("Friends", "Work"), extended.categories)
        assertEquals(Gender.FEMALE, extended.gender)
    }

    @Test
    fun `blank values are ignored`() {
        val data = contactData {
            fullName = "  "
            phone("", PhoneType.CELL)
            email("   ")
            address { street = " " }
            url("", URLType.Homepage)
        }
        assertNull(data.fullName)
        assertEquals(emptyList<PhoneEntry>(), data.phones)
        assertEquals(emptyList<EmailEntry>(), data.emails)
        assertEquals(emptyList<AddressEntry>(), data.addresses)
        assertEquals(emptyList<UrlEntry>(), data.urls)
    }

    @Test
    fun `buildUpon preserves untouched fields and appends to lists`() {
        val base = contactData {
            fullName = "Jane Doe"
            phone("+31612345678", PhoneType.CELL)
            note = "Met at FOSDEM"
        }
        val extended = base.buildUpon {
            phone("+31201234567", PhoneType.WORK)
            nickname = "Janey"
        }
        assertEquals("Jane Doe", extended.fullName)
        assertEquals("Met at FOSDEM", extended.note)
        assertEquals("Janey", extended.nickname)
        assertEquals(
            listOf(
                PhoneEntry("+31612345678", PhoneType.CELL),
                PhoneEntry("+31201234567", PhoneType.WORK),
            ),
            extended.phones,
        )
    }

    @Test
    fun `buildUpon can drop a field by nulling it`() {
        val base = contactData {
            fullName = "Jane"
            note = "temp"
        }
        val cleared = base.buildUpon { note = null }
        assertNull(cleared.note)
    }

    @Test
    fun `effectiveFullName falls back through parts, nickname, email, then phone`() {
        assertEquals("Jane", contactData { fullName = "Jane" }.effectiveFullName())
        assertEquals(
            "Jane Doe",
            contactData { name { given = "Jane"; family = "Doe" } }.effectiveFullName(),
        )
        assertEquals("Janey", contactData { nickname = "Janey" }.effectiveFullName())
        assertEquals(
            "j@example.com",
            contactData { email("j@example.com") }.effectiveFullName(),
        )
        assertEquals(
            "+31600000000",
            contactData { phone("+31600000000") }.effectiveFullName(),
        )
        assertEquals("", contactData { }.effectiveFullName())
    }
}
