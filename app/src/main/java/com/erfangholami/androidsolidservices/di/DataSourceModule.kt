package com.erfangholami.androidsolidservices.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.erfangholami.androidsolidservices.data.local.AccessGrantLocalDataSource
import com.erfangholami.androidsolidservices.data.local.AccessGrantLocalDataSourceImplementation
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val PREFERENCES_NAME = "com.erfangholami.androidsolidservices.preferences"

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    PREFERENCES_NAME
)

@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourceModule {

    @Binds
    abstract fun bindAccessGrantLocalDataSource(
        implementation: AccessGrantLocalDataSourceImplementation,
    ): AccessGrantLocalDataSource

    companion object {

        @Provides
        @Singleton
        fun providePreferencesDatasource(
            @ApplicationContext context: Context
        ): DataStore<Preferences> = context.preferencesDataStore
    }
}
