package com.wind.ggbond.classtime.di

import com.wind.ggbond.classtime.service.UnifiedReminderScheduler
import com.wind.ggbond.classtime.service.ExportService
import com.wind.ggbond.classtime.service.UnifiedScheduleFetchService
import com.wind.ggbond.classtime.service.UpdateOrchestrator
import com.wind.ggbond.classtime.service.contract.IAlarmScheduler
import com.wind.ggbond.classtime.service.contract.IDataExporter
import com.wind.ggbond.classtime.service.contract.IScheduleFetcher
import com.wind.ggbond.classtime.service.contract.IUpdateManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {

    @Binds
    @Singleton
    abstract fun bindAlarmScheduler(
        impl: UnifiedReminderScheduler
    ): IAlarmScheduler

    @Binds
    @Singleton
    abstract fun bindScheduleFetcher(
        impl: UnifiedScheduleFetchService
    ): IScheduleFetcher

    @Binds
    @Singleton
    abstract fun bindUpdateManager(
        impl: UpdateOrchestrator
    ): IUpdateManager

    @Binds
    @Singleton
    abstract fun bindDataExporter(
        impl: ExportService
    ): IDataExporter
}
