package com.erfangholami.androidsolidservices.domain.repository

interface ResourceAccessRepository {

    fun hasAccess(webId: String, callerPackageName: String): Boolean
}
