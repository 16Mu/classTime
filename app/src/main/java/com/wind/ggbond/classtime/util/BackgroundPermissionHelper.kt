package com.wind.ggbond.classtime.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * 后台运行权限检查工具
 * 用于检查和应用后台运行所需的各种权限和设置
 */
object BackgroundPermissionHelper {
    private const val TAG = "BackgroundPermissionHelper"
    
    /**
     * 检查结果
     */
    data class BackgroundStatus(
        val canScheduleExactAlarms: Boolean,  // 精确时间权限
        val isIgnoringBatteryOptimizations: Boolean,  // 电池优化白名单
        val missingPermissions: List<String> = emptyList()  // 缺失的权限列表
    ) {
        val canRunInBackground: Boolean
            get() = canScheduleExactAlarms && isIgnoringBatteryOptimizations
    }
    
    /**
     * 检查所有后台运行所需的条件
     */
    fun checkBackgroundStatus(context: Context): BackgroundStatus {
        val canScheduleExactAlarms = canScheduleExactAlarms(context)
        val isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(context)
        
        val missingPermissions = mutableListOf<String>()
        if (!canScheduleExactAlarms) {
            missingPermissions.add("准时提醒权限")
        }
        if (!isIgnoringBatteryOptimizations) {
            missingPermissions.add("电池优化白名单")
        }
        
        return BackgroundStatus(
            canScheduleExactAlarms = canScheduleExactAlarms,
            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
            missingPermissions = missingPermissions
        )
    }
    
    /**
     * 检查是否可以调度精确时间提醒（Android 12+）
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val alarmManager = ContextCompat.getSystemService(context, android.app.AlarmManager::class.java)
                alarmManager?.canScheduleExactAlarms() ?: false
            } catch (e: Exception) {
                Log.e(TAG, "检查精确时间权限失败", e)
                false
            }
        } else {
            // Android 12 以下默认可用
            true
        }
    }
    
    /**
     * 检查是否在电池优化白名单中
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return try {
            val powerManager = ContextCompat.getSystemService(context, PowerManager::class.java)
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "检查电池优化状态失败", e)
            false
        }
    }
    
    /**
     * 请求精确时间权限（Android 12+）
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun requestExactAlarmPermission(activity: Activity, requestCode: Int) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivityForResult(intent, requestCode)
        } catch (e: Exception) {
            Log.e(TAG, "请求精确时间权限失败", e)
            // 如果无法打开设置页面，尝试打开应用设置
            openAppSettings(activity)
        }
    }
    
    /**
     * 请求电池优化白名单
     */
    fun requestBatteryOptimizationWhitelist(activity: Activity, requestCode: Int) {
        try {
            val powerManager = ContextCompat.getSystemService(activity, PowerManager::class.java)
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(activity.packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivityForResult(intent, requestCode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "请求电池优化白名单失败", e)
            // 如果无法打开设置页面，尝试打开应用设置
            openAppSettings(activity)
        }
    }
    
    /**
     * 打开应用设置页面
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开应用设置失败", e)
        }
    }
    
    /**
     * 打开电池优化设置页面
     * ⚠️ 国产手机优化：尝试直接打开厂商自定义的电池优化/省电设置
     */
    fun openBatteryOptimizationSettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        var opened = false
        
        try {
            // 根据不同品牌尝试打开对应的设置页面
            when {
                // 小米/Redmi - 省电策略设置
                manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                    opened = tryOpenXiaomiBatterySettings(context)
                }
                
                // 华为/荣耀 - 应用启动管理（包含电池优化）
                manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                    opened = tryOpenHuaweiBatterySettings(context)
                }
                
                // OPPO/一加 - 电池优化
                manufacturer.contains("oppo") || manufacturer.contains("oneplus") || manufacturer.contains("realme") -> {
                    opened = tryOpenOppoBatterySettings(context)
                }
                
                // VIVO - 后台高耗电
                manufacturer.contains("vivo") -> {
                    opened = tryOpenVivoBatterySettings(context)
                }
            }
            
