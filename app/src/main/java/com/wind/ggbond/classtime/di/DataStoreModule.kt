package com.wind.ggbond.classtime.di

import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DataStore 模块 - 提供 DataStoreManager 依赖注入
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideDataStoreManager(): DataStoreManager {
        return DataStoreManager
    }
}
