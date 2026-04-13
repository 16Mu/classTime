package com.wind.ggbond.classtime

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.util.StartupTracker
import javax.inject.Inject

@HiltAndroidApp
class CourseScheduleApp : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    companion object {
        private const val TAG = "CourseScheduleApp"
    }
    
    override fun onCreate() {
        StartupTracker.markAppCreateStart()
        super.onCreate()
        StartupTracker.markAppCreateEnd()
        AppLogger.d(TAG, "Application 已启动，WorkManager 配置已就绪")
    }
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO)
            .build()
    
    fun saveCurrentSchoolId(schoolId: String) {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().putString("current_school_id", schoolId).apply()
        AppLogger.d(TAG, "已保存当前学校ID: $schoolId")
    }
}
