package com.wind.ggbond.classtime.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.wind.ggbond.classtime.MainActivity
import com.wind.ggbond.classtime.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 4x4 大尺寸今日课程小组件 - AppWidgetProvider
 * 
 * 负责：
 * - 接收系统广播（添加/更新/删除 Widget）
 * - 构建 RemoteViews 并绑定数据
 * - 处理自定义刷新广播
 * - 管理 ListView 的数据适配器
 */
class LargeTodayCourseWidgetProvider : AppWidgetProvider() {

    companion object {
        /** 自定义刷新 Action */
        const val ACTION_REFRESH = "com.wind.ggbond.classtime.ACTION_REFRESH_LARGE_WIDGET"
        
        /** 协程作用域，用于异步操作 */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        /**
         * 手动触发所有小组件更新
         * 
         * @param context 上下文
         */
        fun refreshAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, LargeTodayCourseWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            // 通知数据变更
            appWidgetManager.notifyAppWidgetViewDataChanged(widgetIds, R.id.lv_courses)
            
            // 发送更新广播
            val intent = Intent(context, LargeTodayCourseWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            }
            context.sendBroadcast(intent)
        }
    }

    /**
     * 接收广播
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        // 处理自定义刷新广播
        if (intent.action == ACTION_REFRESH) {
            refreshAllWidgets(context)
        }
    }

    /**
     * 更新小组件
     * 
     * @param context 上下文
     * @param appWidgetManager 小组件管理器
     * @param appWidgetIds 需要更新的小组件 ID 数组
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 遍历所有需要更新的小组件
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    /**
     * 更新单个小组件
     * 
     * @param context 上下文
     * @param appWidgetManager 小组件管理器
     * @param appWidgetId 小组件 ID
     */
    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        // 创建 RemoteViews
        val views = RemoteViews(context.packageName, R.layout.widget_large_today_course)

        // 设置 ListView 的 RemoteViewsService（必须在 updateAppWidget 之前设置）
        val serviceIntent = Intent(context, LargeTodayCourseWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            // 使用 Uri 区分不同的小组件实例
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.lv_courses, serviceIntent)

        // 设置空视图
        views.setEmptyView(R.id.lv_courses, R.id.empty_container)

        // 设置点击整个小组件跳转到主应用
        val mainIntent = Intent(context, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.header_container, mainPendingIntent)

        // 设置 ListView 项的点击模板
        val itemClickIntent = Intent(context, MainActivity::class.java)
        val itemClickPendingIntent = PendingIntent.getActivity(
            context,
            1,
            itemClickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        views.setPendingIntentTemplate(R.id.lv_courses, itemClickPendingIntent)

        // 设置刷新按钮点击事件
        val refreshIntent = Intent(context, LargeTodayCourseWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)

        // 先更新小组件基础布局（确保 RemoteAdapter 已设置）
        appWidgetManager.updateAppWidget(appWidgetId, views)

        // 异步加载数据并更新头部信息
        scope.launch {
            try {
                val displayData = WidgetDataProvider.getTodayCourses(context)
                
                // 创建新的 RemoteViews 用于更新头部信息
                val headerViews = RemoteViews(context.packageName, R.layout.widget_large_today_course)
                
                // 重新设置 RemoteAdapter（必须）
                headerViews.setRemoteAdapter(R.id.lv_courses, serviceIntent)
                headerViews.setEmptyView(R.id.lv_courses, R.id.empty_container)
                
                // 重新设置点击事件
                headerViews.setOnClickPendingIntent(R.id.header_container, mainPendingIntent)
                headerViews.setPendingIntentTemplate(R.id.lv_courses, itemClickPendingIntent)
                headerViews.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)
                
                // 更新日期信息
                headerViews.setTextViewText(R.id.tv_date, displayData.dateText)
                headerViews.setTextViewText(R.id.tv_day_of_week, displayData.dayOfWeekText)
                headerViews.setTextViewText(R.id.tv_week_number, displayData.weekNumberText)
                
                // 更新课程数量和视图可见性
                if (displayData.courseItems.isNotEmpty()) {
                    headerViews.setTextViewText(R.id.tv_course_count, "${displayData.courseItems.size}节课")
                    headerViews.setViewVisibility(R.id.tv_course_count, android.view.View.VISIBLE)
                    headerViews.setViewVisibility(R.id.lv_courses, android.view.View.VISIBLE)
                    headerViews.setViewVisibility(R.id.empty_container, android.view.View.GONE)
                } else {
                    headerViews.setViewVisibility(R.id.tv_course_count, android.view.View.GONE)
                    headerViews.setViewVisibility(R.id.lv_courses, android.view.View.GONE)
                    headerViews.setViewVisibility(R.id.empty_container, android.view.View.VISIBLE)
                    headerViews.setTextViewText(R.id.tv_empty_message, displayData.emptyMessage ?: "今日无课程")
                }
                
                // 更新小组件
                appWidgetManager.updateAppWidget(appWidgetId, headerViews)
                
                // 通知 ListView 数据变更
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.lv_courses)
            } catch (e: Exception) {
                // 异常时显示错误信息
                val errorViews = RemoteViews(context.packageName, R.layout.widget_large_today_course)
                errorViews.setRemoteAdapter(R.id.lv_courses, serviceIntent)
                errorViews.setEmptyView(R.id.lv_courses, R.id.empty_container)
                errorViews.setOnClickPendingIntent(R.id.header_container, mainPendingIntent)
                errorViews.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)
                errorViews.setViewVisibility(R.id.lv_courses, android.view.View.GONE)
                errorViews.setViewVisibility(R.id.empty_container, android.view.View.VISIBLE)
                errorViews.setTextViewText(R.id.tv_empty_message, "数据加载失败")
                appWidgetManager.updateAppWidget(appWidgetId, errorViews)
            }
        }
    }

    /**
     * 首个小组件实例被添加时调用
     */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // 启动周期性刷新
        WidgetRefreshHelper.startPeriodicRefresh(context)
    }

    /**
     * 最后一个小组件实例被移除时调用
     */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // 检查是否还有其他小组件
        if (!WidgetRefreshHelper.hasActiveWidgets(context)) {
            WidgetRefreshHelper.stopPeriodicRefresh(context)
        }
    }
}
