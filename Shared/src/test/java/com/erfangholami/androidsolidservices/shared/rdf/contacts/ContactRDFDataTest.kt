package com.erfangholami.androidsolidservices.shared.rdf.contacts

import com.erfangholami.androidsolidservices.shared.model.contacts.AddressEntry
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressType
import com.erfangholami.androidsolidservices.shared.model.contacts.EmailEntry
import com.erfangholami.androidsolidservices.shared.model.contacts.EmailType
import com.erfangholami.androidsolidservices.shared.model.contacts.Gender
import com.erfangholami.androidsolidservices.shared.model.contacts.ImEntry
import com.erfangholami.androidsolidservices.shared.model.contacts.ImType
import com.erfangholami.androidsolidservices.shared.model.contacts.Name
import com.erfangholami.androidsolidservices.shared.model.contacts.PhoneEntry
import com.erfangholami.androidsolidservices.shared.model.contacts.PhoneType
import com.erfangholami.androidsolidservices.shared.model.contacts.URLType
import com.erfangholami.androidsolidservices.shared.model.contacts.UrlEntry
import com.erfangholami.androidsolidservices.shared.model.contacts.contactData
import com.erfangholami.androidsolidservices.shared.model.resource.RDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.VCARD
import com.erfangholami.androidsolidservices.shared.vocab.XSD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactRDFDataTest {

    private val contactUri =
        "https://alice.pod/contacts/book1/Person/p1/index.ttl#this"

    private val maximalData = contactData {
        fullName = "Dr. Jane A. Doe"
        name {
            given = "Jane"
            family = "Doe"
            additional = "A."
            prefix = "Dr."
            suffix = "PhD"
        }
        nickname = "Janey"
        phone("+31612345678", PhoneType.CELL)
        phone("+31201234567", PhoneType.WORK)
        email("jane@example.com", EmailType.HOME)
        email("jane@work.example", EmailType.WORK)
        impp("xmpp:jane@jabber.example", ImType.HOME)
        impp("skype:jane.doe", ImType.WORK)
        address(AddressType.HOME) {
            street = "Kerkstraat 1"
            locality = "Amsterdam"
            region = "NH"
            postalCode = "1017GA"
            countryName = "Netherlands"
        }
        address(AddressType.WORK) {
            street = "Science Park 400"
            locality = "Amsterdam"
            poBox = "94079"
            countryName = "Netherlands"
        }
        birthday = "1990-04-01"
        anniversary = "2015-06-20"
        organizationName = "NLnet"
        organizationUnit = "Grants"
        role = "Engineer"
        title = "Senior Software Engineer"
        note = "Met at FOSDEM"
        category("Friends")
        category("Colleagues")
        gender = Gender.FEMALE
        webId("https://jane.solidcommunity.net/profile/card#me")
        url("https://jane.example", URLType.Homepage)
        uid = "urn:uuid:2f1c0000-0000-0000-0000-000000000001"
    }

    private fun maximalContact(): ContactRDF =
        ContactRDF(contactUri).apply { setContactData(maximalData) }

    @Test
    fun `maximal contact data round-trips in memory`() {
        assertEquals(maximalData, maximalContact().toContactData())
    }

    @Test
    fun `categories round-trip as hasCategory literals`() {
        val contact = ContactRDF(contactUri).apply {
            setContactData(
                contactData {
                    fullName = "Jane"
                    category("Friends")
                    category("Family")
                },
            )
        }
        assertEquals(listOf("Friends", "Family"), contact.toContactData().categories)
        val categoryQuads = contact.getAllQuads().count { it.predicate == VCARD.HAS_CATEGORY }
        assertEquals(2, categoryQuads)
    }

    @Test
    fun `gender round-trips as a hasGender IRI not a literal`() {
        val contact = ContactRDF(contactUri).apply {
            setContactData(contactData { fullName = "Jane"; gender = Gender.FEMALE })
        }
        assertEquals(Gender.FEMALE, contact.toContactData().gender)
        assertTrue(
            contact.getAllQuads().any {
                it.predicate == VCARD.HAS_GENDER && it.`object` == VCARD.FEMALE
            },
        )
    }

    @Test
    fun `setContactData clears stale categories and gender`() {
        val contact = ContactRDF(contactUri).apply {
            setContactData(
                contactData { fullName = "Jane"; category("Old"); gender = Gender.MALE },
            )
        }
        contact.setContactData(contactData { fullName = "Jane" })
        assertEquals(emptyList<String>(), contact.toContactData().categories)
        assertNull(contact.toContactData().gender)
    }

    @Test
    fun `maximal contact data survives a serialize-parse wire round-trip`() {
        val jsonLd = maximalContact().getEntity().bufferedReader().use { it.readText() }
        val quads =
            RDFResource.parseJsonLd(jsonLd, contactUri.toString().substringBefore('#'))
        val reparsed = ContactRDF(contactUri, quads = quads).toContactData()
        assertEquals(maximalData.normalized(), reparsed.normalized())
    }

    private fun com.erfangholami.androidsolidservices.shared.model.contacts.ContactData.normalized() =
        copy(
            phones = phones.sortedBy { it.number },
            emails = emails.sortedBy { it.address },
            impps = impps.sortedBy { it.handle },
            addresses = addresses.sortedBy { it.street ?: "" },
            urls = urls.sortedBy { it.value },
        )

    @Test
    fun `instant-messaging handles round-trip with type and verbatim value`() {
        val contact = ContactRDF(contactUri).apply {
            setContactData(
                contactData {
                    fullName = "Jane"
                    impp("xmpp:jane@jabber.example", ImType.HOME)
                    impp("bare-handle-123", ImType.OTHER)
                },
            )
        }
        assertEquals(
            listOf(
                ImEntry("xmpp:jane@jabber.example", ImType.HOME),
                ImEntry("bare-handle-123", ImType.OTHER),
            ),
            contact.toContactData().impps,
        )
        assertEquals(
            2,
            contact.getAllQuads().count { it.predicate == VCARD.HAS_INSTANT_MESSAGE },
        )
    }

    @Test
    fun `untyped legacy instant-message node reads as OTHER`() {
        val self = contactUri.toString()
        val quads = mutableListOf(
            RdfQuad(self, VCARD.FN, "Legacy", XSD.STRING, null),
            RdfQuad(self, VCARD.HAS_INSTANT_MESSAGE, "_:im0", null, null),
            RdfQuad("_:im0", VCARD.VALUE, "xmpp:legacy@im.example", XSD.STRING, null),
        )
        val contact = ContactRDF(contactUri, quads = quads)
        assertEquals(
            listOf(ImEntry("xmpp:legacy@im.example", ImType.OTHER)),
            contact.getImEntries(),
        )
    }

    @Test
    fun `setContactData clears stale instant-message nodes`() {
        val contact = ContactRDF(contactUri).apply {
            setContactData(contactData { fullName = "Jane"; impp("skype:old", ImType.WORK) })
        }
        contact.setContactData(contactData { fullName = "Jane" })
        assertEquals(emptyList<ImEntry>(), contact.toContactData().impps)
    }

    @Test
    fun `setContactData twice leaves no stale entry nodes`() {
        val contact = maximalContact()
        val slim = contactData {
            fullName = "Jane Doe"
            phone("+31600000000", PhoneType.CELL)
        }
        contact.setContactData(slim)
        assertEquals(slim, contact.toContactData())
        val orphaned = contact.getAllQuads().filter {
            it.predicate == VCARD.VALUE || it.predicate == VCARD.STREET_ADDRESS
        }
        assertEquals(1, orphaned.size)
    }

    @Test
    fun `untyped legacy phone and email nodes read as OTHER with stripped schemes`() {
        val self = contactUri.toString()
        val quads = mutableListOf(
            RdfQuad(self, RDF.TYPE, VCARD.INDIVIDUAL, null, null),
            RdfQuad(self, VCARD.FN, "Legacy", XSD.STRING, null),
            RdfQuad(self, VCARD.HAS_TELEPHONE, "_:+31612345678", null, null),
            RdfQuad("_:+31612345678", VCARD.VALUE, "tel:+31612345678", null, null),
            RdfQuad(self, VCARD.HAS_EMAIL, "_:legacy@example.com", null, null),
            RdfQuad("_:legacy@example.com", VCARD.VALUE, "mailto:legacy@example.com", null, null),
        )
        val contact = ContactRDF(contactUri, quads = quads)
        assertEquals(
            listOf(PhoneEntry("+31612345678", PhoneType.OTHER)),
            contact.getPhoneEntries(),
        )
        assertEquals(
            listOf(EmailEntry("legacy@example.com", EmailType.OTHER)),
            contact.getEmailEntries(),
        )
    }

    @Test
    fun `unknown node types and bare values are tolerated`() {
        val self = contactUri.toString()
        val quads = mutableListOf(
            RdfQuad(self, VCARD.FN, "X", XSD.STRING, null),
            RdfQuad(self, VCARD.HAS_TELEPHONE, "_:t0", null, null),
            RdfQuad("_:t0", RDF.TYPE, "${VCARD.NAMESPACE}Hologram", null, null),
            RdfQuad("_:t0", VCARD.VALUE, "+31600000000", null, null),
            RdfQuad(self, VCARD.HAS_ADDRESS, "_:a0", null, null),
            RdfQuad("_:a0", VCARD.LOCALITY, "Amsterdam", XSD.STRING, null),
        )
        val contact = ContactRDF(contactUri, quads = quads)
        assertEquals(
            listOf(PhoneEntry("+31600000000", PhoneType.OTHER)),
            contact.getPhoneEntries(),
        )
        assertEquals(
            listOf(AddressEntry(locality = "Amsterdam", type = AddressType.OTHER)),
            contact.getAddresses(),
        )
    }

    @Test
    fun `OTHER phones and emails are written without a type triple`() {
        val contact = ContactRDF(contactUri).apply {
            setFullName("X")
            addPhone("+31600000000", PhoneType.OTHER)
            addEmail("x@example.com", EmailType.OTHER)
        }
        val nodeTypes = contact.getAllQuads().filter {
            it.predicate == RDF.TYPE && it.subject.startsWith("_:")
        }
        assertEquals(emptyList<RdfQuad>(), nodeTypes)
    }

    @Test
    fun `typed phones write their class and read back with precedence`() {
        val contact = ContactRDF(contactUri).apply {
            setFullName("X")
            addPhone("+31600000001", PhoneType.CELL)
        }
        assertTrue(
            contact.getAllQuads().any {
                it.predicate == RDF.TYPE && it.`object` == VCARD.CELL
            },
        )
        assertEquals(PhoneType.CELL, contact.getPhoneEntries().single().type)
    }

    @Test
    fun `blank node labels are counter based and collision free`() {
        val contact = ContactRDF(contactUri).apply {
            setFullName("X")
            addAddress(AddressEntry(street = "With spaces, and \"quotes\""))
            addAddress(AddressEntry(street = "Second +street"))
        }
        val labels = contact.getAllQuads()
            .filter { it.predicate == VCARD.HAS_ADDRESS }
            .map { it.`object` }
        assertEquals(listOf("_:addr0", "_:addr1"), labels)
        contact.addAddress(AddressEntry(street = "Third"))
        assertEquals(3, contact.getAddresses().size)
    }

    @Test
    fun `anniversary datatype follows the value shape`() {
        val contact = ContactRDF(contactUri).apply { setFullName("X") }
        contact.setAnniversary("2015-06-20")
        assertEquals(
            XSD.DATE,
            contact.getAllQuads().single { it.predicate == VCARD.ANNIVERSARY }.datatype,
        )
        contact.setAnniversary("2015-06-20T12:00:00Z")
        assertEquals(
            XSD.DATE_TIME,
            contact.getAllQuads().single { it.predicate == VCARD.ANNIVERSARY }.datatype,
        )
        contact.setAnniversary(null)
        assertNull(contact.getAnniversary())
    }

    @Test
    fun `setName null removes the node entirely`() {
        val contact = maximalContact()
        contact.setName(null)
        assertNull(contact.getName())
        assertEquals(
            emptyList<RdfQuad>(),
            contact.getAllQuads().filter { it.subject.endsWith("#name") },
        )
    }

    @Test
    fun `optional literals clear when set to null`() {
        val contact = maximalContact()
        contact.setNickname(null)
        contact.setOrganizationUnit(null)
        contact.setUid(null)
        assertNull(contact.getNickname())
        assertNull(contact.getOrganizationUnit())
        assertNull(contact.getUid())
    }

    @Test
    fun `urls dedupe on add and clean up on remove`() {
        val contact = maximalContact()
        assertFalse(contact.addUrl(URLType.Home, "https://jane.example"))
        assertTrue(contact.removeUrl("https://jane.example"))
        assertFalse(contact.removeUrl("https://jane.example"))
        assertEquals(1, contact.getUrlEntries().size)
    }

    @Test
    fun `photo link is preserved across setContactData`() {
        val contact = maximalContact()
        contact.setPhoto("https://alice.pod/contacts/book1/Person/p1/photo.jpg")
        contact.setContactData(contactData { fullName = "Jane" })
        assertEquals(
            "https://alice.pod/contacts/book1/Person/p1/photo.jpg",
            contact.getPhotoUrl(),
        )
    }

    @Test
    fun `typed add helpers mint safe counter labels`() {
        val contact = ContactRDF(contactUri).apply {
            setFullName("X")
            addPhone("+31612345678", PhoneType.OTHER)
            addEmail("x@example.com", EmailType.OTHER)
        }
        val nodeLabels = contact.getAllQuads()
            .filter { it.predicate == VCARD.HAS_TELEPHONE || it.predicate == VCARD.HAS_EMAIL }
            .map { it.`object` }
        assertEquals(listOf("_:phone0", "_:email0"), nodeLabels)
        assertEquals(
            listOf(PhoneEntry("+31612345678", PhoneType.OTHER)),
            contact.getPhoneEntries(),
        )
        assertEquals(
            listOf(EmailEntry("x@example.com", EmailType.OTHER)),
            contact.getEmailEntries(),
        )
    }

    @Test
    fun `people index updates a cached name only for listed contacts`() {
        val index = NameEmailIndexRDF(
            "https://alice.pod/contacts/book1/people.ttl",
        )
        val bookUri = "https://alice.pod/contacts/book1/index.ttl#this"
        index.addContact(bookUri, maximalContact())
        assertTrue(index.updateContactName(contactUri.toString(), "Jane Renamed"))
        assertEquals("Jane Renamed", index.getContacts(bookUri).single().name)
        assertFalse(index.updateContactName("https://alice.pod/ghost#this", "Ghost"))
    }

    @Test
    fun `group updates a cached member name only for members`() {
        val group = GroupRDF(
            "https://alice.pod/contacts/book1/Group/friends.ttl",
        ).apply {
            setTitle("Friends")
            addMember(maximalContact())
        }
        assertTrue(group.updateMemberName(contactUri.toString(), "Jane Renamed"))
        assertEquals("Jane Renamed", group.getContacts().single().name)
        assertFalse(group.updateMemberName("https://alice.pod/ghost#this", "Ghost"))
    }
}
