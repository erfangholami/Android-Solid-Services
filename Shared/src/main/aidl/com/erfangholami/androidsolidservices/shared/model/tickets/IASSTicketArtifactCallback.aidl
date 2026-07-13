package com.erfangholami.androidsolidservices.shared.model.tickets;

import com.erfangholami.androidsolidservices.shared.model.tickets.TicketArtifact;

/**
 * One-way callback delivering a ticket's binary artifact (e.g. the original .pkpass).
 *
 * The artifact carries its bytes inline, so it is subject to the ~1 MB Binder transaction
 * limit; a larger artifact will fail the IPC call rather than be truncated.
 */
interface IASSTicketArtifactCallback {
    oneway void onResult(in @nullable TicketArtifact artifact);
    oneway void onError(int errorCode, String errorMessage);
}
