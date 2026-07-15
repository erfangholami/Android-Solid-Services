package com.erfangholami.androidsolidservices.api.sharing

import com.erfangholami.androidsolidservices.api.sharing.implementation.SolidShareLinkCodec
import com.erfangholami.androidsolidservices.shared.vocab.ShareVocabulary
import com.erfangholami.androidsolidservices.shared.vocab.SolidShareVocabulary

/**
 * The set of SolidShare-specific conventions the sharing engine is parameterized
 * over, so the same engine ([SharingManager]) can run under a different RDF
 * vocabulary, pod layout, and link scheme — or with the public catalog turned
 * off — instead of being hard-wired to SolidShare's choices.
 *
 * Pass a profile to [SharingManager.getInstance]; the default is
 * [SolidShareProfile], which reproduces SolidShare's on-pod format exactly, so
 * existing pods keep working unchanged.
 */
public interface SharingProfile {
    /** RDF vocabulary for the given/received index and catalog records. */
    public val vocabulary: ShareVocabulary

    /** Where the indexes and catalog live on the pod. */
    public val storageLayout: ShareStorageLayout

    /** How shareable links and QR payloads are encoded. */
    public val linkCodec: ShareLinkCodec

    /** Whether the owner-published public catalog feature is available. */
    public val catalogEnabled: Boolean
}

/**
 * The default profile: SolidShare's vocabulary (`https://solidshare.app/ns#`),
 * `solidshare/` pod layout, `solidshare://` links, and the catalog enabled.
 */
public object SolidShareProfile : SharingProfile {
    override val vocabulary: ShareVocabulary = SolidShareVocabulary
    override val storageLayout: ShareStorageLayout = SolidShareStorageLayout
    override val linkCodec: ShareLinkCodec = SolidShareLinkCodec
    override val catalogEnabled: Boolean = true
}
