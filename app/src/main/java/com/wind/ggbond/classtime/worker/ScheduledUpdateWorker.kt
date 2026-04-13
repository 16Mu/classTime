package com.wind.ggbond.classtime.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import com.wind.ggbond.classtime.data.local.entity.UpdateResult
import com.wind.ggbond.classtime.data.repository.AutoUpdateLogRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.service.UnifiedScheduleUpdateService
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.wind.ggbond.classtime.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalTime

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
    private val unifiedScheduleUpdateService: UnifiedScheduleUpdateService,
    private val logRepository: AutoUpdateLogRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            AppLogger.d(TAG, "开始执行定时更新任务")

            val settingsDataStore = DataStoreManager.getSettingsDataStore(context)
            val preferences = settingsDataStore.data.first()

            val autoUpdateEnabled = preferences[DataStoreManager.SettingsKeys.AUTO_UPDATE_ENABLED_KEY]
                ?: DataStoreManager.SettingsKeys.DEFAULT_AUTO_UPDATE_ENABLED

            if (!autoUpdateEnabled) {
                AppLogger.d(TAG, "自动更新未启用，跳过")
                return Result.success()
            }

            val scheduledUpdateEnabled = preferences[DataStoreManager.SettingsKeys.SCHEDULED_UPDATE_ENABLED_KEY]
                ?: DataStoreManager.SettingsKeys.DEFAULT_SCHEDULED_UPDATE_ENABLED

            if (!scheduledUpdateEnabled) {
                AppLogger.d(TAG, "定时更新未启用，跳过")
                return Result.success()
            }

            val scheduledTimeStr = preferences[DataStoreManager.SettingsKeys.SCHEDULED_UPDATE_TIME_KEY]
                ?: DataStoreManager.SettingsKeys.DEFAULT_SCHEDULED_UPDATE_TIME
            val scheduledTime = try {
                LocalTime.parse(scheduledTimeStr)
            } catch (_: Exception) {
                LocalTime.of(7, 0)
            }
            val now = LocalTime.now()
            val timeDiff = Duration.between(scheduledTime, now).abs().toMinutes()
            if (timeDiff > 15) {
                AppLogger.d(TAG, "当前时间 $now 不在定时窗口 $scheduledTime 内，跳过")
                return Result.success()
            }

            val lastUpdateTime = preferences[DataStoreManager.SettingsKeys.LAST_AUTO_UPDATE_TIME_KEY] ?: 0L
            val nowMs = System.currentTimeMillis()
            val dedupIntervalMs = DataStoreManager.SettingsKeys.UPDATE_DEDUP_INTERVAL_MS
            val timeSinceLastUpdate = nowMs - lastUpdateTime

            if (timeSinceLastUpdate < dedupIntervalMs) {
                AppLogger.d(TAG, "防重复：距离上次更新不足5分钟，跳过")
                return Result.success()
            }

            val currentSchedule = scheduleRepository.getCurrentSchedule()
            if (currentSchedule == null) {
                AppLogger.d(TAG, "未导入课表，跳过更新")
                return Result.success()
            }

            val schoolName = currentSchedule.schoolName
            if (schoolName.isNullOrEmpty()) {
                AppLogger.d(TAG, "课表缺少学校配置，跳过更新")
                return Result.success()
            }

            AppLogger.d(TAG, "前置条件满足，执行更新 (课表: ${currentSchedule.name}, 学校: $schoolName)")

            settingsDataStore.updateData { prefs ->
                prefs.toMutablePreferences().apply {
                    this[DataStoreManager.SettingsKeys.LAST_AUTO_UPDATE_TIME_KEY] = nowMs
                }
            }

            val (success, message) = unifiedScheduleUpdateService.performSimpleUpdate()

            logRepository.logUpdate(
                triggerEvent = "ScheduledUpdate",
                result = if (success) UpdateResult.SUCCESS else UpdateResult.FAILED,
                successMessage = if (success) message else null,
                failureReason = if (!success) message else null
            )

            AppLogger.d(TAG, "定时更新完成: ${if (success) "成功" else "失败"} - $message")

            return Result.success()

        } catch (e: CancellationException) {
            AppLogger.d(TAG, "Worker 被取消")
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "定时更新异常", e)

            try {
                logRepository.logUpdate(
                    triggerEvent = "ScheduledUpdate",
                    result = UpdateResult.FAILED,
                    failureReason = "定时更新异常: ${e.message}"
                )
            } catch (logError: Exception) {
                AppLogger.e(TAG, "记录异常日志失败", logError)
            }

            return when (e) {
                is java.io.IOException -> Result.retry()
                else -> Result.failure()
            }
        }
    }

    companion object {
        const val TAG = "ScheduledUpdateWorker"
    }
}
