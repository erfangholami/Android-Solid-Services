package com.erfangholami.androidsolidservices.shared.model.tickets;

import com.erfangholami.androidsolidservices.shared.model.tickets.Ticket;

/** One-way callback delivering a single Ticket from a tickets module operation. */
interface IASSTicketCallback {
    oneway void onResult(in @nullable Ticket ticket);
    oneway void onError(int errorCode, String errorMessage);
}
