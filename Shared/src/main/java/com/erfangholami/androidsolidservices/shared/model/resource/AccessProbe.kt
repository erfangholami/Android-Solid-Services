package com.erfangholami.androidsolidservices.shared.model.resource

import android.os.Parcel
import android.os.Parcelable
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode

/**
 * Parcel discriminators for the sealed [AccessProbe] hierarchy. They live at file scope
 * because an interface companion holding a `@JvmField` (the required `CREATOR`) may not
 * also hold ordinary properties.
 */
private const val TYPE_ACCESSIBLE = 0
private const val TYPE_DENIED = 1

/**
 * The outcome of a `probeAccess` call: what access the current user effectively holds on a
 * resource, read from its `WAC-Allow` response header.
 *
 * Note the third state — "couldn't determine" — is *not* modelled here: it is a
 * [com.erfangholami.androidsolidservices.shared.result.SolidResult.Failure] on the probe (a
 * 401 blip, 5xx, or transport error), so a caller must not mistake it for [Denied]. Only
 * [Accessible] and [Denied] are authoritative answers.
 *
 * Lives in `shared` (and is [Parcelable]) because the probe is offered by both the
 * in-process API and the IPC surface, and both must speak the same type.
 *
 * The [Parcelable] plumbing is hand-written rather than `@Parcelize`d: this is a *sealed*
 * hierarchy, so each variant has to stamp a discriminator that the single [CREATOR] on the
 * parent reads back to reconstruct the right subtype.
 */
public sealed interface AccessProbe : Parcelable {

    /**
     * The resource is reachable and the user holds [modes] (a subset of
     * View/Add/Edit — i.e. Read/Append/Write). [ownerWebId] is the resource's
     * `solid:owner`, or `null` when the server does not advertise one.
     */
    public data class Accessible(
        val modes: Set<ShareMode>,
        val ownerWebId: String?,
    ) : AccessProbe {

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeInt(TYPE_ACCESSIBLE)
            dest.writeStringList(modes.map { it.name })
            dest.writeString(ownerWebId)
        }
    }

    /** The resource exists but access is denied (403), or it does not exist (404). */
    public data object Denied : AccessProbe {

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeInt(TYPE_DENIED)
        }
    }

    public companion object {

        @JvmField
        public val CREATOR: Parcelable.Creator<AccessProbe> =
            object : Parcelable.Creator<AccessProbe> {

                override fun createFromParcel(source: Parcel): AccessProbe =
                    when (source.readInt()) {
                        TYPE_ACCESSIBLE -> Accessible(
                            modes = source.createStringArrayList()
                                .orEmpty()
                                .mapNotNull { name ->
                                    runCatching { ShareMode.valueOf(name) }.getOrNull()
                                }
                                .toSet(),
                            ownerWebId = source.readString(),
                        )

                        else -> Denied
                    }

                override fun newArray(size: Int): Array<AccessProbe?> = arrayOfNulls(size)
            }
    }
}
