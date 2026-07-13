package com.erfangholami.androidsolidservices.shared.model.tickets;

import com.erfangholami.androidsolidservices.shared.model.tickets.TicketList;

/** One-way callback delivering the cached tickets index (a TicketList) from a tickets module operation. */
interface IASSTicketListCallback {
    oneway void onResult(in @nullable TicketList ticketList);
    oneway void onError(int errorCode, String errorMessage);
}
