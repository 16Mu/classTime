package com.wind.ggbond.classtime.di

import android.content.Context
import com.wind.ggbond.classtime.util.SecureCookieManager
import com.wind.ggbond.classtime.util.SecureCredentialsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * 安全相关的依赖注入模块
 * 
 * ✅ 优化：为SecureCookieManager注入OkHttpClient以支持Cookie健康检查
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    
    @Provides
    @Singleton
    fun provideSecureCookieManager(
        @ApplicationContext context: Context,
        @ApiClient okHttpClient: OkHttpClient  // ✅ 注入ApiClient用于Cookie验证
    ): SecureCookieManager {
        return SecureCookieManager(context, okHttpClient)
    }
    
    @Provides
    @Singleton
    fun provideSecureCredentialsManager(
        @ApplicationContext context: Context
    ): SecureCredentialsManager {
        return SecureCredentialsManager(context)
    }
}








