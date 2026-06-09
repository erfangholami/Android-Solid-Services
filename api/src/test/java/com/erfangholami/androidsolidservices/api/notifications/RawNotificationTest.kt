package com.erfangholami.androidsolidservices.api.notifications

import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.vocab.ACL
import com.erfangholami.androidsolidservices.shared.vocab.AS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawNotificationTest {

    @Test
    fun `objectsOf returns only triples on the activity subject`() {
        val subject = "https://p/n#it"
        val notification = RawNotification(
            uri = "https://p/n",
            activitySubject = subject,
            types = listOf(AS.OFFER),
            actor = null,
            `object` = null,
            target = null,
            summary = null,
            publishedAt = null,
            inReplyTo = null,
            quads = listOf(
                RdfQuad(subject, ACL.MODE, ACL.READ),
                RdfQuad(subject, ACL.MODE, ACL.WRITE),
                RdfQuad("https://other#it", ACL.MODE, ACL.APPEND),
            ),
        )

        assertEquals(setOf(ACL.READ, ACL.WRITE), notification.objectsOf(ACL.MODE).toSet())
    }

    @Test
    fun `objectsOf is empty when there is no activity subject`() {
        val notification = RawNotification(
            uri = "u",
            activitySubject = null,
            types = emptyList(),
            actor = null,
            `object` = null,
            target = null,
            summary = null,
            publishedAt = null,
            inReplyTo = null,
            quads = listOf(RdfQuad("s", ACL.MODE, ACL.READ)),
        )

        assertTrue(notification.objectsOf(ACL.MODE).isEmpty())
    }
}
