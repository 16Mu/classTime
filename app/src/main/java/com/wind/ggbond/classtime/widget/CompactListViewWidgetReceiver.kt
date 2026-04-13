package com.wind.ggbond.classtime.widget

import androidx.glance.appwidget.GlanceAppWidget

class CompactListViewWidgetReceiver : BaseWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = CompactListViewWidget()

    override val refreshAction: String = ACTION_REFRESH_WIDGET

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.wind.ggbond.classtime.ACTION_REFRESH_COMPACT_LIST_WIDGET"
    }
}
