package com.erfangholami.androidsolidservices.api.notifications

import com.erfangholami.androidsolidservices.api.notifications.implementation.RawNotificationParser
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.vocab.ACL
import com.erfangholami.androidsolidservices.shared.vocab.AS
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.XSD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RawNotificationParserTest {

    @Test
    fun `extracts the AS2 envelope from an offer`() {
        val subject = "https://alice.pod/inbox/offer#offer"
        val quads = listOf(
            RdfQuad(subject, RDF.TYPE, AS.OFFER),
            RdfQuad(subject, AS.ACTOR, "https://alice.pod/profile/card#me"),
            RdfQuad(subject, AS.OBJECT, "https://alice.pod/photo.jpg"),
            RdfQuad(subject, AS.TARGET, "https://bob.pod/profile/card#me"),
            RdfQuad(subject, ACL.MODE, ACL.READ),
            RdfQuad(subject, AS.PUBLISHED, "2026-06-09T10:00:00Z", datatype = XSD.DATE_TIME),
        )

        val notification = RawNotificationParser.parse("https://alice.pod/inbox/offer", quads)

        assertEquals(subject, notification.activitySubject)
        assertTrue(notification.types.contains(AS.OFFER))
        assertEquals("https://alice.pod/profile/card#me", notification.actor)
        assertEquals("https://alice.pod/photo.jpg", notification.`object`)
        assertEquals("https://bob.pod/profile/card#me", notification.target)
        assertEquals("2026-06-09T10:00:00Z", notification.publishedAt)
        assertEquals(listOf(ACL.READ), notification.objectsOf(ACL.MODE))
    }

    @Test
    fun `reads the inReplyTo link on an accept`() {
        val subject = "https://owner.pod/inbox/accept#accept"
        val quads = listOf(
            RdfQuad(subject, RDF.TYPE, AS.ACCEPT),
            RdfQuad(subject, AS.ACTOR, "https://owner.pod/card#me"),
            RdfQuad(subject, AS.IN_REPLY_TO, "https://owner.pod/inbox/req#req"),
        )

        val notification = RawNotificationParser.parse("https://owner.pod/inbox/accept", quads)

        assertEquals("https://owner.pod/inbox/req#req", notification.inReplyTo)
    }

    @Test
    fun `returns empty fields for a document with no triples`() {
        val notification = RawNotificationParser.parse("https://x/y", emptyList())

        assertNull(notification.activitySubject)
        assertNull(notification.actor)
        assertTrue(notification.types.isEmpty())
        assertTrue(notification.objectsOf(ACL.MODE).isEmpty())
    }

    @Test
    fun `prefers an as-typed subject over a blank node`() {
        val activity = "https://p/inbox/a#it"
        val quads = listOf(
            RdfQuad("_:b0", RDF.TYPE, "https://example.org/Other"),
            RdfQuad(activity, RDF.TYPE, AS.ACCEPT),
            RdfQuad(activity, AS.ACTOR, "https://p/card#me"),
        )

        val notification = RawNotificationParser.parse("https://p/inbox/a", quads)

        assertEquals(activity, notification.activitySubject)
        assertEquals("https://p/card#me", notification.actor)
    }
}
