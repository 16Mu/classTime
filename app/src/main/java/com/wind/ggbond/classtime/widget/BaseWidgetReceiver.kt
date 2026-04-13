package com.wind.ggbond.classtime.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

abstract class BaseWidgetReceiver : GlanceAppWidgetReceiver() {

    abstract val refreshAction: String

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == refreshAction) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, this::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            onUpdate(context, appWidgetManager, widgetIds)
        } else {
            super.onReceive(context, intent)
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
