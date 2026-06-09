package com.erfangholami.androidsolidservices.api.notifications.implementation

import com.erfangholami.androidsolidservices.api.notifications.NotificationTransport
import com.erfangholami.androidsolidservices.api.notifications.ShareNotificationProfile
import com.erfangholami.androidsolidservices.shared.http.HTTPAcceptType
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.vocab.ACL
import com.erfangholami.androidsolidservices.shared.vocab.AS
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.SAI
import com.erfangholami.androidsolidservices.shared.vocab.XSD
import java.net.URI
import java.time.Instant
import java.util.UUID

internal class InboxNotifier(
    private val transport: NotificationTransport,
    private val discovery: InboxDiscovery,
    private val profile: ShareNotificationProfile,
) {

    suspend fun postOffer(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: URI,
        mode: ShareMode,
    ): InboxPostResult = postFromSenderToReceiver(
        senderWebId = ownerWebId,
        receiverWebId = receiverWebId,
        slugPrefix = profile.slugs.offer,
        body = buildOfferTurtle(ownerWebId, receiverWebId, resourceUri, mode),
    )

    suspend fun postUpdate(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: URI,
        mode: ShareMode,
    ): InboxPostResult = postFromSenderToReceiver(
        senderWebId = ownerWebId,
        receiverWebId = receiverWebId,
        slugPrefix = profile.slugs.update,
        body = buildUpdateTurtle(ownerWebId, receiverWebId, resourceUri, mode),
    )

    suspend fun postUndo(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: URI,
        mode: ShareMode? = null,
    ): InboxPostResult = postFromSenderToReceiver(
        senderWebId = ownerWebId,
        receiverWebId = receiverWebId,
        slugPrefix = profile.slugs.undo,
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
        slugPrefix = profile.slugs.request,
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
        slugPrefix = profile.slugs.reject,
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
        slugPrefix = profile.slugs.accept,
        body = buildAcceptTurtle(ownerWebId, requesterWebId, resourceUri, mode, requestUri),
    )

    suspend fun postDecisionGranted(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: URI,
        mode: ShareMode,
        requestUri: String?,
    ): InboxPostResult = postFromSenderToReceiver(
        senderWebId = ownerWebId,
        receiverWebId = ownerWebId,
        slugPrefix = profile.slugs.decisionAccept,
        body = buildAcceptTurtle(ownerWebId, requesterWebId, resourceUri, mode, requestUri),
    )

    suspend fun postDecisionRejected(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: URI,
        mode: ShareMode?,
        reason: String?,
    ): InboxPostResult = postFromSenderToReceiver(
        senderWebId = ownerWebId,
        receiverWebId = ownerWebId,
        slugPrefix = profile.slugs.decisionReject,
        body = buildRejectTurtle(ownerWebId, requesterWebId, resourceUri, reason, mode),
    )

    private suspend fun postFromSenderToReceiver(
        senderWebId: String,
        receiverWebId: String,
        slugPrefix: String,
        body: String,
    ): InboxPostResult {
        val inbox = discovery.resolveInboxOf(receiverWebId, senderWebId)
            ?: return InboxPostResult.NoInbox(receiverWebId)
        val slug = "$slugPrefix-${UUID.randomUUID()}"
        return when (
            val r = transport.post(
                webId = senderWebId,
                inbox = inbox.toString(),
                contentType = HTTPAcceptType.TURTLE,
                body = body.toByteArray(),
                slug = slug,
            )
        ) {
            is SolidNetworkResponse.Success ->
                InboxPostResult.Success(r.data?.let { runCatching { URI.create(it) }.getOrNull() })

            is SolidNetworkResponse.Error -> when (r.errorCode) {
                401 -> InboxPostResult.Unauthorized(inbox)
                403 -> InboxPostResult.Forbidden(inbox)
                else -> InboxPostResult.HttpError(inbox, r.errorCode)
            }

            is SolidNetworkResponse.Exception -> InboxPostResult.NetworkError(inbox, r.exception)
        }
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

    private fun buildUpdateTurtle(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: URI,
        mode: ShareMode,
    ): String = buildString {
        appendLine("@prefix as:  <${AS.NAMESPACE}> .")
        appendLine("@prefix rdf: <${RDF.NAMESPACE}> .")
        appendLine("@prefix xsd: <${XSD.NAMESPACE}> .")
        appendLine()
        appendLine("<#update>")
        appendLine("    rdf:type           <${AS.UPDATE}> ;")
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
        mode: ShareMode? = null,
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
        if (mode != null) {
            appendLine("    <${ACL.MODE}>      <${mode.toAclPredicate()}> ;")
        }
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

internal sealed class InboxPostResult {
    data class Success(val locationUri: URI?) : InboxPostResult()
    data class NoInbox(val targetWebId: String) : InboxPostResult()
    data class Unauthorized(val inboxUri: URI) : InboxPostResult()
    data class Forbidden(val inboxUri: URI) : InboxPostResult()
    data class HttpError(val inboxUri: URI, val statusCode: Int) : InboxPostResult()
    data class NetworkError(val inboxUri: URI, val cause: Throwable) : InboxPostResult()
}