            // 如果品牌特定方法失败，尝试标准Android方法
            if (!opened) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
                Log.d(TAG, "使用标准Android电池优化设置")
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开电池优化设置失败，尝试应用详情页", e)
            // 最后降级方案：跳转到应用详情页
            openAppSettings(context)
        }
    }
    
    /**
     * 小米/Redmi：尝试打开省电策略设置
     */
    private fun tryOpenXiaomiBatterySettings(context: Context): Boolean {
        return try {
            // 方法1：直接打开应用详情的电池优化页面
            val intent1 = Intent().apply {
                setClassName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
                putExtra("package_name", context.packageName)
                putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent1)
            Log.d(TAG, "打开小米电池优化设置（方法1）")
            true
        } catch (e1: Exception) {
            try {
                // 方法2：打开省电优化设置列表
                val intent2 = Intent().apply {
                    setClassName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent2)
                Log.d(TAG, "打开小米电池优化设置（方法2）")
                true
            } catch (e2: Exception) {
                Log.w(TAG, "小米专用设置页面无法打开", e2)
                false
            }
        }
    }
    
    /**
     * 华为/荣耀：尝试打开应用启动管理
     */
    private fun tryOpenHuaweiBatterySettings(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "打开华为应用启动管理")
            true
        } catch (e: Exception) {
            Log.w(TAG, "华为专用设置页面无法打开", e)
            false
        }
    }
    
    /**
     * OPPO/一加：尝试打开电池优化设置
     */
    private fun tryOpenOppoBatterySettings(context: Context): Boolean {
        return try {
            // 方法1：直接打开应用的电池优化详情
            val intent1 = Intent().apply {
                setClassName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.singlepage.PermissionSinglePageActivity"
                )
                putExtra("packageName", context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent1)
            Log.d(TAG, "打开OPPO电池优化设置（方法1）")
            true
        } catch (e1: Exception) {
            try {
                // 方法2：打开自启动管理（也包含电池优化）
                val intent2 = Intent().apply {
                    setClassName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent2)
                Log.d(TAG, "打开OPPO电池优化设置（方法2）")
                true
            } catch (e2: Exception) {
                try {
                    // 方法3：部分ColorOS/realme机型上的省电管理入口
                    val intent3 = Intent().apply {
                        setClassName(
                            "com.coloros.oppoguardelf",
                            "com.coloros.powermanager.fuelgaue.PowerConsumptionActivity"
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent3)
                    Log.d(TAG, "打开OPPO电池优化设置（方法3-oppoguardelf）")
                    true
                } catch (e3: Exception) {
                    Log.w(TAG, "OPPO专用设置页面无法打开", e3)
                    false
                }
            }
        }
    }
    
    /**
     * VIVO：尝试打开后台高耗电设置
     */
    private fun tryOpenVivoBatterySettings(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "打开VIVO后台高耗电设置")
            true
        } catch (e: Exception) {
            Log.w(TAG, "VIVO专用设置页面无法打开", e)
            false
        }
    }
    
    /**
     * 打开自启动管理设置页面（不同品牌有不同的路径）
     */
    fun openAutoStartSettings(context: Context) {
        try {
            val intent = Intent().apply {
                when {
                    // 小米
                    isXiaomiDevice() -> {
                        setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                    }
                    // 华为
                    isHuaweiDevice() -> {
                        setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
                    }
                    // OPPO
                    isOppoDevice() -> {
                        setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
                    }
                    // Vivo
                    isVivoDevice() -> {
                        setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
                    }
                    // 通用：跳转到应用详情页
                    else -> {
                        openAppSettings(context)
                        return
                    }
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开自启动设置失败，跳转到应用详情页", e)
            openAppSettings(context)
        }
    }
    
    /**
     * 检查设备品牌
     */
    private fun isXiaomiDevice(): Boolean {
        return Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
                Build.MANUFACTURER.equals("Redmi", ignoreCase = true)
    }
    
    private fun isHuaweiDevice(): Boolean {
        return Build.MANUFACTURER.equals("Huawei", ignoreCase = true) ||
                Build.MANUFACTURER.equals("Honor", ignoreCase = true)
    }
    
    private fun isOppoDevice(): Boolean {
        return Build.MANUFACTURER.equals("OPPO", ignoreCase = true) ||
                Build.MANUFACTURER.equals("OnePlus", ignoreCase = true) ||
                Build.MANUFACTURER.equals("Realme", ignoreCase = true)
    }
    
    private fun isVivoDevice(): Boolean {
        return Build.MANUFACTURER.equals("vivo", ignoreCase = true)
    }
}









