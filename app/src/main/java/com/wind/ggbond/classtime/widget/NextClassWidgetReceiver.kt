package com.wind.ggbond.classtime.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 下节课倒计时桌面小组件接收器
 * 
 * 接收系统广播和自定义刷新广播，触发 Widget 更新。
 */
class NextClassWidgetReceiver : GlanceAppWidgetReceiver() {

    /** 关联的 Widget 实例 */
    override val glanceAppWidget: GlanceAppWidget = NextClassWidget()

    companion object {
        /** 自定义刷新 Action */
        const val ACTION_REFRESH_WIDGET = "com.wind.ggbond.classtime.ACTION_REFRESH_NEXT_CLASS_WIDGET"
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // 收到自定义刷新广播时，触发所有实例更新
        if (intent.action == ACTION_REFRESH_WIDGET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, NextClassWidgetReceiver::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            // 通知系统更新所有 Widget
            val updateIntent = Intent(context, NextClassWidgetReceiver::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            }
            context.sendBroadcast(updateIntent)
        }
    }

    /**
     * 首个 Widget 实例被添加到桌面时调用
     * 启动周期性刷新任务，保持倒计时数据更新
     */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshHelper.startPeriodicRefresh(context)
    }

    /**
     * 最后一个 Widget 实例被移除时调用
     * 检查是否还有其他类型的 Widget，无则停止周期刷新
     */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (!WidgetRefreshHelper.hasActiveWidgets(context)) {
            WidgetRefreshHelper.stopPeriodicRefresh(context)
        }
    }
}
