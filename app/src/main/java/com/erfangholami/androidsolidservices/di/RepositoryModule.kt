package com.erfangholami.androidsolidservices.di

import com.erfangholami.androidsolidservices.data.remote.AuthRepositoryImplementation
import com.erfangholami.androidsolidservices.data.remote.SystemAccountRepositoryImplementation
import com.erfangholami.androidsolidservices.data.repository.AccessGrantRepositoryImplementation
import com.erfangholami.androidsolidservices.data.repository.ResourceAccessRepositoryImplementation
import com.erfangholami.androidsolidservices.domain.repository.AccessGrantRepository
import com.erfangholami.androidsolidservices.domain.repository.AuthRepository
import com.erfangholami.androidsolidservices.domain.repository.ResourceAccessRepository
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
}
