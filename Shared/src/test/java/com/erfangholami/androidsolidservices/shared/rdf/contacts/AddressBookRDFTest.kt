package com.erfangholami.androidsolidservices.shared.rdf.contacts

import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.vocab.DC
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.VCARD
import com.erfangholami.androidsolidservices.shared.vocab.XSD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressBookRDFTest {

    private val bookUri = "https://alice.pod/datamodule/contacts/b1/index.ttl#this"

    @Test
    fun `a book that declares nothing reads as empty rather than throwing`() {
        val book = AddressBookRDF(
            identifier = bookUri,
            quads = listOf(RdfQuad(bookUri, RDF.TYPE, VCARD.ADDRESS_BOOK)),
        )

        assertNull("a foreign or partial book has no owner triple", book.getOwner())
        assertNull(book.getTitle())
        assertNull("no people index means the book lists no contacts", book.getNameEmailIndex())
        assertNull("no groups index means the book has no groups", book.getGroupsIndex())
    }

    @Test
    fun `a fully described book reads every link back`() {
        val book = AddressBookRDF(
            identifier = bookUri,
            quads = listOf(
                RdfQuad(bookUri, RDF.TYPE, VCARD.ADDRESS_BOOK),
                RdfQuad(bookUri, DC.TITLE, "Contacts", XSD.STRING),
                RdfQuad(bookUri, VCARD.NAME_EMAIL_INDEX, "$bookUri/../people.ttl"),
                RdfQuad(bookUri, VCARD.GROUP_INDEX, "$bookUri/../groups.ttl"),
            ),
        )

        assertEquals("Contacts", book.getTitle())
        assertEquals("$bookUri/../people.ttl", book.getNameEmailIndex())
        assertEquals("$bookUri/../groups.ttl", book.getGroupsIndex())
    }

    @Test
    fun `the legacy https title predicate still reads`() {
        val book = AddressBookRDF(
            identifier = bookUri,
            quads = listOf(
                RdfQuad(bookUri, RDF.TYPE, VCARD.ADDRESS_BOOK),
                RdfQuad(bookUri, DC.TITLE_LEGACY, "Older client", XSD.STRING),
            ),
        )

        assertEquals("Older client", book.getTitle())
    }
}
