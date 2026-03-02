package com.wind.ggbond.classtime.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import com.wind.ggbond.classtime.data.local.entity.UpdateResult
import com.wind.ggbond.classtime.data.repository.AutoUpdateLogRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.service.CookieAutoUpdateService
import com.wind.ggbond.classtime.ui.screen.update.FloatingUpdateActivity
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * 定时自动更新 Worker
 * 
 * 使用 WorkManager 实现每天在指定时间执行自动更新
 * 特点：
 * - 每天在用户指定的时间执行（默认 07:00）
 * - 无需额外条件（如 Wi-Fi、充电等）
 * - 与间隔更新共享去重机制（5分钟内不重复更新）
 * - 自动记录更新日志
 */
@HiltWorker
class ScheduledUpdateWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val scheduleRepository: ScheduleRepository,
    private val cookieAutoUpdateService: CookieAutoUpdateService,
    private val logRepository: AutoUpdateLogRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.d("ScheduledUpdateWorker", "开始执行定时更新任务")
            
            // 读取设置
            val settingsDataStore = DataStoreManager.getSettingsDataStore(context)
            val preferences = settingsDataStore.data.first()
            
            // 检查自动更新总开关
            val autoUpdateEnabled = preferences[DataStoreManager.SettingsKeys.AUTO_UPDATE_ENABLED_KEY]
                ?: DataStoreManager.SettingsKeys.DEFAULT_AUTO_UPDATE_ENABLED
            
            if (!autoUpdateEnabled) {
                Log.d("ScheduledUpdateWorker", "自动更新未启用，跳过")
                return Result.success()
            }
            
            // 检查定时更新是否启用
            val scheduledUpdateEnabled = preferences[DataStoreManager.SettingsKeys.SCHEDULED_UPDATE_ENABLED_KEY]
                ?: DataStoreManager.SettingsKeys.DEFAULT_SCHEDULED_UPDATE_ENABLED
            
            if (!scheduledUpdateEnabled) {
                Log.d("ScheduledUpdateWorker", "定时更新未启用，跳过")
                return Result.success()
            }
            
            // 检查防重复机制：距离上次更新是否 < 5分钟
            val lastUpdateTime = preferences[DataStoreManager.SettingsKeys.LAST_AUTO_UPDATE_TIME_KEY] ?: 0L
            val now = System.currentTimeMillis()
            val dedupIntervalMs = DataStoreManager.SettingsKeys.UPDATE_DEDUP_INTERVAL_MS
            val timeSinceLastUpdate = now - lastUpdateTime
            
            if (timeSinceLastUpdate < dedupIntervalMs) {
                Log.d("ScheduledUpdateWorker", "防重复：距离上次更新不足5分钟，跳过")
                return Result.success()
            }
            
            // 检查前置条件：课表和学校配置
            val currentSchedule = scheduleRepository.getCurrentSchedule()
            if (currentSchedule == null) {
                Log.d("ScheduledUpdateWorker", "未导入课表，跳过更新")
                return Result.success()
            }
            
            val schoolId = currentSchedule.schoolName
            if (schoolId.isNullOrEmpty()) {
                Log.d("ScheduledUpdateWorker", "课表缺少学校配置，跳过更新")
                return Result.success()
            }
            
            Log.d("ScheduledUpdateWorker", "前置条件满足，执行更新 (课表: ${currentSchedule.name}, 学校: $schoolId)")
            
            // 更新最后更新时间
            settingsDataStore.updateData { prefs ->
                prefs.toMutablePreferences().apply {
                    this[DataStoreManager.SettingsKeys.LAST_AUTO_UPDATE_TIME_KEY] = now
                }
            }
            
            // 执行更新
            val (success, message) = cookieAutoUpdateService.performUpdate()
            
            // 记录日志
            logRepository.logUpdate(
                triggerEvent = "ScheduledUpdate",
                result = if (success) UpdateResult.SUCCESS else UpdateResult.FAILED,
                successMessage = if (success) message else null,
                failureReason = if (!success) message else null
            )
            
            Log.d("ScheduledUpdateWorker", "定时更新完成: ${if (success) "成功" else "失败"} - $message")
            
            return Result.success()
            
        } catch (e: Exception) {
            Log.e("ScheduledUpdateWorker", "定时更新异常", e)
            
            try {
                logRepository.logUpdate(
                    triggerEvent = "ScheduledUpdate",
                    result = UpdateResult.FAILED,
                    failureReason = "定时更新异常: ${e.message}"
                )
            } catch (logError: Exception) {
                Log.e("ScheduledUpdateWorker", "记录异常日志失败", logError)
            }
            
            // 重试一次
            return Result.retry()
        }
    }

    companion object {
        const val TAG = "ScheduledUpdateWorker"
    }
}
