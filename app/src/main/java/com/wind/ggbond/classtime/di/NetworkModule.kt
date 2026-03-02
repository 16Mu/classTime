package com.wind.ggbond.classtime.di

import com.wind.ggbond.classtime.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * 网络客户端类型限定符
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ScheduleClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiClient

/**
 * 网络依赖注入模块
 * 
 * ✅ 优化：区分不同场景的超时配置
 * - ScheduleClient: 用于课表抓取，需要更长的超时时间
 * - ApiClient: 用于普通API请求，使用较短的超时时间
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    /**
     * ✅ 课表抓取专用客户端（需要登录、加载、执行JS）
     */
    @Provides
    @Singleton
    @ScheduleClient
    fun provideScheduleOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }
        }
        
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)  // 课表抓取需要更长时间
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .retryOnConnectionFailure(true)  // 自动重试连接失败
            .build()
    }
    
    /**
     * ✅ 普通API请求客户端（快速响应）
     */
    @Provides
    @Singleton
    @ApiClient
    fun provideApiOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .retryOnConnectionFailure(true)
            .build()
    }
    
    /**
     * ✅ 默认客户端（向后兼容）
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(@ApiClient apiClient: OkHttpClient): OkHttpClient {
        return apiClient
    }
}



