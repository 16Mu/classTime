package com.wind.ggbond.classtime.widget

import androidx.glance.appwidget.GlanceAppWidget

class TomorrowCourseWidgetReceiver : BaseWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = TomorrowCourseWidget()

    override val refreshAction: String = ACTION_REFRESH_WIDGET

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.wind.ggbond.classtime.ACTION_REFRESH_TOMORROW_WIDGET"
    }
}
