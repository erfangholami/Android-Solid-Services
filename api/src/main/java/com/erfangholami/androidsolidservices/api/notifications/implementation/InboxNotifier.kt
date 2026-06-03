package com.erfangholami.androidsolidservices.api.notifications.implementation

import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.http.HTTPAcceptType
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.vocab.ACL
import com.erfangholami.androidsolidservices.shared.vocab.AS
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.SAI
import com.erfangholami.androidsolidservices.shared.vocab.XSD
import java.net.URI
import java.time.Instant

/**
 * Posts Activity Streams 2.0 / SolidShare-shaped notifications to an
 * agent's LDN inbox.
 *
 * POSTs are DPoP-authenticated via [SolidResourceManager.post]. Returns a
 * typed [InboxPostResult] so the caller can distinguish "no inbox advertised"
 * / 401 / 403 / generic HTTP failure / network exception.
 *
 * Spec anchors:
 *  - LDN: https://www.w3.org/TR/ldn/
 *  - Activity Streams 2: https://www.w3.org/TR/activitystreams-core/
 *  - Solid Notifications: https://solidproject.org/TR/notifications-protocol
 */
internal class InboxNotifier(private val rm: SolidResourceManager) {

    suspend fun postOffer(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: URI,
        mode: ShareMode,
    ): InboxPostResult = postFromSenderToReceiver(
        senderWebId = ownerWebId,
        receiverWebId = receiverWebId,
        slugPrefix = "solidshare-offer",
        body = buildOfferTurtle(ownerWebId, receiverWebId, resourceUri, mode),
    )

    suspend fun postUndo(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: URI,
        mode: ShareMode? = null,
    ): InboxPostResult = postFromSenderToReceiver(
        senderWebId = ownerWebId,
        receiverWebId = receiverWebId,
        slugPrefix = "solidshare-undo",
        body = buildUndoTurtle(ownerWebId, receiverWebId, resourceUri, mode),
    )

    suspend fun postRequest(
        requesterWebId: String,
        ownerWebId: String,
        resourceUri: URI,
        requestedMode: ShareMode,
        summary: String?,
    ): InboxPostResult = postFromSenderToReceiver(
        senderWebId = requesterWebId,
        receiverWebId = ownerWebId,
        slugPrefix = "solidshare-request",
        body = buildRequestTurtle(requesterWebId, ownerWebId, resourceUri, requestedMode, summary),
    )

    suspend fun postReject(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: URI,
        reason: String?,
    ): InboxPostResult = postFromSenderToReceiver(
        senderWebId = ownerWebId,
        receiverWebId = requesterWebId,
        slugPrefix = "solidshare-reject",
        body = buildRejectTurtle(ownerWebId, requesterWebId, resourceUri, reason),
    )

    suspend fun postAccept(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: URI,
        mode: ShareMode,
        requestUri: String?,
    ): InboxPostResult = postFromSenderToReceiver(
        senderWebId = ownerWebId,
        receiverWebId = requesterWebId,
        slugPrefix = "solidshare-accept",
        body = buildAcceptTurtle(ownerWebId, requesterWebId, resourceUri, mode, requestUri),
    )

    private suspend fun postFromSenderToReceiver(
        senderWebId: String,
        receiverWebId: String,
        slugPrefix: String,
        body: String,
    ): InboxPostResult {
        val inbox = resolveInbox(senderWebId, receiverWebId)
            ?: return InboxPostResult.NoInbox(receiverWebId)
        val slug = "$slugPrefix-${java.util.UUID.randomUUID()}"
        return when (
            val r = rm.post(
                webid = senderWebId,
                uri = inbox,
                contentType = HTTPAcceptType.TURTLE,
                body = body.toByteArray(),
                additionalHeaders = mapOf("Slug" to slug),
            )
        ) {
            is SolidNetworkResponse.Success -> InboxPostResult.Success(r.data)
            is SolidNetworkResponse.Error -> when (r.errorCode) {
                401 -> InboxPostResult.Unauthorized(inbox)
                403 -> InboxPostResult.Forbidden(inbox)
                else -> InboxPostResult.HttpError(inbox, r.errorCode)
            }

            is SolidNetworkResponse.Exception -> InboxPostResult.NetworkError(inbox, r.exception)
        }
    }

