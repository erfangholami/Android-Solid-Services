package com.erfangholami.androidsolidservices.client.internal.fakes

import android.content.Context
import android.os.Bundle
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Records what a fake service actually received, so the test process can read it back.
 *
 * The fakes are hosted in `:fakeass`, so no static field, singleton or in-memory channel reaches
 * the tests — that separation is the whole point of the suite. Both processes belong to the same
 * app and therefore share `filesDir`, which makes an appended file the simplest channel that does
 * not itself depend on the IPC under test.
 *
 * This is what turns "the call returned" into "the call arrived with the arguments I sent". A proxy
 * that swaps two same-typed parameters returns perfectly good results; only the recorded arguments
 * show it.
 */
internal object CallLog {

    private const val FILE_NAME = "ipc-calls.log"
    const val NULL: String = "null"

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun clear(context: Context) {
        file(context).delete()
    }

    /** Called from the fake, on a binder thread in the other process. */
    fun record(
        context: Context,
        method: String,
        vararg args: Pair<String, Any?>,
    ) {
        val line = JSONObject()
            .put("method", method)
            .put("args", JSONObject().apply { args.forEach { (k, v) -> put(k, describe(v)) } })
            .toString()

        synchronized(this) {
            // "rws" flushes content and metadata on every write, so the record is durable before
            // the fake answers the callback and the test wakes up.
            RandomAccessFile(file(context), "rws").use { raf ->
                raf.seek(raf.length())
                raf.write((line + "\n").toByteArray())
            }
        }
    }

    fun entries(context: Context): List<Entry> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return f.readLines().filter { it.isNotBlank() }.map { line ->
            val json = JSONObject(line)
            val args = json.getJSONObject("args")
            Entry(
                method = json.getString("method"),
                args = args.keys().asSequence().associateWith { args.getString(it) },
            )
        }
    }

    /**
     * The most recent record of [method]. Polls because the fake writes on its own binder thread:
     * the write completes before the callback is dispatched, but the two processes are not
     * otherwise ordered.
     */
    fun await(
        context: Context,
        method: String,
        timeoutMs: Long = 5_000L,
    ): Entry {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            entries(context).lastOrNull { it.method == method }?.let { return it }
            Thread.sleep(20)
        }
        throw AssertionError(
            "the fake never recorded a call to $method — it saw ${entries(context).map(Entry::method)}",
        )
    }

    /**
     * Renders a value as the string both processes agree on. Data classes carry every field into
     * `toString()`, which is exactly the fidelity assertion these tests want; byte arrays are
     * reduced to a length and digest so a megabyte payload does not land in the log.
     */
    fun describe(value: Any?): String = when (value) {
        null -> NULL
        is ByteArray -> "bytes(size=${value.size}, sha256=${digest(value)})"
        is Bundle -> value.keySet().sorted()
            .joinToString(prefix = "{", postfix = "}") { "$it=${value.getString(it)}" }

        is Map<*, *> -> value.entries.sortedBy { it.key.toString() }
            .joinToString(prefix = "{", postfix = "}") { "${it.key}=${describe(it.value)}" }

        is List<*> -> value.joinToString(prefix = "[", postfix = "]") { describe(it) }
        else -> value.toString()
    }

    fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .take(8)
            .joinToString("") { "%02x".format(it) }

    data class Entry(val method: String, val args: Map<String, String>) {

        fun arg(name: String): String =
            args[name] ?: throw AssertionError(
                "$method recorded no argument named '$name' — it has ${args.keys}",
            )
    }
}
