package com.erfangholami.androidsolidservices.client.sdk

import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.InputStream

/**
 * A resource body being streamed across from the Android Solid Services app.
 *
 * The bytes are *not* parcelled — they would not survive the ~1 MB Binder transaction
 * limit. They arrive through a pipe, so a multi-megabyte download is never materialised in
 * memory on either side; you read them as they arrive.
 *
 * The caller **must** close this (the pipe stays open until then). Use it in a `use { }`
 * block:
 *
 * ```
 * client.readStream(webId, uri).use { body ->
 *     body.stream().copyTo(destination)
 * }
 * ```
 *
 * @property contentType The body's media type.
 * @property contentLength The advertised length, or `-1` when the server did not give one.
 */
public class SolidStream internal constructor(
    public val contentType: String,
    public val contentLength: Long,
    descriptor: ParcelFileDescriptor,
) : Closeable {

    private val source: InputStream = ParcelFileDescriptor.AutoCloseInputStream(descriptor)

    /**
     * The body as a live stream. Reading blocks until the service has piped more bytes
     * across. Closing this (or the [SolidStream]) releases the pipe.
     */
    public fun stream(): InputStream = source

    override fun close() {
        source.close()
    }
}
