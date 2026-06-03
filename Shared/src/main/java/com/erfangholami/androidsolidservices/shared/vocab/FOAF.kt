package com.erfangholami.androidsolidservices.shared.vocab

import com.erfangholami.androidsolidservices.shared.vocab.FOAF.AGENT


/**
 * Friend of a Friend (FOAF) vocabulary constants.
 * http://xmlns.com/foaf/0.1/
 *
 * Used in Solid WebID profiles to describe people, organizations, and social
 * relationships. [AGENT] is also the value of [ACL.AGENT_PUBLIC] for public
 * access rules in WAC.
 */
public object FOAF {
    public const val NAMESPACE: String = "http://xmlns.com/foaf/0.1/"

    //Types
    public const val AGENT: String = "${NAMESPACE}Agent"
    public const val PERSON: String = "${NAMESPACE}Person"
    public const val ORGANIZATION: String = "${NAMESPACE}Organization"
    public const val DOCUMENT: String = "${NAMESPACE}Document"
    public const val PERSONAL_PROFILE_DOCUMENT: String = "${NAMESPACE}PersonalProfileDocument"
    public const val ONLINE_ACCOUNT: String = "${NAMESPACE}OnlineAccount"

    //Identity predicates
    public const val NAME: String = "${NAMESPACE}name"
    public const val GIVEN_NAME: String = "${NAMESPACE}givenName"
    public const val FAMILY_NAME: String = "${NAMESPACE}familyName"
    public const val TITLE: String = "${NAMESPACE}title"
    public const val NICK: String = "${NAMESPACE}nick"
    public const val MBOX: String = "${NAMESPACE}mbox"
    public const val MBOX_SHA1_SUM: String = "${NAMESPACE}mbox_sha1sum"

    //Profile / social
    public const val IMG: String = "${NAMESPACE}img"
    public const val DEPICTION: String = "${NAMESPACE}depiction"
    public const val HOMEPAGE: String = "${NAMESPACE}homepage"
    public const val WEBLOG: String = "${NAMESPACE}weblog"
    public const val ACCOUNT: String = "${NAMESPACE}account"
    public const val KNOWS: String = "${NAMESPACE}knows"
    public const val MEMBER: String = "${NAMESPACE}member"

    //Document predicates
    public const val PRIMARY_TOPIC: String = "${NAMESPACE}primaryTopic"
    public const val IS_PRIMARY_TOPIC_OF: String = "${NAMESPACE}isPrimaryTopicOf"
    public const val MAKER: String = "${NAMESPACE}maker"
    public const val MADE: String = "${NAMESPACE}made"
    public const val TOPIC: String = "${NAMESPACE}topic"
    public const val PAGE: String = "${NAMESPACE}page"

    //Account predicates
    public const val ACCOUNT_NAME: String = "${NAMESPACE}accountName"
    public const val ACCOUNT_SERVICE_HOMEPAGE: String = "${NAMESPACE}accountServiceHomepage"
    public const val OPENID: String = "${NAMESPACE}openid"
}
