package com.erfangholami.androidsolidservices.di

import com.erfangholami.androidsolidservices.data.remote.AuthRepositoryImplementation
import com.erfangholami.androidsolidservices.data.remote.ContactsRepositoryImplementation
import com.erfangholami.androidsolidservices.data.remote.NotificationsRepositoryImplementation
import com.erfangholami.androidsolidservices.data.remote.SharingRepositoryImplementation
import com.erfangholami.androidsolidservices.data.remote.SolidResourceRepositoryImplementation
import com.erfangholami.androidsolidservices.data.remote.SystemAccountRepositoryImplementation
import com.erfangholami.androidsolidservices.data.repository.AccessGrantRepositoryImplementation
import com.erfangholami.androidsolidservices.data.repository.ResourceAccessRepositoryImplementation
import com.erfangholami.androidsolidservices.domain.repository.AccessGrantRepository
import com.erfangholami.androidsolidservices.domain.repository.AuthRepository
import com.erfangholami.androidsolidservices.domain.repository.ContactsRepository
import com.erfangholami.androidsolidservices.domain.repository.NotificationsRepository
import com.erfangholami.androidsolidservices.domain.repository.ResourceAccessRepository
import com.erfangholami.androidsolidservices.domain.repository.SharingRepository
import com.erfangholami.androidsolidservices.domain.repository.SolidResourceRepository
import com.erfangholami.androidsolidservices.domain.repository.SystemAccountRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindAuthRepository(
        implementation: AuthRepositoryImplementation,
    ): AuthRepository

    @Binds
    abstract fun bindSystemAccountRepository(
        implementation: SystemAccountRepositoryImplementation,
    ): SystemAccountRepository

    @Binds
    abstract fun bindAccessGrantRepository(
        implementation: AccessGrantRepositoryImplementation,
    ): AccessGrantRepository

    @Binds
    abstract fun bindResourceAccessRepository(
        implementation: ResourceAccessRepositoryImplementation,
    ): ResourceAccessRepository

    @Binds
    abstract fun bindSolidResourceRepository(
        implementation: SolidResourceRepositoryImplementation,
    ): SolidResourceRepository

    @Binds
    abstract fun bindSharingRepository(
        implementation: SharingRepositoryImplementation,
    ): SharingRepository

    @Binds
    abstract fun bindNotificationsRepository(
        implementation: NotificationsRepositoryImplementation,
    ): NotificationsRepository

    @Binds
    abstract fun bindContactsRepository(
        implementation: ContactsRepositoryImplementation,
    ): ContactsRepository
}
