package com.erfangholami.androidsolidservices.domain.repository

interface SystemAccountRepository {

    fun getAccountWebIds(): Set<String>

    fun addAccount(webId: String)

    fun removeAccount(webId: String)
}
