package com.erfangholami.androidsolidservices.shared.model.resource;

/**
 * One-way callback handing back a live read stream for a resource body.
 *
 * The body is NOT parcelled — it would not survive the ~1 MB Binder transaction limit.
 * Instead the service writes it into a pipe and returns the read end; the caller reads from
 * [source] and must close it. [contentLength] is -1 when the server did not advertise one.
 */
interface IASSStreamCallback {
    oneway void onResult(in ParcelFileDescriptor source, String contentType, long contentLength);
    oneway void onError(int errorCode, String errorMessage);
}
