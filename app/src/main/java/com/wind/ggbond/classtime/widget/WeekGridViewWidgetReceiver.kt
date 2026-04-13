package com.wind.ggbond.classtime.widget

import androidx.glance.appwidget.GlanceAppWidget

class WeekGridViewWidgetReceiver : BaseWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = WeekGridViewWidget()

    override val refreshAction: String = ACTION_REFRESH_WIDGET

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.wind.ggbond.classtime.ACTION_REFRESH_WEEK_GRID_VIEW_WIDGET"
    }
}
