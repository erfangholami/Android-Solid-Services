package com.erfangholami.androidsolidservices.shared.vocab

import com.erfangholami.androidsolidservices.shared.vocab.DC.ELEMENT_NAMESPACE
import com.erfangholami.androidsolidservices.shared.vocab.DC.NAMESPACE
import com.erfangholami.androidsolidservices.shared.vocab.DC.TITLE


/**
 * Dublin Core Metadata Terms vocabulary constants.
 * http://purl.org/dc/terms/  (dcterms — preferred)
 * http://purl.org/dc/elements/1.1/  (dc — legacy elements namespace)
 *
 * Used for descriptive metadata on Solid resources: titles, dates, creators,
 * and relationships. Prefer dcterms (the [NAMESPACE] prefix) over the legacy
 * dc elements namespace ([ELEMENT_NAMESPACE]).
 */
public object DC {
    public const val NAMESPACE: String = "http://purl.org/dc/terms/"
    public const val ELEMENT_NAMESPACE: String = "http://purl.org/dc/elements/1.1/"

    public const val CREATED: String = "${NAMESPACE}created"
    public const val MODIFIED: String = "${NAMESPACE}modified"
    public const val ISSUED: String = "${NAMESPACE}issued"
    public const val DATE: String = "${NAMESPACE}date"

    public const val TITLE: String = "${NAMESPACE}title"

    /** https variant of [TITLE] — present on some older documents; prefer [TITLE]. */
    public const val TITLE_LEGACY: String = "https://purl.org/dc/terms/title"
    public const val DESCRIPTION: String = "${NAMESPACE}description"
    public const val SUBJECT: String = "${NAMESPACE}subject"
    public const val LANGUAGE: String = "${NAMESPACE}language"
    public const val RIGHTS: String = "${NAMESPACE}rights"
    public const val SOURCE: String = "${NAMESPACE}source"
    public const val FORMAT: String = "${NAMESPACE}format"

    public const val IDENTIFIER: String = "${NAMESPACE}identifier"
    public const val PUBLISHER: String = "${NAMESPACE}publisher"
    public const val CREATOR: String = "${NAMESPACE}creator"
    public const val CONTRIBUTOR: String = "${NAMESPACE}contributor"

    public const val RELATION: String = "${NAMESPACE}relation"
    public const val IS_PART_OF: String = "${NAMESPACE}isPartOf"
    public const val HAS_PART: String = "${NAMESPACE}hasPart"
    public const val REPLACES: String = "${NAMESPACE}replaces"
    public const val REQUIRES: String = "${NAMESPACE}requires"
}
