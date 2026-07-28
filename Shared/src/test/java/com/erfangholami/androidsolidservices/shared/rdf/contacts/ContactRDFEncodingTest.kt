package com.erfangholami.androidsolidservices.shared.rdf.contacts

import com.erfangholami.androidsolidservices.shared.model.contacts.PhoneType
import com.erfangholami.androidsolidservices.shared.model.resource.RDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.vocab.VCARD
import com.erfangholami.androidsolidservices.shared.vocab.XSD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactRDFEncodingTest {

    private val contactUri =
        "https://alice.pod/contacts/b1/Person/p1/index.ttl#this"

    private fun reparse(contact: ContactRDF): ContactRDF {
        val json = contact.getEntity().bufferedReader().use { it.readText() }
        val quads = RDFResource.parseJsonLd(json, contactUri.toString().substringBefore('#'))
        return ContactRDF(contactUri, quads = quads)
    }

    private fun quadsOf(contact: ContactRDF): List<RdfQuad> {
        val json = contact.getEntity().bufferedReader().use { it.readText() }
        return RDFResource.parseJsonLd(json, contactUri.toString().substringBefore('#'))
    }

    @Test
    fun `formatted phone number becomes a valid tel IRI and round-trips`() {
        val contact = ContactRDF(contactUri).apply {
            setFullName("Jane")
            addPhone("+31 6 12 34 56 78", PhoneType.CELL)
        }
        val roundTripped = reparse(contact).getPhoneEntries().single()
        assertEquals("+31612345678", roundTripped.number)
        assertEquals(PhoneType.CELL, roundTripped.type)

        val telObject = quadsOf(contact).single { it.predicate == VCARD.VALUE }.`object`
        assertEquals("tel:+31612345678", telObject)
        assertTrue("tel: IRI must have no spaces", !telObject.contains(' '))
    }

    @Test
    fun `parenthesised phone number is normalised`() {
        val contact = ContactRDF(contactUri).apply {
            setFullName("Bob")
            addPhone("(555) 123-4567", PhoneType.WORK)
        }
        assertEquals("5551234567", reparse(contact).getPhoneEntries().single().number)
    }

    @Test
    fun `email with a space is percent-encoded into a valid mailto IRI`() {
        val contact = ContactRDF(contactUri).apply {
            setFullName("Jane")
            addEmail("jane doe@example.com")
        }
        val mailObject = quadsOf(contact).single { it.predicate == VCARD.VALUE }.`object`
        assertTrue("mailto: IRI must have no raw spaces", !mailObject.contains(' '))
        assertTrue(mailObject.startsWith("mailto:"))
        assertEquals("jane doe@example.com", reparse(contact).getEmailEntries().single().address)
    }

    @Test
    fun `non-IRI uid is urn-wrapped and unwraps on read`() {
        val contact = ContactRDF(contactUri).apply {
            setFullName("Jane")
            setUid("local-vcf-id 42")
        }
        val storedUid = quadsOf(contact).single { it.predicate == VCARD.HAS_UID }.`object`
        assertTrue("stored UID must be an absolute IRI", storedUid.startsWith("urn:uid:"))
        assertTrue("stored UID must have no raw spaces", !storedUid.contains(' '))
        assertEquals("local-vcf-id 42", reparse(contact).getUid())
    }

    @Test
    fun `absolute uid is stored verbatim`() {
        val contact = ContactRDF(contactUri).apply {
            setFullName("Jane")
            setUid("urn:uuid:1e4f-abcd")
        }
        val storedUid = quadsOf(contact).single { it.predicate == VCARD.HAS_UID }.`object`
        assertEquals("urn:uuid:1e4f-abcd", storedUid)
        assertEquals("urn:uuid:1e4f-abcd", reparse(contact).getUid())
    }

    @Test
    fun `date-only birthday is typed xsd date, not dateTime`() {
        val contact = ContactRDF(contactUri).apply {
            setFullName("Jane")
            setBirthday("1990-05-01")
        }
        val bday = quadsOf(contact).single { it.predicate == VCARD.BIRTHDAY }
        assertEquals(XSD.DATE, bday.datatype)
    }

    @Test
    fun `birthday with a time keeps xsd dateTime`() {
        val contact = ContactRDF(contactUri).apply {
            setFullName("Jane")
            setBirthday("1990-05-01T08:30:00Z")
        }
        val bday = quadsOf(contact).single { it.predicate == VCARD.BIRTHDAY }
        assertEquals(XSD.DATE_TIME, bday.datatype)
    }
}
