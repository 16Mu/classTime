package com.wind.ggbond.classtime.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.wind.ggbond.classtime.util.AppLogger

class WidgetPinCallbackReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WidgetPinCallback"
        const val EXTRA_WIDGET_TYPE = "widget_type"

        private val WIDGET_TYPE_MAP = mapOf(
            "next_class" to NextClassWidgetReceiver::class.java,
            "today_course" to TodayCourseWidgetReceiver::class.java,
            "large_today_course" to LargeTodayCourseWidgetProvider::class.java,
            "compact_list" to CompactListViewWidgetReceiver::class.java,
            "week_overview" to WeekOverviewWidgetReceiver::class.java,
            "tomorrow_course" to TomorrowCourseWidgetReceiver::class.java
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        AppLogger.i(TAG, "=== onReceive CALLBACK === action=${intent.action}, extras=${intent.extras}")

        if (intent.action != AppWidgetManager.ACTION_APPWIDGET_CONFIGURE) {
            AppLogger.w(TAG, "Unexpected action: ${intent.action}, ignoring")
            return
        }

        val widgetType = intent.getStringExtra(EXTRA_WIDGET_TYPE) ?: "unknown"
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        AppLogger.i(TAG, "Widget pin callback received: widgetType=$widgetType, appWidgetId=$appWidgetId, resultCode=$resultCode")

        handleSuccess(context, widgetType, appWidgetId)
    }

    private fun handleSuccess(context: Context, widgetType: String, appWidgetId: Int) {
        AppLogger.i(TAG, "Widget pin confirmed for type: $widgetType, appWidgetId: $appWidgetId")

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

    private fun refreshWidgetsAfterPin(context: Context, widgetType: String) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val receiverClass = WIDGET_TYPE_MAP[widgetType.lowercase()]
            if (receiverClass != null) {
                val componentName = ComponentName(context, receiverClass)
                val ids = appWidgetManager.getAppWidgetIds(componentName)
                if (ids.isNotEmpty()) {
                    appWidgetManager.notifyAppWidgetViewDataChanged(ids[0], 0)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error refreshing widgets", e)
        }
    }
}
