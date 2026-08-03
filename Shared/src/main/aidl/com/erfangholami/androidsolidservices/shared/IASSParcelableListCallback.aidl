package com.erfangholami.androidsolidservices.shared;

/**
 * The one callback every IPC verb that answers with a list of values uses.
 *
 * Identical in shape to IASSParcelableCallback — the Bundle under `IpcEnvelope.KEY_VALUE`
 * carries a ParcelableArrayList instead of a single Parcelable. Kept as its own interface so
 * the cardinality of a verb's answer is visible in the AIDL signature rather than only in the
 * SDK that reads it.
 *
 * Build the Bundle with `IpcEnvelope.ofList` and read it with `IpcEnvelope.parcelableList`.
 */
interface IASSParcelableListCallback {
    void onResult(in @nullable Bundle result);
    void onError(int errorCode, String errorMessage);
}
