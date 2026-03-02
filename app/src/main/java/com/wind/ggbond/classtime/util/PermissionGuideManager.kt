package com.wind.ggbond.classtime.util

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.wind.ggbond.classtime.worker.ReminderWorker

/**
 * 权限引导步骤管理器
 * 用于管理课程提醒所需权限的检查和引导流程
 */
object PermissionGuideManager {
    
    /** SharedPreferences 文件名，用于持久化用户手动确认的权限步骤 */
    private const val PREFS_NAME = "permission_guide_prefs"
    
    /** SharedPreferences key 前缀，拼接步骤标识形成完整 key */
    private const val KEY_PREFIX_STEP_CONFIRMED = "step_confirmed_"
    
    /** 悬浮/横幅通知步骤的标识 */
    private const val STEP_KEY_HEADS_UP = "heads_up_notification"
    
    /** 自启动权限步骤的标识 */
    private const val STEP_KEY_AUTO_START = "auto_start"
    
    /** 后台弹出界面步骤的标识 */
    private const val STEP_KEY_BACKGROUND_POPUP = "background_popup"
    
    /**
     * 保存用户手动确认的权限步骤状态
     * @param context 上下文
     * @param stepKey 步骤标识（如 STEP_KEY_HEADS_UP）
     * @param confirmed 是否已确认完成
     */
    fun saveManualStepConfirmation(context: Context, stepKey: String, confirmed: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PREFIX_STEP_CONFIRMED + stepKey, confirmed)
            .apply()
    }
    
    /**
     * 读取用户手动确认的权限步骤状态
     * @param context 上下文
     * @param stepKey 步骤标识
     * @return 是否已手动确认完成
     */
    fun isManualStepConfirmed(context: Context, stepKey: String): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PREFIX_STEP_CONFIRMED + stepKey, false)
    }
    
    /**
     * 清除所有手动确认状态（用于重置权限引导流程）
     */
    fun clearAllManualConfirmations(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
    
    /**
     * 权限步骤
     */
    data class PermissionStep(
        val id: Int,
        val title: String,
        val description: String,
        val detailedExplanation: String,
        val isCompleted: Boolean,
        val isCritical: Boolean,  // 是否为关键步骤（必须完成）
        val checkPermission: (Context) -> Boolean,
        val openSettings: ((Context) -> Unit)? = null,
        val tips: List<String> = emptyList(),  // 操作提示
        val stepKey: String? = null  // 手动确认步骤的标识，用于持久化到 SharedPreferences
    )
    
    /**
     * 权限引导结果
     */
    data class GuideResult(
        val allCriticalStepsCompleted: Boolean,
        val completedSteps: Int,
        val totalSteps: Int,
        val steps: List<PermissionStep>
    ) {
        val progress: Float
            get() = if (totalSteps > 0) completedSteps.toFloat() / totalSteps else 0f
    }
    
    /**
     * 获取所有权限步骤
     */
    fun getAllSteps(context: Context): GuideResult {
        val steps = mutableListOf<PermissionStep>()
        
        // Step 1: 通知权限（所有版本都需要）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 需要运行时权限
            val notificationGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            steps.add(
                PermissionStep(
                    id = 1,
                    title = "步骤一：允许通知权限",
                    description = "允许应用发送课程提醒通知",
                    detailedExplanation = "这是最基础的权限。没有通知权限，应用将无法向您发送任何提醒。" +
                            "\n\n系统会弹出权限请求对话框，请点击「允许」。",
                    isCompleted = notificationGranted,
                    isCritical = true,
                    checkPermission = { ctx ->
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            ctx,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    },
                    openSettings = { ctx ->
                        ReminderPermissionHelper.openPermissionSettings(
                            ctx,
                            ReminderPermissionHelper.PermissionItem(
                                name = "通知权限",
                                description = "",
                                isGranted = false,
                                importance = ReminderPermissionHelper.PermissionImportance.CRITICAL,
                                intentAction = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                            )
                        )
                    },
                    tips = listOf(
                        "在弹出的对话框中点击「允许」",
                        "如果没有弹出，点击下方按钮前往设置"
                    )
                )
            )
        } else {
            val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
            val notificationsEnabled = notificationManager.areNotificationsEnabled()
            
            steps.add(
                PermissionStep(
                    id = 1,
                    title = "步骤一：开启通知权限",
                    description = "允许应用发送课程提醒通知",
                    detailedExplanation = "这是最基础的权限。没有通知权限，应用将无法向您发送任何提醒。" +
                            "\n\n请在系统设置中开启本应用的通知权限。",
                    isCompleted = notificationsEnabled,
                    isCritical = true,
                    checkPermission = { ctx ->
                        val nm = androidx.core.app.NotificationManagerCompat.from(ctx)
                        nm.areNotificationsEnabled()
                    },
                    openSettings = { ctx ->
                        try {
                            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                }
                            } else {
                                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.parse("package:${ctx.packageName}")
                                }
                            }
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(intent)
                        } catch (e: Exception) {
                            BackgroundPermissionHelper.openAppSettings(ctx)
                        }
                    },
                    tips = listOf(
                        "点击下方按钮进入系统设置",
                        "找到「通知」选项并开启",
                        "确保通知不被静音或隐藏"
                    )
                )
            )
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val headsUpStepId = steps.size + 1
            steps.add(
                PermissionStep(
                    id = headsUpStepId,
                    title = "步骤${getChineseNumber(headsUpStepId)}：开启悬浮/横幅通知",
                    description = "让课程提醒可以以横幅形式从屏幕顶部弹出，类似微信/QQ 消息",
                    detailedExplanation = "在系统通知设置中，需要为「课程提醒」这一通知渠道开启悬浮/横幅通知。" +
                            "\n\n不同系统的开关名称可能为「允许横幅通知」「悬浮通知」「在锁屏上显示横幅」等，请根据下方提示在系统设置中手动开启。",
                    isCompleted = isManualStepConfirmed(context, STEP_KEY_HEADS_UP),
                    isCritical = true,
                    checkPermission = { ctx -> isManualStepConfirmed(ctx, STEP_KEY_HEADS_UP) },
                    stepKey = STEP_KEY_HEADS_UP,
                    openSettings = { ctx ->
                        try {
                            // 优先尝试打开应用通知设置（更通用）
                            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            } else {
                                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.parse("package:${ctx.packageName}")
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            }
                            ctx.startActivity(intent)
                        } catch (e: Exception) {
                            // 如果上面的方式失败，尝试通知渠道设置
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    val channelIntent = android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                        putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, ReminderWorker.CHANNEL_ID)
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    ctx.startActivity(channelIntent)
                                } else {
                                    BackgroundPermissionHelper.openAppSettings(ctx)
                                }
                            } catch (e2: Exception) {
                                // 最后的备用方案
                                BackgroundPermissionHelper.openAppSettings(ctx)
                            }
                        }
                    },
                    tips = listOf(
                        "点击下方按钮，进入本应用的通知设置",
                        "找到「课程提醒」通知渠道并点击进入",
                        "ColorOS：开启「允许横幅通知」或「悬浮通知」",
                        "其他系统：开启「横幅」「弹出式」「在屏幕顶部显示」等选项"
                    )
                )
            )
        }
        
        // Step 2: 精确定时权限（Android 12+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val exactAlarmGranted = alarmManager.canScheduleExactAlarms()
            
            steps.add(
                PermissionStep(
                    id = 2,
                    title = "步骤二：允许准时提醒",
                    description = "确保通知能在精确的时间送达（如上课前10分钟）",
                    detailedExplanation = "这个权限不是真的设置\"闹钟\"，而是让应用能够准时发送通知。" +
                            "\n\n比如您的课程是9:00开始，设置提前10分钟提醒，那么通知会在8:50准时弹出。" +
                            "没有这个权限，通知可能会延迟几分钟甚至更久。" +
                            "\n\n在设置页面中，找到「闹钟和提醒」或「定时器和提醒」权限并开启。",
                    isCompleted = exactAlarmGranted,
                    isCritical = true,
                    checkPermission = { ctx ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val am = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                            am.canScheduleExactAlarms()
                        } else {
                            true
                        }
                    },
                    openSettings = { ctx ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            ReminderPermissionHelper.openPermissionSettings(
                                ctx,
                                ReminderPermissionHelper.PermissionItem(
                                    name = "准时提醒",
                                    description = "",
                                    isGranted = false,
                                    importance = ReminderPermissionHelper.PermissionImportance.CRITICAL,
                                    intentAction = android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                                )
                            )
                        }
                    },
                    tips = listOf(
                        "点击下方按钮进入设置",
                        "开启「允许设置闹钟和提醒」开关",
                        "注意：这不是真的闹钟，只是让通知准时弹出",
                        "某些手机显示为「精确闹钟」或「定时器和提醒」"
                    )
                )
            )
        }
        
        // Step 3: 全屏通知权限（Android 14+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {  // API 34 = Android 14
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val fullScreenIntentGranted = notificationManager.canUseFullScreenIntent()
            
            steps.add(
                PermissionStep(
                    id = 3,
                    title = "步骤三：允许全屏通知",
                    description = "允许应用在锁屏或使用其他应用时弹出全屏提醒",
                    detailedExplanation = "Android 14 新增的权限，可以让提醒以全屏形式显示，类似闹钟。" +
                            "\n\n这样即使在锁屏状态下，或者您正在使用其他应用时，课程提醒也能以全屏形式弹出，" +
                            "确保您不会错过重要的课程。\n\n" +
                            "在设置页面中，找到「全屏通知」或「全屏显示」权限并开启。",
                    isCompleted = fullScreenIntentGranted,
                    isCritical = true,
                    checkPermission = { ctx ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                            nm.canUseFullScreenIntent()
                        } else {
                            true
                        }
                    },
                    openSettings = { ctx ->
                        try {
                            // 跳转到全屏通知权限设置
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT
                            ).apply {
                                data = android.net.Uri.parse("package:${ctx.packageName}")
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            ctx.startActivity(intent)
                        } catch (e: Exception) {
                            // 降级方案：跳转到应用详情页
                            BackgroundPermissionHelper.openAppSettings(ctx)
                        }
                    },
                    tips = listOf(
                        "点击下方按钮进入设置",
                        "找到「全屏通知」或「允许全屏显示」",
                        "将开关打开",
                        "这个权限可以让提醒像闹钟一样全屏弹出"
                    )
                )
            )
        }
        
        // Step 4: 电池优化白名单（必需，用于防止后台被杀）
        val batteryStepId = steps.size + 1
        val batteryWhitelisted = BackgroundPermissionHelper.isIgnoringBatteryOptimizations(context)
        steps.add(
            PermissionStep(
                id = batteryStepId,
                title = "步骤${getChineseNumber(batteryStepId)}：加入电池优化白名单",
                description = "防止系统在后台关闭课程提醒，建议设置为“不过度限制”或“始终允许后台活动”",
                detailedExplanation = "不同系统的电池/省电策略名称可能不同，但目标是一致的：\n" +
                        "\n- 不要让系统在后台自动结束本应用" +
                        "\n- 不要对本应用应用“深度休眠”“严格省电”等策略" +
                        "\n\n请在接下来的设置页面中，将本应用的电池策略调整为“不过度限制”“允许后台活动”或类似选项。",
                isCompleted = batteryWhitelisted,
                isCritical = true,
                checkPermission = { ctx ->
                    BackgroundPermissionHelper.isIgnoringBatteryOptimizations(ctx)
                },
                openSettings = { ctx ->
                    BackgroundPermissionHelper.openBatteryOptimizationSettings(ctx)
                },
                tips = listOf(
                    "点击下方按钮进入电池/省电设置",
                    "在列表中找到本应用（课程表）",
                    "将电池策略设置为“不过度限制”或“允许后台活动”"
                )
            )
        )
        
        // Step 5 (或 Step 6): 自启动权限（必需，特定厂商）
        val manufacturer = Build.MANUFACTURER.lowercase()
        val nextStepId = steps.size + 1  // 动态计算步骤编号
        if (manufacturer.contains("xiaomi") || 
            manufacturer.contains("huawei") || 
            manufacturer.contains("oppo") || 
            manufacturer.contains("vivo")) {
            
            steps.add(
                PermissionStep(
                    id = nextStepId,
                    title = "步骤${getChineseNumber(nextStepId)}：开启自启动权限",
                    description = "允许应用在开机时自动启动，或在后台时自动运行",
                    detailedExplanation = getAutoStartGuide(manufacturer),
                    isCompleted = isManualStepConfirmed(context, STEP_KEY_AUTO_START),
                    isCritical = true,
                    checkPermission = { ctx -> isManualStepConfirmed(ctx, STEP_KEY_AUTO_START) },
                    stepKey = STEP_KEY_AUTO_START,
                    openSettings = { ctx ->
                        // 跳转到自启动管理设置
                        BackgroundPermissionHelper.openAutoStartSettings(ctx)
                    },
                    tips = getAutoStartTips(manufacturer)
                )
            )
        }
        
        // Step 6 (或更前): 后台弹出界面权限（必需，小米/OPPO）
        val popupStepId = steps.size + 1  // 动态计算步骤编号
        if (manufacturer.contains("xiaomi") || manufacturer.contains("oppo")) {
            steps.add(
                PermissionStep(
                    id = popupStepId,
                    title = "步骤${getChineseNumber(popupStepId)}：允许后台弹出界面",
                    description = "允许应用在您使用其他应用时弹出提醒窗口",
                    detailedExplanation = getPopupGuide(manufacturer),
                    isCompleted = isManualStepConfirmed(context, STEP_KEY_BACKGROUND_POPUP),
                    isCritical = true,
                    checkPermission = { ctx -> isManualStepConfirmed(ctx, STEP_KEY_BACKGROUND_POPUP) },
                    stepKey = STEP_KEY_BACKGROUND_POPUP,
                    openSettings = { ctx ->
                        try {
                            ReminderPermissionHelper.openPermissionSettings(
                                ctx,
                                ReminderPermissionHelper.PermissionItem(
                                    name = "后台弹出界面",
                                    description = "",
                                    isGranted = false,
                                    importance = ReminderPermissionHelper.PermissionImportance.IMPORTANT,
                                    intentAction = null,
                                    requiresManualCheck = true
                                )
                            )
                        } catch (e: Exception) {
                            BackgroundPermissionHelper.openAppSettings(ctx)
                        }
                    },
                    tips = getPopupTips(manufacturer)
                )
            )
        }
        
        // 统计完成情况
        val completedSteps = steps.count { it.isCompleted }
        val allCriticalCompleted = steps.filter { it.isCritical }.all { it.isCompleted }
        
        return GuideResult(
            allCriticalStepsCompleted = allCriticalCompleted,
            completedSteps = completedSteps,
            totalSteps = steps.size,
            steps = steps
        )
    }
    
    /**
     * 检查所有关键步骤是否完成
     */
    fun checkAllCriticalStepsCompleted(context: Context): Boolean {
        val result = getAllSteps(context)
        val criticalSteps = result.steps.filter { it.isCritical }
        
        // 调试日志
        android.util.Log.d("PermissionGuide", buildString {
            append("========== 权限检查 ==========")
            append("\nAndroid版本: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
            append("\n厂商: ${Build.MANUFACTURER}")
            append("\n总步骤数: ${result.totalSteps}")
            append("\n已完成步骤: ${result.completedSteps}")
            append("\n关键步骤数: ${criticalSteps.size}")
            criticalSteps.forEach { step ->
                val status = if (step.isCompleted) "已完成" else "未完成"
                append("\n- ${step.title}: $status")
            }
            val finalStatus = if (result.allCriticalStepsCompleted) "所有关键步骤已完成" else "有关键步骤未完成"
            append("\n最终结果: $finalStatus")
            append("\n============================")
        })
        
        return result.allCriticalStepsCompleted
    }
    
    /**
     * 获取自启动权限引导文本
     */
    private fun getAutoStartGuide(manufacturer: String): String {
        return when {
            manufacturer.contains("xiaomi") -> {
                "小米/Redmi手机的自启动权限说明：\n\n" +
                "由于MIUI系统的严格后台管理，必须开启自启动权限。这样即使手机重启，" +
                "应用也能自动启动并继续为您提供提醒服务。\n\n" +
                "点击下方按钮将自动跳转到自启动管理页面。"
            }
            manufacturer.contains("huawei") -> {
                "华为/荣耀手机的自启动权限说明：\n\n" +
                "EMUI/HarmonyOS系统对后台应用管理较为严格，必须开启自启动权限。\n\n" +
                "点击下方按钮将跳转到应用启动管理页面，将本应用设为「手动管理」，" +
                "并开启「自动启动」「后台活动」。"
            }
            manufacturer.contains("oppo") -> {
                "OPPO手机的自启动权限说明：\n\n" +
                "ColorOS系统为了优化电池使用，会限制后台应用。必须开启自启动权限。\n\n" +
                "点击下方按钮将跳转到自启动管理页面。"
            }
            manufacturer.contains("vivo") -> {
                "VIVO手机的自启动权限说明：\n\n" +
                "OriginOS/FuntouchOS系统对后台应用有严格控制。必须开启自启动权限。\n\n" +
                "点击下方按钮将跳转到权限管理页面。"
            }
            else -> {
                "自启动权限可以确保应用在手机重启后自动启动，" +
                "持续为您提供提醒服务。"
            }
        }
    }
    
    /**
     * 获取自启动权限操作提示
     */
    private fun getAutoStartTips(manufacturer: String): List<String> {
        return when {
            manufacturer.contains("xiaomi") -> listOf(
                "点击下方按钮进入自启动管理",
                "找到「课程表」应用",
                "将开关打开（变成蓝色）",
                "完成后点击「我已完成此步骤」按钮"
            )
            manufacturer.contains("huawei") -> listOf(
                "点击下方按钮进入应用启动管理",
                "找到「课程表」，关闭「自动管理」",
                "手动开启「自动启动」「后台活动」",
                "完成后点击「我已完成此步骤」按钮"
            )
            manufacturer.contains("oppo") -> listOf(
                "点击下方按钮进入自启动管理",
                "找到「课程表」应用并允许自启动",
                "完成后点击「我已完成此步骤」按钮"
            )
            manufacturer.contains("vivo") -> listOf(
                "点击下方按钮进入权限管理",
                "找到「课程表」并开启自启动",
                "完成后点击「我已完成此步骤」按钮"
            )
            else -> listOf(
                "前往系统设置",
                "找到应用管理或权限管理",
                "开启自启动相关权限",
                "完成后点击「我已完成此步骤」按钮"
            )
        }
    }
    
    /**
     * 获取后台弹出界面引导文本
     */
    private fun getPopupGuide(manufacturer: String): String {
        return when {
            manufacturer.contains("xiaomi") -> {
                "小米/Redmi手机的后台弹出权限说明：\n\n" +
                "这个权限允许应用在您使用其他应用时弹出提醒通知。" +
                "如果不开启此权限，提醒可能只会静默显示在通知栏，容易被忽略。\n\n" +
                "点击下方按钮将跳转到权限管理页面。"
            }
            manufacturer.contains("oppo") -> {
                "OPPO手机的后台弹出权限说明：\n\n" +
                "这个权限可以让提醒更加醒目，不易错过。\n\n" +
                "点击下方按钮将跳转到权限管理页面。"
            }
            else -> {
                "后台弹出界面权限可以让提醒更加醒目，确保您不会错过重要的课程提醒。"
            }
        }
    }
    
    /**
     * 获取后台弹出操作提示
     */
    private fun getPopupTips(manufacturer: String): List<String> {
        return when {
            manufacturer.contains("xiaomi") -> listOf(
                "点击下方按钮进入应用权限",
                "找到「后台弹出界面」权限",
                "将开关打开",
                "完成后点击「我已完成此步骤」按钮"
            )
            manufacturer.contains("oppo") -> listOf(
                "点击下方按钮进入权限管理",
                "找到「后台弹出」或「显示悬浮窗」",
                "允许权限",
                "完成后点击「我已完成此步骤」按钮"
            )
            else -> listOf(
                "前往应用权限设置",
                "找到并允许后台弹出相关权限",
                "完成后点击「我已完成此步骤」按钮"
            )
        }
    }
    
    /**
     * 将数字转换为中文数字（用于步骤标题）
     */
    private fun getChineseNumber(number: Int): String {
        return when (number) {
            1 -> "一"
            2 -> "二"
            3 -> "三"
            4 -> "四"
            5 -> "五"
            6 -> "六"
            7 -> "七"
            8 -> "八"
            9 -> "九"
            else -> number.toString()
        }
    }
}



