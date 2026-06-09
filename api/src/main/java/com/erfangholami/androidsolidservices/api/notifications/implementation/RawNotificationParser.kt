package com.erfangholami.androidsolidservices.api.notifications.implementation

import com.erfangholami.androidsolidservices.api.notifications.RawNotification
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.vocab.AS
import com.erfangholami.androidsolidservices.shared.vocab.RDF

internal object RawNotificationParser {

    private const val BLANK_NODE_PREFIX = "_:"

    fun parse(uri: String, quads: List<RdfQuad>): RawNotification {
        val subject = activitySubject(quads)
        return RawNotification(
            uri = uri,
            activitySubject = subject,
            types = objectsFor(quads, subject, RDF.TYPE),
            actor = firstObjectFor(quads, subject, AS.ACTOR),
            `object` = firstObjectFor(quads, subject, AS.OBJECT),
            target = firstObjectFor(quads, subject, AS.TARGET),
            summary = firstObjectFor(quads, subject, AS.SUMMARY),
            publishedAt = firstObjectFor(quads, subject, AS.PUBLISHED),
            inReplyTo = firstObjectFor(quads, subject, AS.IN_REPLY_TO),
            quads = quads,
        )
    }

    private fun activitySubject(quads: List<RdfQuad>): String? {
        quads.firstOrNull { it.predicate == RDF.TYPE && it.`object`.startsWith(AS.NAMESPACE) }
            ?.let { return it.subject }
        return quads.firstOrNull { !it.subject.startsWith(BLANK_NODE_PREFIX) }?.subject
    }

    private fun objectsFor(
        quads: List<RdfQuad>,
        subject: String?,
        predicate: String,
    ): List<String> {
        if (subject == null) return emptyList()
        return quads
            .filter { it.subject == subject && it.predicate == predicate }
            .map { it.`object` }
    }

    private fun firstObjectFor(
        quads: List<RdfQuad>,
        subject: String?,
        predicate: String,
    ): String? {
        if (subject == null) return null
        return quads.firstOrNull { it.subject == subject && it.predicate == predicate }?.`object`
    }
}
