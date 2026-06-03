package com.erfangholami.androidsolidservices.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class GrantedApp(
    val packageName: String,
    val name: String,
    val webId: String,
)
