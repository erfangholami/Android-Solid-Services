package com.erfangholami.androidsolidservices.shared.vocab

import com.erfangholami.androidsolidservices.shared.vocab.OWL.SAME_AS


/**
 * Web Ontology Language (OWL) vocabulary constants.
 * http://www.w3.org/2002/07/owl#
 *
 * Used in Solid profiles and data to express equivalence between IRIs
 * (e.g. linking a WebID to another identity with [SAME_AS]).
 */
public object OWL {
    public const val NAMESPACE: String = "http://www.w3.org/2002/07/owl#"

    public const val SAME_AS: String = "${NAMESPACE}sameAs"
}
