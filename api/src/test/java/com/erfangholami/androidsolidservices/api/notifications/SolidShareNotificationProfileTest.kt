package com.erfangholami.androidsolidservices.api.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class SolidShareNotificationProfileTest {

    @Test
    fun `vocabulary reproduces the existing solidshare fallback literals`() {
        val vocab = SolidShareNotificationProfile.vocabulary
        assertEquals("https://solidshare.com/ns#mode", vocab.modeLiteral)
        assertEquals("https://solidshare.com/ns#requestedMode", vocab.requestedModeLiteral)
    }

    @Test
    fun `slugs reproduce the existing solidshare prefixes`() {
        val slugs = SolidShareNotificationProfile.slugs
        assertEquals("solidshare-offer", slugs.offer)
        assertEquals("solidshare-undo", slugs.undo)
        assertEquals("solidshare-request", slugs.request)
        assertEquals("solidshare-reject", slugs.reject)
        assertEquals("solidshare-accept", slugs.accept)
        assertEquals("solidshare-decision-accept", slugs.decisionAccept)
        assertEquals("solidshare-decision-reject", slugs.decisionReject)
    }

    @Test
    fun `a custom profile rebrands the notification slugs`() {
        val custom = object : NotificationSlugs {
            override val offer: String = "myapp-offer"
            override val undo: String = "myapp-undo"
            override val request: String = "myapp-request"
            override val reject: String = "myapp-reject"
            override val accept: String = "myapp-accept"
            override val decisionAccept: String = "myapp-decision-accept"
            override val decisionReject: String = "myapp-decision-reject"
        }
        assertEquals("myapp-offer", custom.offer)
        assertEquals("myapp-decision-reject", custom.decisionReject)
    }
}
