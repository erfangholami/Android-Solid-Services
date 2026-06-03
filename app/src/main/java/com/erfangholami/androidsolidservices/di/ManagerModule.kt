package com.erfangholami.androidsolidservices.di

import android.accounts.AccountManager
import android.content.Context
import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.datamodule.contacts.SolidContactsDataModule
import com.erfangholami.androidsolidservices.api.notifications.NotificationsManager
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.api.sharing.SharingManager
import com.erfangholami.androidsolidservices.base.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ManagerModule {

    @Provides
    @Singleton
    @Named(Constants.ASS_ACCOUNT_NAME)
    fun provideASSAccountName(): String =
        "com.erfangholami.androidsolidservices.account.DEMOACCOUNT"

    @Provides
    @Singleton
    fun provideAccountManager(
        @ApplicationContext context: Context
    ): AccountManager = AccountManager.get(context)

    @Provides
    @Singleton
    fun provideAuthenticator(
        @ApplicationContext context: Context,
    ): Authenticator = Authenticator.getInstance(context)

    @Provides
    @Singleton
    fun provideSolidResourceManager(
        authenticator: Authenticator,
    ): SolidResourceManager = SolidResourceManager.getInstance(authenticator)

    @Provides
    @Singleton
    fun provideSolidContactsDataModule(
        authenticator: Authenticator,
    ): SolidContactsDataModule = SolidContactsDataModule.getInstance(authenticator)

    @Provides
    @Singleton
    fun provideSharingManager(
        resourceManager: SolidResourceManager,
    ): SharingManager = SharingManager.getInstance(resourceManager)

    @Provides
    @Singleton
    fun provideNotificationsManager(
        resourceManager: SolidResourceManager,
    ): NotificationsManager = NotificationsManager.getInstance(resourceManager)
}
