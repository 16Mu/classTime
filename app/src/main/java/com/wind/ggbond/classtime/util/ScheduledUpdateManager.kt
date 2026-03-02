package com.wind.ggbond.classtime.util

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import com.wind.ggbond.classtime.worker.ScheduledUpdateWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 定时更新管理器
 * 
 * 负责：
 * - 启用/禁用定时更新任务
 * - 更新定时更新时间
 * - 管理 WorkManager 的周期性任务
 */
@Singleton
class ScheduledUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * 启用定时更新
     * 
     * 创建一个每天执行的周期性任务
     * 任务会在用户指定的时间执行（通过 DataStore 读取）
     */
    suspend fun enableScheduledUpdate() {
        try {
            Log.d("ScheduledUpdateManager", "启用定时更新")
            
            // 创建周期性工作请求
            // 最小周期为 15 分钟（WorkManager 限制）
            val updateRequest = PeriodicWorkRequestBuilder<ScheduledUpdateWorker>(
                15, TimeUnit.MINUTES
            ).addTag(ScheduledUpdateWorker.TAG).build()
            
            // 使用 KEEP 策略：如果任务已存在，保持现有任务
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "scheduled_update",
                ExistingPeriodicWorkPolicy.KEEP,
                updateRequest
            )
            
            Log.d("ScheduledUpdateManager", "✅ 定时更新任务已启用")
            
        } catch (e: Exception) {
            Log.e("ScheduledUpdateManager", "启用定时更新失败", e)
        }
    }

    /**
     * 禁用定时更新
     * 
     * 取消所有定时更新任务
     */
    suspend fun disableScheduledUpdate() {
        try {
            Log.d("ScheduledUpdateManager", "禁用定时更新")
            
            WorkManager.getInstance(context).cancelUniqueWork("scheduled_update")
            
            Log.d("ScheduledUpdateManager", "✅ 定时更新任务已禁用")
            
        } catch (e: Exception) {
            Log.e("ScheduledUpdateManager", "禁用定时更新失败", e)
        }
    }

    /**
     * 更新定时更新时间
     * 
     * 当用户修改定时更新时间时调用
     * 时间格式：HH:mm（例如 "07:00"）
     */
    suspend fun updateScheduledTime(time: String) {
        try {
            Log.d("ScheduledUpdateManager", "更新定时更新时间: $time")
            
            val settingsDataStore = DataStoreManager.getSettingsDataStore(context)
            settingsDataStore.updateData { prefs ->
                prefs.toMutablePreferences().apply {
                    this[DataStoreManager.SettingsKeys.SCHEDULED_UPDATE_TIME_KEY] = time
                }
            }
            
            // 重新启用任务以应用新的时间设置
            // 注：实际的时间检查在 ScheduledUpdateWorker 中进行
            disableScheduledUpdate()
            enableScheduledUpdate()
            
            Log.d("ScheduledUpdateManager", "✅ 定时更新时间已更新: $time")
            
        } catch (e: Exception) {
            Log.e("ScheduledUpdateManager", "更新定时更新时间失败", e)
        }
    }

    /**
     * 获取当前定时更新时间
     */
    suspend fun getScheduledTime(): String {
        return try {
            val settingsDataStore = DataStoreManager.getSettingsDataStore(context)
            val preferences = settingsDataStore.data.first()
            
            preferences[DataStoreManager.SettingsKeys.SCHEDULED_UPDATE_TIME_KEY]
                ?: DataStoreManager.SettingsKeys.DEFAULT_SCHEDULED_UPDATE_TIME
        } catch (e: Exception) {
            Log.e("ScheduledUpdateManager", "获取定时更新时间失败", e)
            DataStoreManager.SettingsKeys.DEFAULT_SCHEDULED_UPDATE_TIME
        }
    }

    /**
     * 检查定时更新是否已启用
     */
    suspend fun isScheduledUpdateEnabled(): Boolean {
        return try {
            val settingsDataStore = DataStoreManager.getSettingsDataStore(context)
            val preferences = settingsDataStore.data.first()
            
            preferences[DataStoreManager.SettingsKeys.SCHEDULED_UPDATE_ENABLED_KEY]
                ?: DataStoreManager.SettingsKeys.DEFAULT_SCHEDULED_UPDATE_ENABLED
        } catch (e: Exception) {
            Log.e("ScheduledUpdateManager", "检查定时更新状态失败", e)
            false
        }
    }

    /**
     * 设置定时更新启用状态
     */
    suspend fun setScheduledUpdateEnabled(enabled: Boolean) {
        try {
            Log.d("ScheduledUpdateManager", "设置定时更新状态: $enabled")
            
            val settingsDataStore = DataStoreManager.getSettingsDataStore(context)
            settingsDataStore.updateData { prefs ->
                prefs.toMutablePreferences().apply {
                    this[DataStoreManager.SettingsKeys.SCHEDULED_UPDATE_ENABLED_KEY] = enabled
                }
            }
            
            if (enabled) {
                enableScheduledUpdate()
            } else {
                disableScheduledUpdate()
            }
            
            Log.d("ScheduledUpdateManager", "✅ 定时更新状态已更新: $enabled")
            
        } catch (e: Exception) {
            Log.e("ScheduledUpdateManager", "设置定时更新状态失败", e)
        }
    }
}
