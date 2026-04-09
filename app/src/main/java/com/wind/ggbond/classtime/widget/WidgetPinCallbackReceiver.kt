package com.wind.ggbond.classtime.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.wind.ggbond.classtime.util.AppLogger
import androidx.core.app.NotificationManagerCompat

class WidgetPinCallbackReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WidgetPinCallback"
        const val EXTRA_WIDGET_TYPE = "widget_type"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AppWidgetManager.ACTION_APPWIDGET_CONFIGURE) return
        
        val result = resultCode
        val widgetType = intent.getStringExtra(EXTRA_WIDGET_TYPE) ?: "unknown"

        when (result) {
            Activity.RESULT_OK -> handleSuccess(context, widgetType)
            Activity.RESULT_CANCELED -> handleCancelled(context, widgetType)
            else -> handleUnknownResult(context, result, widgetType)
        }
    }

    private fun handleSuccess(context: Context, widgetType: String) {
        AppLogger.d(TAG, "Widget pin confirmed for type: $widgetType")
        
        Toast.makeText(
            context,
            "✅ 小组件已成功添加到桌面！",
            Toast.LENGTH_LONG
        ).show()

        try {
            refreshWidgetsAfterPin(context, widgetType)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to refresh widgets after pin", e)
        }
    }

    private fun handleCancelled(context: Context, widgetType: String) {
        AppLogger.w(TAG, "Widget pin cancelled by user for type: $widgetType")
        
        Toast.makeText(
            context,
            "⚠️ 已取消添加小组件",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun handleUnknownResult(context: Context, resultCode: Int, widgetType: String) {
        AppLogger.w(TAG, "Unknown result code $resultCode for widget type: $widgetType")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            showFallbackNotification(context, widgetType)
        } else {
            Toast.makeText(
                context,
                "小组件添加结果未知，请检查桌面",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun refreshWidgetsAfterPin(context: Context, widgetType: String) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)

            when (widgetType.lowercase()) {
                "next_class" -> {
                    val componentName = ComponentName(context, NextClassWidgetReceiver::class.java)
                    val ids = appWidgetManager.getAppWidgetIds(componentName)
                    if (ids.isNotEmpty()) {
                        appWidgetManager.notifyAppWidgetViewDataChanged(ids[0], 0)
                    }
                }
                "today_course" -> {
                    val componentName = ComponentName(context, TodayCourseWidgetReceiver::class.java)
                    val ids = appWidgetManager.getAppWidgetIds(componentName)
                    if (ids.isNotEmpty()) {
                        appWidgetManager.notifyAppWidgetViewDataChanged(ids[0], 0)
                    }
                }
                "large_today" -> {
                    val componentName = ComponentName(context, LargeTodayCourseWidgetProvider::class.java)
                    val ids = appWidgetManager.getAppWidgetIds(componentName)
                    if (ids.isNotEmpty()) {
                        appWidgetManager.notifyAppWidgetViewDataChanged(ids[0], 0)
                    }
                }
                "compact_list" -> {
                    val componentName = ComponentName(context, CompactListViewWidgetReceiver::class.java)
                    val ids = appWidgetManager.getAppWidgetIds(componentName)
                    if (ids.isNotEmpty()) {
                        appWidgetManager.notifyAppWidgetViewDataChanged(ids[0], 0)
                    }
                }
                "week_overview" -> {
                    val componentName = ComponentName(context, WeekOverviewWidgetReceiver::class.java)
                    val ids = appWidgetManager.getAppWidgetIds(componentName)
                    if (ids.isNotEmpty()) {
                        appWidgetManager.notifyAppWidgetViewDataChanged(ids[0], 0)
                    }
                }
                "tomorrow_course" -> {
                    val componentName = ComponentName(context, TomorrowCourseWidgetReceiver::class.java)
                    val ids = appWidgetManager.getAppWidgetIds(componentName)
                    if (ids.isNotEmpty()) {
                        appWidgetManager.notifyAppWidgetViewDataChanged(ids[0], 0)
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error refreshing widgets", e)
        }
    }

    private fun showFallbackNotification(context: Context, widgetType: String) {
        val channelId = "widget_pin_result"

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("课表小组件")
            .setContentText("请确认小组件是否已成功添加到桌面")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        with(NotificationManagerCompat.from(context)) {
            notify(System.currentTimeMillis().toInt(), notification)
        }
    }
}
