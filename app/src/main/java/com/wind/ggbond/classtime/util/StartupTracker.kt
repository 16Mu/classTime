package com.wind.ggbond.classtime.util

import com.wind.ggbond.classtime.BuildConfig

object StartupTracker {

    private const val TAG = "StartupTracker"

    private var appCreateStart: Long = 0L
    private var appCreateEnd: Long = 0L
    private var activityCreateStart: Long = 0L
    private var criticalInitStart: Long = 0L
    private var criticalInitEnd: Long = 0L
    private var deferredInitStart: Long = 0L
    private var deferredInitEnd: Long = 0L
    private var firstFrameRendered: Long = 0L

    private val stepTimings = mutableMapOf<String, Long>()

    fun markAppCreateStart() {
        appCreateStart = System.currentTimeMillis()
    }

    fun markAppCreateEnd() {
        appCreateEnd = System.currentTimeMillis()
        logIfDebug("Application.onCreate 耗时: ${appCreateEnd - appCreateStart}ms")
    }

    fun markActivityCreateStart() {
        activityCreateStart = System.currentTimeMillis()
    }

    fun markCriticalInitStart() {
        criticalInitStart = System.currentTimeMillis()
    }

    fun markCriticalInitEnd() {
        criticalInitEnd = System.currentTimeMillis()
        logIfDebug("关键初始化耗时: ${criticalInitEnd - criticalInitStart}ms")
    }

    fun markDeferredInitStart() {
        deferredInitStart = System.currentTimeMillis()
    }

    fun markDeferredInitEnd() {
        deferredInitEnd = System.currentTimeMillis()
        logIfDebug("延迟初始化耗时: ${deferredInitEnd - deferredInitStart}ms")
    }

    fun markFirstFrameRendered() {
        firstFrameRendered = System.currentTimeMillis()
        logIfDebug("首帧渲染完成，冷启动总耗时: ${firstFrameRendered - appCreateStart}ms")
    }

    fun markStep(stepName: String) {
        stepTimings[stepName] = System.currentTimeMillis()
        logIfDebug("启动节点: $stepName")
    }

    fun report() {
        if (!BuildConfig.DEBUG) return
        val sb = StringBuilder()
        sb.appendLine("=== 启动性能报告 ===")
        if (appCreateStart > 0) sb.appendLine("Application.onCreate: ${appCreateEnd - appCreateStart}ms")
        if (criticalInitStart > 0) sb.appendLine("关键初始化: ${criticalInitEnd - criticalInitStart}ms")
        if (deferredInitStart > 0) sb.appendLine("延迟初始化: ${deferredInitEnd - deferredInitStart}ms")
        if (firstFrameRendered > 0) sb.appendLine("冷启动总耗时: ${firstFrameRendered - appCreateStart}ms")
        sb.appendLine("=== 报告结束 ===")
        AppLogger.d(TAG, sb.toString())
    }

    private fun logIfDebug(message: String) {
        if (BuildConfig.DEBUG) {
            AppLogger.d(TAG, message)
        }
    }
}
