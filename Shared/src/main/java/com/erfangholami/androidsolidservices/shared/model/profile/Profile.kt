package com.erfangholami.androidsolidservices.shared.model.profile

import com.erfangholami.androidsolidservices.shared.model.profile.WebId.Companion.readFromString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.Json
import net.openid.appauth.AuthState

/**
 * The set of all signed-in Solid profiles, keyed by WebID string.
 *
 * Persisted locally via DataStore. Use [contains] or [getProfileOrNull] to look up
 * a specific account before issuing pod operations.
 */
@Serializable
public data class ProfileList(
    @Serializable(with = ProfileMapSerializer::class)
    val profiles: Map<String, Profile> = mapOf()
)

/** Returns `true` if [webId] has an active profile in this list. */
public fun ProfileList.contains(webId: String): Boolean {
    return profiles.containsKey(webId)
}

/** Returns the [Profile] for [webId], or `null` if not signed in. */
public fun ProfileList.getProfileOrNull(webId: String): Profile? {
    return profiles[webId]
}

/**
 * The local state for a single signed-in Solid account.
 *
 * @property authState The AppAuth [AuthState] holding the OIDC token set for this account.
 * @property userInfo  The OIDC `userinfo` response for this account, or `null` if not yet fetched.
 * @property webId     The parsed WebID profile document, or `null` if not yet fetched.
 * @property dpopKeyId Identifier of this account's own DPoP key in the Android Keystore. `null` for
 *   profiles created before per-account keys existed; those fall back to the shared legacy key.
 */
@Serializable(with = ProfileSerializer::class)
public data class Profile(
    val authState: AuthState = AuthState(),
    val userInfo: UserInfo? = null,
    val webId: WebId? = null,
    val dpopKeyId: String? = null,
)

/**
 * `kotlinx.serialization` serializer for `Map<String, Profile>`.
 *
 * Delegates to [MapSerializer] using the provided key and value serializers, allowing
 * [ProfileList.profiles] to be stored as a JSON object keyed by WebID string.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = Map::class)
public class ProfileMapSerializer(
    private val keySerializer: KSerializer<String>,
    private val valueSerializer: KSerializer<Profile>
) : KSerializer<Map<String, Profile>> {
    override fun serialize(encoder: Encoder, value: Map<String, Profile>) {
        MapSerializer(keySerializer, valueSerializer)
            .serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): Map<String, Profile> {
        return MapSerializer(keySerializer, valueSerializer)
            .deserialize(decoder)
    }
}

/**
 * `kotlinx.serialization` serializer for [Profile].
 *
 * Each field is stored as a JSON string element: [Profile.authState] is serialized via
 * AppAuth's own JSON serialization, [Profile.userInfo] via `kotlinx.serialization`, and
 * [Profile.webId] via [WebId.writeToString]. Empty string is used as the absent sentinel
 * for nullable fields so the format remains a flat JSON object with three string fields.
 */
public class ProfileSerializer : KSerializer<Profile> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Profile") {
        element<String>("authState")
        element<String>("userInfo")
        element<String>("webId")
        element<String>("dpopKeyId")
    }

    override fun serialize(encoder: Encoder, value: Profile) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.authState.jsonSerializeString())
            encodeStringElement(
                descriptor,
                1,
                if (value.userInfo != null) Json.encodeToString(value.userInfo) else ""
            )
            encodeStringElement(descriptor, 2, WebId.Companion.writeToString(value.webId) ?: "")
            encodeStringElement(descriptor, 3, value.dpopKeyId ?: "")
        }
    }

    override fun deserialize(decoder: Decoder): Profile {
        var stateString = ""
        var userInfoString = ""
        var webIdString = ""
        var dpopKeyIdString = ""

        decoder.decodeStructure(descriptor) {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> stateString = decodeStringElement(descriptor, 0)
                    1 -> userInfoString = decodeStringElement(descriptor, 1)
                    2 -> webIdString = decodeStringElement(descriptor, 2)
                    3 -> dpopKeyIdString = decodeStringElement(descriptor, 3)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
        }

        return Profile(
            authState = if (stateString.isNotEmpty()) AuthState.jsonDeserialize(stateString) else AuthState(),
            userInfo = if (userInfoString.isNotEmpty()) Json.decodeFromString<UserInfo>(
                userInfoString
            ) else null,
            webId = if (webIdString.isNotEmpty()) readFromString(webIdString) else null,
            dpopKeyId = dpopKeyIdString.takeIf { it.isNotEmpty() },
        )
    }
}
