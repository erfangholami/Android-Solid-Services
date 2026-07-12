package com.erfangholami.androidsolidservices.api.resource

import java.io.Closeable
import java.io.InputStream

/**
 * A resource body exposed as a live, unbuffered [stream] — the streaming counterpart to
 * reading a [com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource]
 * (whose bytes are fully materialised in memory).
 *
 * Returned by [SolidResourceManager.readStream]. The underlying network response stays open
 * until this is [close]d, so callers **must** close it (a `use { }` block is idiomatic):
 *
 * ```kotlin
 * rm.readStream(webId, uri).getOrThrow().use { res ->
 *     res.stream().copyTo(fileOut)   // count bytes here for download progress
 * }
 * ```
 *
 * @property uri The resource URI this body was read from.
 * @property contentType The response `Content-Type`.
 * @property contentLength The `Content-Length` in bytes, or `-1` when the server did not report one.
 */
public class StreamingResource internal constructor(
    public val uri: java.net.URI,
    public val contentType: String,
    public val contentLength: Long,
    private val stream: InputStream,
    private val onClose: () -> Unit,
) : Closeable {

    /** The live body stream. Read it once; it is closed by [close]. */
    public fun stream(): InputStream = stream

    override fun close() {
        try {
            stream.close()
        } finally {
            onClose()
        }
    }
}
