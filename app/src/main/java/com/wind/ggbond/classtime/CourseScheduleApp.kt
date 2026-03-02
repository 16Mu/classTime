package com.wind.ggbond.classtime

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.wind.ggbond.classtime.data.local.database.CourseDatabase
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * 应用程序类
 * 
 * 职责：
 * 1. 提供 WorkManager 配置（支持 Hilt Worker 注入）
 * 2. 提供全局学校ID存取
 * 3. 提供数据库访问（供 BroadcastReceiver 使用）
 * 
 * ⚠️ 数据初始化和自动更新统一由 MainActivity 负责，避免竞态条件
 */
@HiltAndroidApp
class CourseScheduleApp : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    @Inject
    lateinit var database: CourseDatabase
    
    companion object {
        private const val TAG = "CourseScheduleApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        // WorkManager 通过 Configuration.Provider 自动完成配置，无需额外初始化
        Log.d(TAG, "Application 已启动，WorkManager 配置已就绪")
    }
    
    /**
     * 提供 WorkManager 配置，支持 Hilt Worker 注入
     * 实现 Configuration.Provider 接口，让 WorkManager 使用自定义配置
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
            .build()
    
    /**
     * 保存当前学校ID（供导入时调用）
     */
    fun saveCurrentSchoolId(schoolId: String) {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().putString("current_school_id", schoolId).apply()
        Log.d(TAG, "已保存当前学校ID: $schoolId")
    }
}



