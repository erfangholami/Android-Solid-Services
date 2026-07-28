package com.erfangholami.androidsolidservices.shared.model.tickets;

import com.erfangholami.androidsolidservices.shared.model.tickets.Ticket;
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket;
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicketImages;
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketList;
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketArtifact;
import com.erfangholami.androidsolidservices.shared.model.tickets.IASSTicketCallback;
import com.erfangholami.androidsolidservices.shared.model.tickets.IASSTicketListCallback;
import com.erfangholami.androidsolidservices.shared.model.tickets.IASSTicketArtifactCallback;

/**
 * AIDL IPC contract for the Solid Tickets data module — the wallet: `schema:Ticket`
 * resources on the user's pod, registered in the Solid type index.
 *
 * Mirrors the in-process `TicketStore` one-for-one. Third-party apps normally use the
 * higher-level client SDK (`Solid.getTicketsDataModule(...)`) rather than binding here
 * directly.
 */
interface IASSTicketsModuleInterface {

    /** Returns the cached tickets index for [webId] — enough to render a wallet list. */
    void listTickets(String webId, IASSTicketListCallback callback);

    /** Reads the full ticket document at [ticketUri]. */
    void getTicket(String webId, String ticketUri, IASSTicketCallback callback);

    /**
     * Creates a ticket in its own sub-container of the tickets container. When [artifact]
     * is non-null it is stored inside that sub-container (e.g. the original .pkpass) and
     * linked from the ticket; [artifactContentType] is then required. [images] adds the
     * pass images the same way. [storage] / [container] override the registered or default
     * location, and [isPrivate] selects the private (default) or public type index.
     */
    void createTicket(
        String webId,
        in NewTicket newTicket,
        @nullable String storage,
        in @nullable byte[] artifact,
        @nullable String artifactContentType,
        in @nullable NewTicketImages images,
        boolean isPrivate,
        @nullable String container,
        IASSTicketCallback callback
    );

    /**
     * Rewrites the ticket at [ticketUri] from [updated] with replace semantics: properties
     * absent from [updated] are removed. The created date and the artifact link are preserved.
     */
    void updateTicket(
        String webId,
        String ticketUri,
        in NewTicket updated,
        IASSTicketCallback callback
    );

    /**
     * Replaces the ticket's stored artifact (and any provided pass-image roles) with fresh
     * bytes, refreshing the document links. Subject to the ~1 MB Binder transaction limit.
     */
    void putTicketArtifact(
        String webId,
        String ticketUri,
        in byte[] artifact,
        String artifactContentType,
        in @nullable NewTicketImages images,
        IASSTicketCallback callback
    );

    /**
     * Deletes the ticket at [ticketUri]: its index row and its whole sub-container (document,
     * artifact, stored images). Returns the removed ticket.
     */
    void deleteTicket(String webId, String ticketUri, IASSTicketCallback callback);

    /**
     * Reads a binary stored with a ticket (the artifact or a stored pass image). Subject to
     * the ~1 MB Binder transaction limit.
     */
    void getTicketArtifact(String webId, String artifactUri, IASSTicketArtifactCallback callback);
}
