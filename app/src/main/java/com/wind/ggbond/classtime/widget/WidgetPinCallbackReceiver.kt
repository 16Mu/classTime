package com.wind.ggbond.classtime.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * 小组件固定回调接收器
 * 
 * 接收小组件固定到桌面的结果回调
 */
class WidgetPinCallbackReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 小组件固定成功时显示提示
        Toast.makeText(context, "小组件已添加到桌面", Toast.LENGTH_SHORT).show()
    }
}
