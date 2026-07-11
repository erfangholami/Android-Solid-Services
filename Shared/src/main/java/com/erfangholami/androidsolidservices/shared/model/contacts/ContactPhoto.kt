package com.erfangholami.androidsolidservices.shared.model.contacts

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
public class ContactPhoto(
    public val uri: String,
    public val contentType: String,
    public val bytes: ByteArray,
) : Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContactPhoto) return false
        return uri == other.uri &&
                contentType == other.contentType &&
                bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = uri.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}
