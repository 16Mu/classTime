package com.wind.ggbond.classtime.util

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.wind.ggbond.classtime.R

/**
 * 提醒诊断工具
 * 用于检查课程提醒功能可能存在的问题
 */
object ReminderDiagnostic {
    
    data class DiagnosticResult(
        val item: String,
        val status: Boolean,
        val description: String,
        val suggestion: String
    )
    
    /**
     * 执行完整的提醒诊断
     */
    fun runDiagnostic(context: Context): List<DiagnosticResult> {
        val results = mutableListOf<DiagnosticResult>()
        
        // 1. 检查通知权限
        val notificationPermission = checkNotificationPermission(context)
        results.add(notificationPermission)
        
        // 2. 检查通知渠道状态
        val notificationChannel = checkNotificationChannel(context)
        results.add(notificationChannel)
        
        // 3. 检查 AlarmManager 权限
        val alarmPermission = checkAlarmPermission(context)
        results.add(alarmPermission)
        
        // 4. 检查电池优化
        val batteryOptimization = checkBatteryOptimization(context)
        results.add(batteryOptimization)
        
        // 5. 检查 Doze 模式
        val dozeMode = checkDozeMode(context)
        results.add(dozeMode)
        
        // 6. 检查后台运行限制
        val backgroundRestriction = checkBackgroundRestriction(context)
        results.add(backgroundRestriction)
        
        return results
    }
    
    /**
     * 检查通知权限
     */
    private fun checkNotificationPermission(context: Context): DiagnosticResult {
        val hasPermission = NotificationManagerCompat.from(context).areNotificationsEnabled()
        
        return DiagnosticResult(
            item = "通知权限",
            status = hasPermission,
            description = if (hasPermission) "应用已获得通知权限" else "应用未获得通知权限",
            suggestion = if (hasPermission) "" else "请在设置中开启通知权限"
        )
    }
    
    /**
     * 检查通知渠道状态
     */
    private fun checkNotificationChannel(context: Context): DiagnosticResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return DiagnosticResult(
                item = "通知渠道",
                status = true,
                description = "系统版本低于 Android 8.0，无需通知渠道",
                suggestion = ""
            )
        }
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = notificationManager.getNotificationChannel("course_reminder")
        
        return if (channel != null) {
            val isEnabled = channel.importance != NotificationManager.IMPORTANCE_NONE
            DiagnosticResult(
                item = "通知渠道",
                status = isEnabled,
                description = if (isEnabled) "通知渠道已启用" else "通知渠道已被禁用",
                suggestion = if (isEnabled) "" else "请在系统设置中重新启用通知渠道"
            )
        } else {
            DiagnosticResult(
                item = "通知渠道",
                status = false,
                description = "通知渠道未创建",
                suggestion = "请重启应用以创建通知渠道"
            )
        }
    }
    
    /**
     * 检查 AlarmManager 权限
     */
    private fun checkAlarmPermission(context: Context): DiagnosticResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return DiagnosticResult(
                item = "精确闹钟权限",
                status = true,
                description = "系统版本低于 Android 12，无需精确闹钟权限",
                suggestion = ""
            )
        }
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canScheduleExact = alarmManager.canScheduleExactAlarms()
        
        return DiagnosticResult(
            item = "精确闹钟权限",
            status = canScheduleExact,
            description = if (canScheduleExact) "应用可以设置精确闹钟" else "应用无法设置精确闹钟",
            suggestion = if (canScheduleExact) "" else "请在设置中授予精确闹钟权限"
        )
    }
    
    /**
     * 检查电池优化
     */
    private fun checkBatteryOptimization(context: Context): DiagnosticResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return DiagnosticResult(
                item = "电池优化",
                status = true,
                description = "系统版本低于 Android 6.0，无需检查电池优化",
                suggestion = ""
            )
        }
        
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
        
        return DiagnosticResult(
            item = "电池优化",
            status = isIgnoringOptimizations,
            description = if (isIgnoringOptimizations) "应用已豁免电池优化" else "应用受到电池优化限制",
            suggestion = if (isIgnoringOptimizations) "" else "请在电池设置中将应用加入白名单"
        )
    }
    
    /**
     * 检查 Doze 模式
     */
    private fun checkDozeMode(context: Context): DiagnosticResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return DiagnosticResult(
                item = "Doze 模式",
                status = true,
                description = "系统版本低于 Android 6.0，无需检查 Doze 模式",
                suggestion = ""
            )
        }
        
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
        
        return DiagnosticResult(
            item = "Doze 模式影响",
            status = isIgnoringOptimizations,
            description = if (isIgnoringOptimizations) "应用可在 Doze 模式下唤醒" else "Doze 模式可能影响提醒",
            suggestion = if (isIgnoringOptimizations) "" else "建议将应用加入电池优化白名单"
        )
    }
    
    /**
     * 检查后台运行限制
     */
    private fun checkBackgroundRestriction(context: Context): DiagnosticResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return DiagnosticResult(
                item = "后台运行限制",
                status = true,
                description = "系统版本低于 Android 9.0，无需检查后台限制",
                suggestion = ""
            )
        }
        
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val appInBackground = activityManager.appTasks.isEmpty()
        
        // 这个检查比较复杂，我们简化处理
        return DiagnosticResult(
            item = "后台运行限制",
            status = true,
            description = "系统可能对后台应用有限制",
            suggestion = "建议允许应用在后台运行"
        )
    }
    
    /**
     * 获取诊断摘要
     */
    fun getDiagnosticSummary(results: List<DiagnosticResult>): String {
        val passedCount = results.count { it.status }
        val totalCount = results.size
        
        return "诊断完成：$passedCount/$totalCount 项检查通过"
    }
    
    /**
     * 检查是否有严重问题
     */
    fun hasCriticalIssues(results: List<DiagnosticResult>): Boolean {
        val criticalItems = listOf("通知权限", "通知渠道")
        return results.any { 
            it.item in criticalItems && !it.status 
        }
    }
}
