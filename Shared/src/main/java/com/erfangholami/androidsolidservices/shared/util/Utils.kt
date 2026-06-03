package com.erfangholami.androidsolidservices.shared.util

import java.io.InputStream

/** Reads this [InputStream] to a UTF-8 string and closes it. */
public fun InputStream.toPlainString(): String {
    return this.bufferedReader().use { it.readText() }
}