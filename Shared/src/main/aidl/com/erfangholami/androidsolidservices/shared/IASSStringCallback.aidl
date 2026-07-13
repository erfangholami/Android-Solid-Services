package com.erfangholami.androidsolidservices.shared;

/**
 * One-way callback delivering a single string — a resource location minted by the server
 * (the Location of a POST / createInContainer) or echoed back by copy / move / rename.
 */
interface IASSStringCallback {
    oneway void onResult(@nullable String value);
    oneway void onError(int errorCode, String errorMessage);
}
