package com.erfangholami.androidsolidservices.shared;

/**
 * The one callback every IPC verb that answers with a single value uses.
 *
 * AIDL cannot express a generic `Parcelable` parameter, so the value travels inside a Bundle
 * envelope whose keys are named by `shared/ipc/IpcEnvelope.kt` — `KEY_VALUE` for the value
 * itself, plus the few auxiliary keys the multi-part answers (a stream, a login outcome) need.
 * The same envelope carries the primitive answers: a String, a boolean, or nothing at all for
 * an operation that only reports success.
 *
 * Build the Bundle with `IpcEnvelope.of…` and read it with `IpcEnvelope.parcelable` — the
 * reader sets the Bundle's class loader, which a caller must not be left to remember.
 *
 * This replaced a per-return-type callback interface for every value the services hand back.
 */
interface IASSParcelableCallback {
    void onResult(in @nullable Bundle result);
    void onError(int errorCode, String errorMessage);
}
