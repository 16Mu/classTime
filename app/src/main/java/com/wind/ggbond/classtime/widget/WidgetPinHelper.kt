package com.wind.ggbond.classtime.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast

/**
 * 小组件固定到桌面的辅助类
 * 
 * 使用 Android 8.0+ 的 requestPinAppWidget API
 * 允许用户从应用内快速将小组件添加到桌面
 */
object WidgetPinHelper {

    /**
     * 小组件类型枚举
     */
    enum class WidgetType {
        /** 今日课程（4x2） */
        TODAY_COURSE,
        /** 下节课倒计时（3x2） */
        NEXT_CLASS
        // 临时禁用4x4大尺寸小组件
        // /** 今日课程大尺寸（4x4） */
        // LARGE_TODAY_COURSE
    }

    /**
     * 检查系统是否支持固定小组件
     * 
     * @param context 上下文
     * @return 是否支持
     */
    fun isRequestPinAppWidgetSupported(context: Context): Boolean {
        // Android 8.0 (API 26) 及以上版本支持
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }
        val appWidgetManager = AppWidgetManager.getInstance(context)
        return appWidgetManager.isRequestPinAppWidgetSupported
    }

    /**
     * 请求将指定类型的小组件固定到桌面
     * 
     * @param context 上下文
     * @param widgetType 小组件类型
     * @return 是否成功发起请求
     */
    fun requestPinWidget(context: Context, widgetType: WidgetType): Boolean {
        // 检查系统是否支持
        if (!isRequestPinAppWidgetSupported(context)) {
            Toast.makeText(context, "当前系统不支持此功能", Toast.LENGTH_SHORT).show()
            return false
        }

        val appWidgetManager = AppWidgetManager.getInstance(context)
        
        // 根据类型获取对应的 Provider 组件
        val providerComponent = when (widgetType) {
            WidgetType.TODAY_COURSE -> ComponentName(context, TodayCourseWidgetReceiver::class.java)
            WidgetType.NEXT_CLASS -> ComponentName(context, NextClassWidgetReceiver::class.java)
            // 临时禁用4x4大尺寸小组件
            // WidgetType.LARGE_TODAY_COURSE -> ComponentName(context, LargeTodayCourseWidgetProvider::class.java)
        }

        // 创建回调 PendingIntent（可选，用于接收固定结果）
        val callbackIntent = Intent(context, WidgetPinCallbackReceiver::class.java)
        val successCallback = PendingIntent.getBroadcast(
            context,
            widgetType.ordinal,
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 发起固定请求
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appWidgetManager.requestPinAppWidget(providerComponent, null, successCallback)
            } else {
                false
            }
        } catch (e: Exception) {
            Toast.makeText(context, "添加小组件失败: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * 获取小组件的显示名称
     * 
     * @param widgetType 小组件类型
     * @return 显示名称
     */
    fun getWidgetDisplayName(widgetType: WidgetType): String {
        return when (widgetType) {
            WidgetType.TODAY_COURSE -> "今日课程"
            WidgetType.NEXT_CLASS -> "下节课倒计时"
            // 临时禁用4x4大尺寸小组件
            // WidgetType.LARGE_TODAY_COURSE -> "今日课程(大)"
        }
    }

    /**
     * 获取小组件的描述
     * 
     * @param widgetType 小组件类型
     * @return 描述文本
     */
    fun getWidgetDescription(widgetType: WidgetType): String {
        return when (widgetType) {
            WidgetType.TODAY_COURSE -> "4x2 尺寸，显示今日课程列表"
            WidgetType.NEXT_CLASS -> "3x2 尺寸，显示下节课倒计时"
            // 临时禁用4x4大尺寸小组件
            // WidgetType.LARGE_TODAY_COURSE -> "4x4 尺寸，显示更多课程详情"
        }
    }
}
