package com.erfangholami.androidsolidservices.api.auth.implementation

import com.erfangholami.androidsolidservices.api.auth.Profile

internal interface ProfileStore {

    fun getProfileOrNull(webId: String): Profile?

    suspend fun writeProfile(webId: String, profile: Profile)
}
