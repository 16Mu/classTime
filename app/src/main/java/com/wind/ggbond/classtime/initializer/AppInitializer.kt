package com.wind.ggbond.classtime.initializer

import android.content.Context
import com.wind.ggbond.classtime.data.repository.InitializationRepository
import com.wind.ggbond.classtime.service.contract.IAlarmScheduler
import com.wind.ggbond.classtime.service.KeepAliveService
import com.wind.ggbond.classtime.util.ScheduledUpdateManager
import com.wind.ggbond.classtime.util.StartupTracker
import com.wind.ggbond.classtime.widget.WidgetRefreshHelper
import com.wind.ggbond.classtime.worker.ReminderCheckWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Singleton
class AppInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val initializationRepository: InitializationRepository,
    private val scheduledUpdateManager: ScheduledUpdateManager,
    private val alarmReminderScheduler: IAlarmScheduler
) {

    companion object {
        private const val TAG = "AppInitializer"
        private const val INITIALIZATION_TIMEOUT_MS = 10_000L
    }

    suspend fun initializeCritical(): InitializationResult {
        return try {
            StartupTracker.markCriticalInitStart()
            AppLogger.d(TAG, "开始关键初始化...")

            val result = kotlinx.coroutines.withTimeoutOrNull(INITIALIZATION_TIMEOUT_MS) {
                step1InitializeDefaultData()
                step2PreloadCoreData()
                InitializationResult.Success
            } ?: InitializationResult.Timeout

            StartupTracker.markCriticalInitEnd()
            AppLogger.d(TAG, "关键初始化完成: $result")
            result

        } catch (e: Exception) {
            StartupTracker.markCriticalInitEnd()
            AppLogger.e(TAG, "关键初始化失败", e)
            InitializationResult.Error(e.message ?: "未知错误", e)
        }
    }

    suspend fun initializeDeferred() {
        StartupTracker.markDeferredInitStart()
        AppLogger.d(TAG, "开始延迟初始化...")
        try {
            coroutineScope {
                launch { step3InitializeScheduledUpdate() }
                launch { step4EnqueueReminderCheckWorker() }
                launch { step5StartKeepAliveService() }
                launch { step6RescheduleReminders() }
            }
            StartupTracker.markDeferredInitEnd()
            AppLogger.d(TAG, "延迟初始化完成")
        } catch (e: Exception) {
            StartupTracker.markDeferredInitEnd()
            AppLogger.e(TAG, "延迟初始化失败", e)
        }
    }

    private suspend fun step1InitializeDefaultData() {
        StartupTracker.markStep("step1_start")
        initializationRepository.initializeDefaultData()
        AppLogger.d(TAG, "✅ 默认数据初始化完成")
    }

    private suspend fun step2PreloadCoreData() {
        StartupTracker.markStep("step2_start")
        initializationRepository.preloadCoreData()
        AppLogger.d(TAG, "✅ 核心数据预加载完成")
    }

    private suspend fun step3InitializeScheduledUpdate() {
        StartupTracker.markStep("step3_start")
        val isEnabled = scheduledUpdateManager.isScheduledUpdateEnabled()
        if (isEnabled) {
            AppLogger.d(TAG, "✅ 定时更新已启用，初始化 WorkManager 任务")
            scheduledUpdateManager.enableScheduledUpdate()
        } else {
            AppLogger.d(TAG, "定时更新未启用")
        }
    }

    private fun step4EnqueueReminderCheckWorker() {
        StartupTracker.markStep("step4_start")
        ReminderCheckWorker.enqueue(context)
        AppLogger.d(TAG, "✅ WorkManager 兜底检查任务已注册")
    }

    private fun step5StartKeepAliveService() {
        StartupTracker.markStep("step5_start")
        KeepAliveService.start(context)
        AppLogger.d(TAG, "✅ 前台保活服务已启动")
    }

    private suspend fun step6RescheduleReminders() {
        StartupTracker.markStep("step6_start")
        alarmReminderScheduler.rescheduleAllReminders()
        AppLogger.d(TAG, "✅ 课程提醒重新调度完成")
    }

    fun refreshWidgets() {
        WidgetRefreshHelper.refreshAllWidgets(context)
        AppLogger.d(TAG, "✅ 小组件数据已刷新")
    }
}

sealed class InitializationResult {
    object Success : InitializationResult()
    object Timeout : InitializationResult()
    data class Error(val message: String, val throwable: Throwable) : InitializationResult()
}
