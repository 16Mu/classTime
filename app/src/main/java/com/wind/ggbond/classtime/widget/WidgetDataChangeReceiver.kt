package com.wind.ggbond.classtime.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wind.ggbond.classtime.util.AppLogger

class WidgetDataChangeReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_COURSE_DATA_CHANGED = "com.wind.ggbond.classtime.ACTION_COURSE_DATA_CHANGED"
        const val PERMISSION_SEND_DATA_CHANGE = "com.wind.ggbond.classtime.PERMISSION_SEND_DATA_CHANGE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_COURSE_DATA_CHANGED) {
            val senderPackage = resultData ?: intent.`package` ?: "unknown"
            if (senderPackage != context.packageName) {
                AppLogger.w("WidgetDataChangeReceiver", "忽略来自外部包的广播: $senderPackage")
                return
            }
            AppLogger.d("WidgetDataChangeReceiver", "收到课表数据变更广播，刷新小组件")
            WidgetRefreshHelper.refreshAllWidgets(context)
        }
    }
}
