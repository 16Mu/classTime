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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import com.wind.ggbond.classtime.util.AppLogger
import kotlinx.coroutines.launch

class LargeTodayCourseWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.wind.ggbond.classtime.ACTION_REFRESH_LARGE_WIDGET"

        private val errorHandler = CoroutineExceptionHandler { _, throwable ->
            AppLogger.e("LargeTodayWidgetProvider", "协程异常", throwable)
        }

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + errorHandler)

        fun cancelScope() {
            scope.cancel()
        }

        fun refreshAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, LargeTodayCourseWidgetProvider::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(componentName)

                appWidgetManager.notifyAppWidgetViewDataChanged(widgetIds, R.id.lv_courses)

                val intent = Intent(context, LargeTodayCourseWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
                }
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                AppLogger.e("LargeTodayWidgetProvider", "refreshAllWidgets 异常", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            refreshAllWidgets(context)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        try {
            val views = RemoteViews(context.packageName, R.layout.widget_large_today_course)

            val serviceIntent = Intent(context, LargeTodayCourseWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.lv_courses, serviceIntent)
            views.setEmptyView(R.id.lv_courses, R.id.empty_container)

            val mainIntent = Intent(context, MainActivity::class.java)
            val mainPendingIntent = PendingIntent.getActivity(
                context,
                0,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.header_container, mainPendingIntent)

            val itemClickIntent = Intent(context, MainActivity::class.java)
            val itemClickPendingIntent = PendingIntent.getActivity(
                context,
                1,
                itemClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.lv_courses, itemClickPendingIntent)

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

            appWidgetManager.updateAppWidget(appWidgetId, views)

            scope.launch {
                try {
                    val displayData = WidgetDataProvider.getTodayCourses(context)

                    val headerViews = RemoteViews(context.packageName, R.layout.widget_large_today_course)
                    headerViews.setRemoteAdapter(R.id.lv_courses, serviceIntent)
                    headerViews.setEmptyView(R.id.lv_courses, R.id.empty_container)
                    headerViews.setOnClickPendingIntent(R.id.header_container, mainPendingIntent)
                    headerViews.setPendingIntentTemplate(R.id.lv_courses, itemClickPendingIntent)
                    headerViews.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)

                    headerViews.setTextViewText(R.id.tv_date, displayData.dateText)
                    headerViews.setTextViewText(R.id.tv_day_of_week, displayData.dayOfWeekText)
                    headerViews.setTextViewText(R.id.tv_week_number, displayData.weekNumberText)

                    if (displayData.courseItems.isNotEmpty()) {
                        headerViews.setTextViewText(R.id.tv_course_count, "${displayData.courseItems.size}节课")
                        headerViews.setViewVisibility(R.id.tv_course_count, android.view.View.VISIBLE)
                        headerViews.setViewVisibility(R.id.lv_courses, android.view.View.VISIBLE)
                        headerViews.setViewVisibility(R.id.empty_container, android.view.View.GONE)
                    } else {
                        headerViews.setViewVisibility(R.id.tv_course_count, android.view.View.GONE)
                        headerViews.setViewVisibility(R.id.lv_courses, android.view.View.GONE)
                        headerViews.setViewVisibility(R.id.empty_container, android.view.View.VISIBLE)
                        headerViews.setTextViewText(
                            R.id.tv_empty_message,
                            displayData.emptyMessage ?: "今日无课程"
                        )
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, headerViews)
                    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.lv_courses)
                } catch (e: Exception) {
                    AppLogger.e("LargeTodayWidgetProvider", "异步更新异常", e)
                    val errorViews = RemoteViews(context.packageName, R.layout.widget_large_today_course)
                    errorViews.setRemoteAdapter(R.id.lv_courses, serviceIntent)
                    errorViews.setEmptyView(R.id.lv_courses, R.id.empty_container)
                    errorViews.setOnClickPendingIntent(R.id.header_container, mainPendingIntent)
                    errorViews.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)
                    errorViews.setViewVisibility(R.id.lv_courses, android.view.View.GONE)
                    errorViews.setViewVisibility(R.id.empty_container, android.view.View.VISIBLE)
                    errorViews.setTextViewText(R.id.tv_empty_message, "数据加载失败，请点击刷新")
                    appWidgetManager.updateAppWidget(appWidgetId, errorViews)
                }
            }
        } catch (e: Exception) {
            AppLogger.e("LargeTodayWidgetProvider", "updateAppWidget 异常", e)
            val errorViews = RemoteViews(context.packageName, R.layout.widget_large_today_course)
            errorViews.setViewVisibility(R.id.lv_courses, android.view.View.GONE)
            errorViews.setViewVisibility(R.id.empty_container, android.view.View.VISIBLE)
            errorViews.setTextViewText(R.id.tv_empty_message, "小组件加载失败")
            appWidgetManager.updateAppWidget(appWidgetId, errorViews)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshHelper.startPeriodicRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelScope()
        if (!WidgetRefreshHelper.hasActiveWidgets(context)) {
            WidgetRefreshHelper.stopPeriodicRefresh(context)
        }
    }
}
