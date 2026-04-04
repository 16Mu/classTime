package com.wind.ggbond.classtime.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.wind.ggbond.classtime.data.repository.SettingsRepository
import com.wind.ggbond.classtime.data.repository.SettingsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 应用模块
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    companion object {

        @Provides
        @Singleton
        fun provideGson(): Gson {
            return GsonBuilder()
                .setLenient()
                .create()
        }
    }
}



