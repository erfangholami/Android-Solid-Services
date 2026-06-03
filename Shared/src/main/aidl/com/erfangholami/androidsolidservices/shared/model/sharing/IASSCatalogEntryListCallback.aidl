package com.erfangholami.androidsolidservices.shared.model.sharing;

import com.erfangholami.androidsolidservices.shared.model.sharing.CatalogEntry;

/** One-way callback delivering the list of publicly discoverable catalog entries from a pod owner. */
interface IASSCatalogEntryListCallback {
    oneway void onResult(in List<CatalogEntry> entries);
    oneway void onError(int errorCode, String errorMessage);
}
