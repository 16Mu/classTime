package com.wind.ggbond.classtime.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.wind.ggbond.classtime.service.contract.IWidgetRefresher
import com.wind.ggbond.classtime.util.AppLogger
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object WidgetRefreshHelper : IWidgetRefresher {

    private const val TAG = "WidgetRefreshHelper"

    /** 周期刷新任务的唯一标识 */
    private const val PERIODIC_REFRESH_WORK_NAME = "widget_periodic_refresh"

    /**
     * 刷新所有类型的 Widget 实例
     * 
     * 使用 Glance API 的 updateAll 方法，
     * 依次刷新今日课程 Widget 和下节课倒计时 Widget。
     * 同时刷新基于 RemoteViews 的 4x4 大尺寸小组件。
     * 
     * @param context 应用上下文
     */
    override fun refreshAllWidgets(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 刷新今日课程 Widget (Glance)
                TodayCourseWidget().updateAll(context)
                // 刷新下节课倒计时 Widget (Glance)
                NextClassWidget().updateAll(context)
                // 刷新 4x4 大尺寸小组件 (RemoteViews)
                LargeTodayCourseWidgetProvider.refreshAllWidgets(context)
                AppLogger.d(TAG, "所有 Widget 已刷新")
            } catch (e: Exception) {
                AppLogger.e(TAG, "刷新 Widget 失败", e)
            }
        }
    }

    /**
     * 检查是否存在已添加的任意类型 Widget
     * 
     * @param context 应用上下文
     * @return 是否有至少一个 Widget 在桌面上
     */
    override fun hasActiveWidgets(context: Context): Boolean {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val todayCourseIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, TodayCourseWidgetReceiver::class.java)
        )
        val nextClassIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, NextClassWidgetReceiver::class.java)
        )
        val largeWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, LargeTodayCourseWidgetProvider::class.java)
        )
        val tomorrowCourseIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, TomorrowCourseWidgetReceiver::class.java)
        )
        return todayCourseIds.isNotEmpty() || nextClassIds.isNotEmpty() || largeWidgetIds.isNotEmpty() || tomorrowCourseIds.isNotEmpty()
    }

    /**
     * 启动周期性刷新任务
     * 
     * 使用 WorkManager 每 15 分钟（系统最低周期）刷新一次 Widget，
     * 确保倒计时数据保持更新。
     * 仅在有活跃的 Widget 时启动，避免浪费电量。
     * 
     * @param context 应用上下文
     */
    override fun startPeriodicRefresh(context: Context) {
        if (!hasActiveWidgets(context)) {
            AppLogger.d(TAG, "无活跃 Widget，跳过周期刷新任务启动")
            return
        }

        val refreshRequest = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
            15, TimeUnit.MINUTES  // WorkManager 最低周期 15 分钟
        ).addTag(PERIODIC_REFRESH_WORK_NAME).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_REFRESH_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,  // 已存在则保留，避免重复创建
            refreshRequest
        )

        AppLogger.d(TAG, "周期刷新任务已启动（每 15 分钟）")
    }

    /**
     * 停止周期性刷新任务
     * 
     * 当所有 Widget 被移除时调用，节省电量。
     * 
     * @param context 应用上下文
     */
    override fun stopPeriodicRefresh(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_REFRESH_WORK_NAME)
        AppLogger.d(TAG, "周期刷新任务已停止")
    }
}

/**
 * Widget 周期刷新 Worker
 * 
 * 由 WorkManager 调度，每 15 分钟执行一次，
 * 刷新所有活跃的 Widget 数据。
 */
class WidgetRefreshWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            if (!WidgetRefreshHelper.hasActiveWidgets(applicationContext)) {
                WidgetRefreshHelper.stopPeriodicRefresh(applicationContext)
                return Result.success()
            }

            withContext(Dispatchers.IO) {
                TodayCourseWidget().updateAll(applicationContext)
                NextClassWidget().updateAll(applicationContext)
                LargeTodayCourseWidgetProvider.refreshAllWidgets(applicationContext)
            }
            AppLogger.d("WidgetRefreshWorker", "Widget 周期刷新完成")
            Result.success()
        } catch (e: Exception) {
            AppLogger.e("WidgetRefreshWorker", "Widget 周期刷新失败", e)
            Result.retry()
        }
    }
}
