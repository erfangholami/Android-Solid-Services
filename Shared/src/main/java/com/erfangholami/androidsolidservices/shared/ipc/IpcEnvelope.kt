package com.erfangholami.androidsolidservices.shared.ipc

import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Parcelable

/**
 * The Bundle envelope carried by the two generic AIDL callbacks,
 * `IASSParcelableCallback` and `IASSParcelableListCallback`.
 *
 * AIDL has no generic `Parcelable` parameter, so a callback that could return "whatever this
 * verb answers with" used to mean one interface per return type. A Bundle is the one wire type
 * that can hold any of them, so both callbacks take a Bundle and this file owns the keys, the
 * packing ([IpcEnvelope]) and the unpacking (the `Bundle?` readers below).
 *
 * Everything an ASS verb answers with fits: a single Parcelable, a list of them, a String, a
 * boolean, nothing at all, an open stream, or a login outcome.
 */
public object IpcEnvelope {

    /** The result value: the Parcelable, the ParcelableArrayList, the String, or the boolean. */
    public const val KEY_VALUE: String = "ass.ipc.value"

    /** A stream's `Content-Type`, alongside the [KEY_VALUE] file descriptor. */
    public const val KEY_CONTENT_TYPE: String = "ass.ipc.contentType"

    /** A stream's `Content-Length`, or `-1` when the server advertised none. */
    public const val KEY_CONTENT_LENGTH: String = "ass.ipc.contentLength"

    /** Whether a login was granted, alongside the [KEY_VALUE] WebID the user picked. */
    public const val KEY_GRANTED: String = "ass.ipc.granted"

    /** Envelopes a single [value]; a `null` value is a legitimate answer, not an error. */
    public fun of(value: Parcelable?): Bundle = Bundle(1).apply { putParcelable(KEY_VALUE, value) }

    /** Envelopes [values] as a ParcelableArrayList. A `null` list envelopes as an absent value. */
    public fun ofList(values: List<Parcelable>?): Bundle = Bundle(1).apply {
        putParcelableArrayList(KEY_VALUE, values?.let { ArrayList(it) })
    }

    /** Envelopes a single [value] String. */
    public fun ofString(value: String?): Bundle = Bundle(1).apply { putString(KEY_VALUE, value) }

    /** Envelopes a single [value] boolean. */
    public fun ofBoolean(value: Boolean): Bundle = Bundle(1).apply { putBoolean(KEY_VALUE, value) }

    /** The answer of an operation that reports only that it succeeded. */
    public fun empty(): Bundle = Bundle(0)

    /**
     * Envelopes an open read stream: the descriptor the caller reads and must close, plus the
     * content type and the length ([contentLength] is `-1` when the server advertised none).
     *
     * The body itself is never parcelled — it would not survive the Binder transaction limit.
     */
    public fun ofStream(source: ParcelFileDescriptor, contentType: String, contentLength: Long): Bundle =
        Bundle(3).apply {
            putParcelable(KEY_VALUE, source)
            putString(KEY_CONTENT_TYPE, contentType)
            putLong(KEY_CONTENT_LENGTH, contentLength)
        }

    /** Envelopes a login outcome: whether it was [granted] and the WebID the user selected. */
    public fun ofLogin(granted: Boolean, selectedWebId: String?): Bundle = Bundle(2).apply {
        putBoolean(KEY_GRANTED, granted)
        putString(KEY_VALUE, selectedWebId)
    }

    internal const val UNKNOWN_LENGTH: Long = -1L
}

/**
 * Reads the single Parcelable of [expected] type, or `null` when the envelope carries none.
 *
 * This — and every reader below it — sets the Bundle's class loader before touching it. A Bundle
 * that crosses a process boundary arrives lazily parcelled with the *system* class loader, which
 * cannot see this library's model classes, so reading one without that step throws
 * `BadParcelableException`. No call site should have to remember it.
 */
public fun <T : Parcelable> Bundle?.parcelable(expected: Class<T>): T? {
    val envelope = this?.withClassLoaderOf(expected) ?: return null
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        envelope.getParcelable(IpcEnvelope.KEY_VALUE, expected)
    } else {
        @Suppress("DEPRECATION")
        envelope.getParcelable<Parcelable>(IpcEnvelope.KEY_VALUE)?.let(expected::cast)
    }
}

/** Reads the list of Parcelables of [expected] type; an absent value reads as empty. */
public fun <T : Parcelable> Bundle?.parcelableList(expected: Class<T>): List<T> {
    val envelope = this?.withClassLoaderOf(expected) ?: return emptyList()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        envelope.getParcelableArrayList(IpcEnvelope.KEY_VALUE, expected).orEmpty()
    } else {
        @Suppress("DEPRECATION")
        envelope.getParcelableArrayList<Parcelable>(IpcEnvelope.KEY_VALUE).orEmpty().map(expected::cast)
    }
}

/** Reads the single String value, or `null` when the envelope carries none. */
public fun Bundle?.stringValue(): String? = this?.getString(IpcEnvelope.KEY_VALUE)

/** Reads the single boolean value; an absent value reads as `false`. */
public fun Bundle?.booleanValue(): Boolean = this?.getBoolean(IpcEnvelope.KEY_VALUE) ?: false

/** Reads a stream's content type, or the empty String when the envelope carries none. */
public fun Bundle?.streamContentType(): String = this?.getString(IpcEnvelope.KEY_CONTENT_TYPE).orEmpty()

/** Reads a stream's content length; an absent value reads as `-1`, meaning "unknown". */
public fun Bundle?.streamContentLength(): Long =
    this?.getLong(IpcEnvelope.KEY_CONTENT_LENGTH, IpcEnvelope.UNKNOWN_LENGTH) ?: IpcEnvelope.UNKNOWN_LENGTH

/** Reads whether a login was granted; an absent value reads as `false`. */
public fun Bundle?.loginGranted(): Boolean = this?.getBoolean(IpcEnvelope.KEY_GRANTED) ?: false

private fun Bundle.withClassLoaderOf(expected: Class<*>): Bundle = apply {
    classLoader = expected.classLoader
}
