package com.wind.ggbond.classtime.widget

import androidx.glance.appwidget.GlanceAppWidget

class NextClassWidgetReceiver : BaseWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = NextClassWidget()

    override val refreshAction: String = ACTION_REFRESH_WIDGET

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.wind.ggbond.classtime.ACTION_REFRESH_NEXT_CLASS_WIDGET"
    }
}
