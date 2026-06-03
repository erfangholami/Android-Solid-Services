package com.erfangholami.androidsolidservices.shared.model.sharing;

import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotification;

/** One-way callback delivering the list of LDN inbox notifications (share offers, undos) for the user's WebID. */
interface IASSShareNotificationListCallback {
    oneway void onResult(in List<ShareNotification> notifications);
    oneway void onError(int errorCode, String errorMessage);
}
