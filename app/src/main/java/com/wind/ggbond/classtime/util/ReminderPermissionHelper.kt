package com.wind.ggbond.classtime.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 课程提醒权限检查助手
 * 用于检查和引导用户授予必要的后台权限，确保课程提醒能够正常工作
 */
object ReminderPermissionHelper {
    
    private const val TAG = "ReminderPermissionHelper"
    
    /**
     * 权限检查结果
     */
    data class PermissionCheckResult(
        val allGranted: Boolean,  // 是否所有权限都已授予
        val missingPermissions: List<PermissionItem>  // 缺失的权限列表
    )
    
    /**
     * 权限项
     */
    data class PermissionItem(
        val name: String,  // 权限名称
        val description: String,  // 权限描述
        val isGranted: Boolean,  // 是否已授予
        val importance: PermissionImportance,  // 重要性
        val intentAction: String?,  // 跳转到设置的Intent Action
        val requiresManualCheck: Boolean = false  // 是否需要手动检查（无法通过代码检测）
    )
    
    enum class PermissionImportance {
        CRITICAL,  // 关键权限，必须授予
        IMPORTANT,  // 重要权限，强烈建议授予
        OPTIONAL   // 可选权限
    }
    
    /**
     * 检查所有课程提醒所需的权限
     */
    fun checkAllPermissions(context: Context): PermissionCheckResult {
        val permissions = mutableListOf<PermissionItem>()
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        // 1. 通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            permissions.add(
                PermissionItem(
                    name = "通知权限",
                    description = "允许应用发送课程提醒通知",
                    isGranted = notificationPermission,
                    importance = PermissionImportance.CRITICAL,
                    intentAction = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                )
            )
        }
        
        // 2. 电池优化白名单
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryOptimizationIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true  // Android 6.0 以下不需要
        }
        
        permissions.add(
            PermissionItem(
                name = "电池优化白名单",
                description = "允许应用在后台运行，不受电池优化限制，确保提醒准时送达",
                isGranted = batteryOptimizationIgnored,
                importance = PermissionImportance.CRITICAL,
                intentAction = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            )
        )
        
        // 3. 精确定时权限（Android 12+）
        val exactAlarmPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
        } else {
            true
        }
        
        permissions.add(
            PermissionItem(
                name = "准时提醒权限",
                description = "确保通知能在精确的时间送达（不是真的闹钟，只是让通知准时弹出）",
                isGranted = exactAlarmPermission,
                importance = PermissionImportance.CRITICAL,
                intentAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                } else null
            )
        )
        
        // 4. 自启动权限（仅特定厂商需要，读取 SharedPreferences 中的手动确认状态）
        if (manufacturer.contains("xiaomi") || manufacturer.contains("huawei") ||
            manufacturer.contains("oppo") || manufacturer.contains("vivo")) {
            val autoStartConfirmed = PermissionGuideManager.isManualStepConfirmed(context, "auto_start")
            permissions.add(
                PermissionItem(
                    name = "自启动权限",
                    description = "允许应用开机自动启动和后台自启动，确保提醒服务持续运行",
                    isGranted = autoStartConfirmed,
                    importance = PermissionImportance.IMPORTANT,
                    intentAction = null,
                    requiresManualCheck = true
                )
            )
        }
        
        // 5. 后台弹出界面权限（仅小米/OPPO 需要，读取 SharedPreferences 中的手动确认状态）
        if (manufacturer.contains("xiaomi") || manufacturer.contains("oppo")) {
            val popupConfirmed = PermissionGuideManager.isManualStepConfirmed(context, "background_popup")
            permissions.add(
                PermissionItem(
                    name = "后台弹出界面",
                    description = "允许应用在后台弹出提醒窗口",
                    isGranted = popupConfirmed,
                    importance = PermissionImportance.IMPORTANT,
                    intentAction = null,
                    requiresManualCheck = true
                )
            )
        }
        
        // 6. 悬浮窗权限（部分厂商需要）
        val overlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
        
        permissions.add(
            PermissionItem(
                name = "显示悬浮窗",
                description = "部分手机（如小米）需要此权限才能弹出提醒",
                isGranted = overlayPermission,
                importance = PermissionImportance.IMPORTANT,
                intentAction = Settings.ACTION_MANAGE_OVERLAY_PERMISSION
            )
        )
        
        // 7. 全屏通知权限（Android 14+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val fullScreenIntentPermission = notificationManager.canUseFullScreenIntent()
            
            permissions.add(
                PermissionItem(
                    name = "全屏通知权限",
                    description = "允许应用显示全屏提醒通知",
                    isGranted = fullScreenIntentPermission,
                    importance = PermissionImportance.IMPORTANT,
                    intentAction = Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT
                )
            )
        }
        
        // 检查是否所有关键权限都已授予
        val missingCriticalPermissions = permissions.filter { 
            !it.isGranted && it.importance == PermissionImportance.CRITICAL 
        }
        val missingImportantPermissions = permissions.filter { 
            !it.isGranted && it.importance == PermissionImportance.IMPORTANT 
        }
        
        val missingPermissions = missingCriticalPermissions + missingImportantPermissions

        return PermissionCheckResult(
            allGranted = missingCriticalPermissions.isEmpty() && missingImportantPermissions.isEmpty(),
            missingPermissions = missingPermissions
        )
    }
    
    /**
     * 跳转到相应的权限设置页面
     */
    fun openPermissionSettings(context: Context, permission: PermissionItem) {
        try {
            when {
                // 电池优化
                permission.intentAction == Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS -> {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
                
                // 通知设置
                permission.intentAction == Settings.ACTION_APP_NOTIFICATION_SETTINGS -> {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                }
                
                // 悬浮窗权限
                permission.intentAction == Settings.ACTION_MANAGE_OVERLAY_PERMISSION -> {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
                
                // 精确闹钟权限
                permission.intentAction == Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                }
                
                // 全屏通知权限
                permission.intentAction == Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                }
                
                // 需要手动引导的权限
                permission.requiresManualCheck -> {
                    openManualPermissionGuide(context, permission.name)
                }
                
                // 默认：打开应用详情页
                else -> {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开权限设置失败: ${permission.name}", e)
            // 降级方案：打开应用详情页
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "打开应用详情页也失败", e2)
            }
        }
    }
    
    /**
     * 打开需要手动设置的权限引导
     */
    private fun openManualPermissionGuide(context: Context, permissionName: String) {
        // 根据不同厂商，尝试打开对应的设置页面
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        try {
            when {
                // 小米
                manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                    when (permissionName) {
                        "自启动权限" -> {
                            val intent = Intent().apply {
                                component = android.content.ComponentName(
                                    "com.miui.securitycenter",
                                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                                )
                            }
                            context.startActivity(intent)
                        }
                        "后台弹出界面" -> {
                            val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                                putExtra("extra_pkgname", context.packageName)
                            }
                            context.startActivity(intent)
                        }
                    }
                }
                
                // 华为
                manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                    val intent = Intent().apply {
                        component = android.content.ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                        )
                    }
                    context.startActivity(intent)
                }
                
                // OPPO
                manufacturer.contains("oppo") -> {
                    val intent = Intent().apply {
                        component = android.content.ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                        )
                    }
                    context.startActivity(intent)
                }
                
                // VIVO
                manufacturer.contains("vivo") -> {
                    val intent = Intent().apply {
                        component = android.content.ComponentName(
                            "com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                        )
                    }
                    context.startActivity(intent)
                }
                
                else -> {
                    // 其他厂商，打开应用详情页
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开厂商特定设置页面失败: $manufacturer", e)
            // 降级：打开应用详情页
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "打开应用详情页失败", e2)
            }
        }
    }
    
    /**
     * 获取当前手机厂商的提示文本
     */
    fun getManufacturerGuideText(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                "小米/Redmi 手机：\n" +
                "1. 前往「设置 > 应用设置 > 应用管理」\n" +
                "2. 找到本应用，开启「自启动」\n" +
                "3. 在「权限管理 > 后台弹出界面」中允许\n" +
                "4. 在「权限管理 > 显示悬浮窗」中允许"
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                "华为/荣耀 手机：\n" +
                "1. 前往「设置 > 应用 > 应用启动管理」\n" +
                "2. 找到本应用，设为「手动管理」\n" +
                "3. 开启「自动启动」「后台活动」「悬浮窗」"
            }
            manufacturer.contains("oppo") -> {
                "OPPO 手机：\n" +
                "1. 前往「设置 > 应用管理 > 应用列表」\n" +
                "2. 找到本应用，点击「应用权限」\n" +
                "3. 开启「自启动」「后台运行」「悬浮窗」"
            }
            manufacturer.contains("vivo") -> {
                "VIVO 手机：\n" +
                "1. 前往「设置 > 应用与权限 > 权限管理」\n" +
                "2. 找到本应用\n" +
                "3. 开启「自启动」「后台弹出」「悬浮窗」"
            }
            else -> {
                "请在手机设置中：\n" +
                "1. 允许应用自动启动\n" +
                "2. 允许应用后台运行\n" +
                "3. 允许应用显示悬浮窗\n" +
                "4. 关闭电池优化限制"
            }
        }
    }
}


