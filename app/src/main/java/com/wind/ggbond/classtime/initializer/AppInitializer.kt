package com.wind.ggbond.classtime.initializer

import android.content.Context
import android.util.Log
import com.wind.ggbond.classtime.data.repository.InitializationRepository
import com.wind.ggbond.classtime.service.contract.IAlarmScheduler
import com.wind.ggbond.classtime.service.KeepAliveService
import com.wind.ggbond.classtime.util.ScheduledUpdateManager
import com.wind.ggbond.classtime.widget.WidgetRefreshHelper
import com.wind.ggbond.classtime.worker.ReminderCheckWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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

    suspend fun initialize(): InitializationResult {
        return try {
            Log.d(TAG, "开始应用初始化...")

            val result = kotlinx.coroutines.withTimeoutOrNull(INITIALIZATION_TIMEOUT_MS) {
                step1InitializeDefaultData()
                step2PreloadCoreData()
                step3InitializeScheduledUpdate()
                step4EnqueueReminderCheckWorker()
                step5StartKeepAliveService()
                step6RescheduleReminders()

                InitializationResult.Success
            } ?: InitializationResult.Timeout

            Log.d(TAG, "应用初始化完成: $result")
            result

        } catch (e: Exception) {
            Log.e(TAG, "应用初始化失败", e)
            InitializationResult.Error(e.message ?: "未知错误", e)
        }
    }

    private suspend fun step1InitializeDefaultData() {
        initializationRepository.initializeDefaultData()
        Log.d(TAG, "✅ 默认数据初始化完成")
    }

    private suspend fun step2PreloadCoreData() {
        initializationRepository.preloadCoreData()
        Log.d(TAG, "✅ 核心数据预加载完成")
    }

    private suspend fun step3InitializeScheduledUpdate() {
        val isEnabled = scheduledUpdateManager.isScheduledUpdateEnabled()
        if (isEnabled) {
            Log.d(TAG, "✅ 定时更新已启用，初始化 WorkManager 任务")
            scheduledUpdateManager.enableScheduledUpdate()
        } else {
            Log.d(TAG, "定时更新未启用")
        }
    }

    private fun step4EnqueueReminderCheckWorker() {
        ReminderCheckWorker.enqueue(context)
        Log.d(TAG, "✅ WorkManager 兜底检查任务已注册")
    }

    private fun step5StartKeepAliveService() {
        KeepAliveService.start(context)
        Log.d(TAG, "✅ 前台保活服务已启动")
    }

    private suspend fun step6RescheduleReminders() {
        alarmReminderScheduler.rescheduleAllReminders()
        Log.d(TAG, "✅ 课程提醒重新调度完成")
    }

    fun refreshWidgets() {
        WidgetRefreshHelper.refreshAllWidgets(context)
        Log.d(TAG, "✅ 小组件数据已刷新")
    }
}

sealed class InitializationResult {
    object Success : InitializationResult()
    object Timeout : InitializationResult()
    data class Error(val message: String, val throwable: Throwable) : InitializationResult()
}
