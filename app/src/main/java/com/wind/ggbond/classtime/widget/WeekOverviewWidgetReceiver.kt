package com.wind.ggbond.classtime.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class WeekOverviewWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = WeekOverviewWidget()

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.wind.ggbond.classtime.ACTION_REFRESH_WEEK_OVERVIEW_WIDGET"
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, WeekOverviewWidgetReceiver::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            val updateIntent = Intent(context, WeekOverviewWidgetReceiver::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            }
            context.sendBroadcast(updateIntent)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshHelper.startPeriodicRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (!WidgetRefreshHelper.hasActiveWidgets(context)) {
            WidgetRefreshHelper.stopPeriodicRefresh(context)
        }
    }
}
