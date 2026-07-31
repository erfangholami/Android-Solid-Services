package com.erfangholami.androidsolidservices.data.remote

import android.accounts.Account
import android.accounts.AccountManager
import com.erfangholami.androidsolidservices.base.Constants
import com.erfangholami.androidsolidservices.domain.repository.SystemAccountRepository
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class SystemAccountRepositoryImplementation @Inject constructor(
    private val accountManager: AccountManager,
    @param:Named(Constants.ASS_ACCOUNT_NAME) private val accountType: String,
) : SystemAccountRepository {

    // Asking for one type rather than filtering the device-wide list: these are accounts this app
    // authenticates, so they come back without GET_ACCOUNTS — a Contacts-group permission.
    override fun getAccountWebIds(): Set<String> =
        accountManager.getAccountsByType(accountType)
            .map { it.name }
            .toSet()

    override fun addAccount(webId: String) {
        accountManager.addAccountExplicitly(Account(webId, accountType), null, null)
    }

    override fun removeAccount(webId: String) {
        accountManager.removeAccountExplicitly(Account(webId, accountType))
    }
}
