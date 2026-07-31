package com.erfangholami.androidsolidservices.domain.usecase

import com.erfangholami.androidsolidservices.domain.repository.AuthRepository
import com.erfangholami.androidsolidservices.domain.repository.SystemAccountRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the system account list following the logged-in profiles, for as long as the process
 * lives.
 *
 * Mirroring used to run once, when the main screen's ViewModel was created — so a login that
 * completed afterwards (a custom pod provider's redirect, an add-account flow) produced a profile
 * with no system account until the next cold open. Collecting the profiles flow instead makes
 * the mirror independent of which screen is on top and of how the login arrived.
 *
 * Additions only: removals are event-driven on both sides already — an in-app logout removes the
 * account through [LogoutUseCase], and a Settings removal signs the profile out through the
 * authenticator's removal hook.
 */
@Singleton
class SystemAccountMirror @Inject constructor(
    private val authRepository: AuthRepository,
    private val systemAccountRepository: SystemAccountRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            authRepository.loggedInProfilesFlow.collect { profiles ->
                val existing = systemAccountRepository.getAccountWebIds()
                profiles
                    .mapNotNull { it.userInfo?.webId }
                    .filterNot { it in existing }
                    .forEach(systemAccountRepository::addAccount)
            }
        }
    }
}