    private suspend fun resolveInbox(senderWebId: String, receiverWebId: String): URI? {
        val profile = runCatching {
            rm.readPublic(URI.create(receiverWebId), WebId::class.java).getOrThrow()
        }.getOrNull()
        profile?.getInbox()?.let { return it }

        runCatching {
            when (val r = rm.headPublic(URI.create(receiverWebId))) {
                is SolidNetworkResponse.Success -> r.data.inboxUri
                else -> null
            }
        }.getOrNull()?.let { return it }

        profile?.let { p ->
            (p.getPrimaryTopicDocuments() + p.getRelatedResources()).distinct().forEach { doc ->
                runCatching {
                    rm.readPublic(doc, WebId::class.java).getOrThrow().getInbox()
                }.getOrNull()?.let { return it }
                runCatching {
                    rm.read(senderWebId, doc, WebId::class.java).getOrThrow().getInbox()
                }.getOrNull()?.let { return it }
            }
        }

        profile?.getStorages()?.firstOrNull()?.let { storage ->
            val root = storage.toString().let { if (it.endsWith("/")) it else "$it/" }
            return URI.create("${root}inbox/")
        }
        return null
    }

    private fun buildOfferTurtle(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: URI,
        mode: ShareMode,
    ): String = buildString {
        appendLine("@prefix as:  <${AS.NAMESPACE}> .")
        appendLine("@prefix rdf: <${RDF.NAMESPACE}> .")
        appendLine("@prefix xsd: <${XSD.NAMESPACE}> .")
        appendLine()
        appendLine("<#offer>")
        appendLine("    rdf:type           <${AS.OFFER}> ;")
        appendLine("    <${AS.ACTOR}>      <$ownerWebId> ;")
        appendLine("    <${AS.OBJECT}>     <$resourceUri> ;")
        appendLine("    <${AS.TARGET}>     <$receiverWebId> ;")
        appendLine("    <${ACL.MODE}>      <${mode.toAclPredicate()}> ;")
        appendLine("    <${AS.PUBLISHED}>  \"${Instant.now()}\"^^xsd:dateTime .")
    }

    private fun buildUndoTurtle(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: URI,
        mode: ShareMode?,
    ): String = buildString {
        appendLine("@prefix as:  <${AS.NAMESPACE}> .")
        appendLine("@prefix rdf: <${RDF.NAMESPACE}> .")
        appendLine("@prefix xsd: <${XSD.NAMESPACE}> .")
        appendLine()
        appendLine("<#undo>")
        appendLine("    rdf:type           <${AS.UNDO}> ;")
        appendLine("    <${AS.ACTOR}>      <$ownerWebId> ;")
        appendLine("    <${AS.OBJECT}>     <$resourceUri> ;")
        appendLine("    <${AS.TARGET}>     <$receiverWebId> ;")
        if (mode != null) {
            appendLine("    <${ACL.MODE}>      <${mode.toAclPredicate()}> ;")
        }
        appendLine("    <${AS.PUBLISHED}>  \"${Instant.now()}\"^^xsd:dateTime .")
    }

    private fun buildRequestTurtle(
        requesterWebId: String,
        ownerWebId: String,
        resourceUri: URI,
        requestedMode: ShareMode,
        summary: String?,
    ): String = buildString {
        appendLine("@prefix as:  <${AS.NAMESPACE}> .")
        appendLine("@prefix rdf: <${RDF.NAMESPACE}> .")
        appendLine("@prefix xsd: <${XSD.NAMESPACE}> .")
        appendLine()
        appendLine("<#req>")
        appendLine("    rdf:type                <${SAI.ACCESS_REQUEST}> ;")
        appendLine("    <${AS.ACTOR}>           <$requesterWebId> ;")
        appendLine("    <${AS.OBJECT}>          <$resourceUri> ;")
        appendLine("    <${AS.TARGET}>          <$ownerWebId> ;")
        appendLine("    <${ACL.MODE}>           <${requestedMode.toAclPredicate()}> ;")
        if (summary != null) {
            appendLine("    <${AS.SUMMARY}>         ${escapeLiteral(summary)} ;")
        }
        appendLine("    <${AS.PUBLISHED}>       \"${Instant.now()}\"^^xsd:dateTime .")
    }

    private fun buildAcceptTurtle(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: URI,
        mode: ShareMode,
        requestUri: String?,
    ): String = buildString {
        appendLine("@prefix as:  <${AS.NAMESPACE}> .")
        appendLine("@prefix rdf: <${RDF.NAMESPACE}> .")
        appendLine("@prefix xsd: <${XSD.NAMESPACE}> .")
        appendLine()
        appendLine("<#accept>")
        appendLine("    rdf:type           <${AS.ACCEPT}> ;")
        appendLine("    <${AS.ACTOR}>      <$ownerWebId> ;")
        appendLine("    <${AS.OBJECT}>     <$resourceUri> ;")
        appendLine("    <${AS.TARGET}>     <$requesterWebId> ;")
        appendLine("    <${ACL.MODE}>      <${mode.toAclPredicate()}> ;")
        if (requestUri != null) {
            appendLine("    <${AS.IN_REPLY_TO}> <$requestUri> ;")
        }
        appendLine("    <${AS.PUBLISHED}>  \"${Instant.now()}\"^^xsd:dateTime .")
    }

    private fun buildRejectTurtle(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: URI,
        reason: String?,
    ): String = buildString {
        appendLine("@prefix as:  <${AS.NAMESPACE}> .")
        appendLine("@prefix rdf: <${RDF.NAMESPACE}> .")
        appendLine("@prefix xsd: <${XSD.NAMESPACE}> .")
        appendLine()
        appendLine("<#reject>")
        appendLine("    rdf:type           <${AS.REJECT}> ;")
        appendLine("    <${AS.ACTOR}>      <$ownerWebId> ;")
        appendLine("    <${AS.OBJECT}>     <$resourceUri> ;")
        appendLine("    <${AS.TARGET}>     <$requesterWebId> ;")
        if (reason != null) {
            appendLine("    <${AS.SUMMARY}>    ${escapeLiteral(reason)} ;")
        }
        appendLine("    <${AS.PUBLISHED}>  \"${Instant.now()}\"^^xsd:dateTime .")
    }

    private fun escapeLiteral(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        return "\"$escaped\""
    }
}

/**
 * Typed result of an inbox POST. Used by [InboxNotifier]; the
 * [com.erfangholami.androidsolidservices.api.notifications.NotificationsManager]
 * layer converts the variants to
 * [com.erfangholami.androidsolidservices.api.exceptions.SharingException]
 * for the public boundary.
 */
internal sealed class InboxPostResult {
    /** Server returned 2xx. [locationUri] is the server-allocated URI, when present. */
    data class Success(val locationUri: java.net.URI?) : InboxPostResult()

    /** Receiver's WebID profile and HEAD-link both fail to yield an inbox URI. */
    data class NoInbox(val targetWebId: String) : InboxPostResult()

    /** Server responded 401. */
    data class Unauthorized(val inboxUri: java.net.URI) : InboxPostResult()

    /** Server responded 403. */
    data class Forbidden(val inboxUri: java.net.URI) : InboxPostResult()

    /** Server responded with another non-2xx status. */
    data class HttpError(val inboxUri: java.net.URI, val statusCode: Int) : InboxPostResult()

    /** No HTTP response at all (network failure, timeout, DNS, etc.). */
    data class NetworkError(val inboxUri: java.net.URI, val cause: Throwable) : InboxPostResult()
}

