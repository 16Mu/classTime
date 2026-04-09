package com.wind.ggbond.classtime.util

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.wind.ggbond.classtime.worker.ReminderWorker

object PermissionGuideManager {

    private const val PREFS_NAME = "permission_guide_prefs"
    private const val KEY_PREFIX_STEP_CONFIRMED = "step_confirmed_"
    private const val STEP_KEY_HEADS_UP = "heads_up_notification"
    private const val STEP_KEY_AUTO_START = "auto_start"
    private const val STEP_KEY_BACKGROUND_POPUP = "background_popup"

    private val CHINESE_NUMBERS = arrayOf("一", "二", "三", "四", "五", "六", "七", "八", "九")

    fun saveManualStepConfirmation(context: Context, stepKey: String, confirmed: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PREFIX_STEP_CONFIRMED + stepKey, confirmed)
            .apply()
    }

    fun isManualStepConfirmed(context: Context, stepKey: String): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PREFIX_STEP_CONFIRMED + stepKey, false)
    }

    fun clearAllManualConfirmations(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    data class PermissionStep(
        val id: Int,
        val title: String,
        val description: String,
        val detailedExplanation: String,
        val isCompleted: Boolean,
        val isCritical: Boolean,
        val checkPermission: (Context) -> Boolean,
        val openSettings: ((Context) -> Unit)? = null,
        val tips: List<String> = emptyList(),
        val stepKey: String? = null
    )

    data class GuideResult(
        val allCriticalStepsCompleted: Boolean,
        val completedSteps: Int,
        val totalSteps: Int,
        val steps: List<PermissionStep>
    ) {
        val progress: Float
            get() = if (totalSteps > 0) completedSteps.toFloat() / totalSteps else 0f
    }

    fun getAllSteps(context: Context): GuideResult {
        val steps = mutableListOf<PermissionStep>()
        val manufacturer = Build.MANUFACTURER.lowercase()

        addNotificationStep(context, steps)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) addHeadsUpStep(context, steps)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) addExactAlarmStep(context, steps)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) addFullScreenIntentStep(context, steps)
        addBatteryOptimizationStep(context, steps)
        if (isAutoStartRequired(manufacturer)) addAutoStartStep(context, manufacturer, steps)
        if (isPopupRequired(manufacturer)) addBackgroundPopupStep(context, manufacturer, steps)

        val completedSteps = steps.count { it.isCompleted }
        val allCriticalCompleted = steps.filter { it.isCritical }.all { it.isCompleted }

        return GuideResult(allCriticalCompleted, completedSteps, steps.size, steps)
    }

    private fun addNotificationStep(context: Context, steps: MutableList<PermissionStep>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            steps.add(createNotificationStepTIRAMISU(granted))
        } else {
            val enabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
            steps.add(createNotificationStepLegacy(enabled))
        }
    }

    private fun createNotificationStepTIRAMISU(granted: Boolean): PermissionStep {
        return PermissionStep(
            id = 1, title = "步骤一：允许通知权限",
            description = "允许应用发送课程提醒通知",
            detailedExplanation = "这是最基础的权限。没有通知权限，应用将无法向您发送任何提醒。\n\n系统会弹出权限请求对话框，请点击「允许」。",
            isCompleted = granted, isCritical = true,
            checkPermission = { ctx -> checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) },
            openSettings = { ctx ->
                ReminderPermissionHelper.openPermissionSettings(ctx,
                    ReminderPermissionHelper.PermissionItem(
                        name = "通知权限", description = "", isGranted = false,
                        importance = ReminderPermissionHelper.PermissionImportance.CRITICAL,
                        intentAction = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    ))
            },
            tips = listOf("在弹出的对话框中点击「允许」", "如果没有弹出，点击下方按钮前往设置")
        )
    }

    private fun createNotificationStepLegacy(enabled: Boolean): PermissionStep {
        return PermissionStep(
            id = 1, title = "步骤一：开启通知权限",
            description = "允许应用发送课程提醒通知",
            detailedExplanation = "这是最基础的权限。没有通知权限，应用将无法向您发送任何提醒。\n\n请在系统设置中开启本应用的通知权限。",
            isCompleted = enabled, isCritical = true,
            checkPermission = { ctx -> androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled() },
            openSettings = { ctx -> openAppNotificationSettings(ctx) },
            tips = listOf("点击下方按钮进入系统设置", "找到「通知」选项并开启", "确保通知不被静音或隐藏")
        )
    }

    private fun addHeadsUpStep(context: Context, steps: MutableList<PermissionStep>) {
        val stepId = steps.size + 1
        steps.add(PermissionStep(
            id = stepId,
            title = "步骤${cnNum(stepId)}：开启悬浮/横幅通知",
            description = "让课程提醒可以以横幅形式从屏幕顶部弹出，类似微信/QQ 消息",
            detailedExplanation = "在系统通知设置中，需要为「课程提醒」这一通知渠道开启悬浮/横幅通知。\n\n不同系统的开关名称可能为「允许横幅通知」「悬浮通知」「在锁屏上显示横幅」等，请根据下方提示在系统设置中手动开启。",
            isCompleted = isManualStepConfirmed(context, STEP_KEY_HEADS_UP),
            isCritical = true,
            checkPermission = { ctx -> isManualStepConfirmed(ctx, STEP_KEY_HEADS_UP) },
            stepKey = STEP_KEY_HEADS_UP,
            openSettings = { ctx -> openAppNotificationSettings(ctx) },
            tips = listOf(
                "点击下方按钮，进入本应用的通知设置",
                "找到「课程提醒」通知渠道并点击进入",
                "ColorOS：开启「允许横幅通知」或「悬浮通知」",
                "其他系统：开启「横幅」「弹出式」「在屏幕顶部显示」等选项"
            )
        ))
    }

    private fun addExactAlarmStep(context: Context, steps: MutableList<PermissionStep>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        steps.add(PermissionStep(
            id = 2, title = "步骤二：允许准时提醒",
            description = "确保通知能在精确的时间送达（如上课前10分钟）",
            detailedExplanation = "这个权限不是真的设置\"闹钟\"，而是让应用能够准时发送通知。\n\n比如您的课程是9:00开始，设置提前10分钟提醒，那么通知会在8:50准时弹出。没有这个权限，通知可能会延迟几分钟甚至更久。\n\n在设置页面中，找到「闹钟和提醒」或「定时器和提醒」权限并开启。",
            isCompleted = alarmManager.canScheduleExactAlarms(),
            isCritical = true,
            checkPermission = { ctx ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).canScheduleExactAlarms()
                } else true
            },
            openSettings = { ctx ->
                ReminderPermissionHelper.openPermissionSettings(ctx,
                    ReminderPermissionHelper.PermissionItem(
                        name = "准时提醒", description = "", isGranted = false,
                        importance = ReminderPermissionHelper.PermissionImportance.CRITICAL,
                        intentAction = android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    ))
            },
            tips = listOf("点击下方按钮进入设置", "开启「允许设置闹钟和提醒」开关",
                "注意：这不是真的闹钟，只是让通知准时弹出", "某些手机显示为「精确闹钟」或「定时器和提醒」")
        ))
    }

    private fun addFullScreenIntentStep(context: Context, steps: MutableList<PermissionStep>) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        steps.add(PermissionStep(
            id = 3, title = "步骤三：允许全屏通知",
            description = "允许应用在锁屏或使用其他应用时弹出全屏提醒",
            detailedExplanation = "Android 14 新增的权限，可以让提醒以全屏形式显示，类似闹钟。\n\n这样即使在锁屏状态下，或者您正在使用其他应用时，课程提醒也能以全屏形式弹出，确保您不会错过重要的课程。\n\n在设置页面中，找到「全屏通知」或「全屏显示」权限并开启。",
            isCompleted = nm.canUseFullScreenIntent(),
            isCritical = true,
            checkPermission = { ctx ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).canUseFullScreenIntent()
                } else true
            },
            openSettings = { ctx ->
                try {
                    ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                        data = android.net.Uri.parse("package:${ctx.packageName}")
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                } catch (_: Exception) {
                    BackgroundPermissionHelper.openAppSettings(ctx)
                }
            },
            tips = listOf("点击下方按钮进入设置", "找到「全屏通知」或「允许全屏显示」", "将开关打开", "这个权限可以让提醒像闹钟一样全屏弹出")
        ))
    }

    private fun addBatteryOptimizationStep(context: Context, steps: MutableList<PermissionStep>) {
        val stepId = steps.size + 1
        steps.add(PermissionStep(
            id = stepId, title = "步骤${cnNum(stepId)}：加入电池优化白名单",
            description = "防止系统在后台关闭课程提醒，建议设置为「不过度限制」或「始终允许后台活动」",
            detailedExplanation = "不同系统的电池/省电策略名称可能不同，但目标是一致的：\n\n- 不要让系统在后台自动结束本应用\n- 不要对本应用应用「深度休眠」「严格省电」等策略\n\n请在接下来的设置页面中，将本应用的电池策略调整为「不过度限制」「允许后台活动」或类似选项。",
            isCompleted = BackgroundPermissionHelper.isIgnoringBatteryOptimizations(context),
            isCritical = true,
            checkPermission = { ctx -> BackgroundPermissionHelper.isIgnoringBatteryOptimizations(ctx) },
            openSettings = { ctx -> BackgroundPermissionHelper.openBatteryOptimizationSettings(ctx) },
            tips = listOf("点击下方按钮进入电池/省电设置", "在列表中找到本应用（课程表）", "将电池策略设置为「不过度限制」或「允许后台活动」")
        ))
    }

    private fun addAutoStartStep(context: Context, manufacturer: String, steps: MutableList<PermissionStep>) {
        val stepId = steps.size + 1
        val guide = AutoStartGuide.getGuide(manufacturer)
        steps.add(PermissionStep(
            id = stepId, title = "步骤${cnNum(stepId)}：开启自启动权限",
            description = "允许应用在开机时自动启动，或在后台时自动运行",
            detailedExplanation = guide.explanation,
            isCompleted = isManualStepConfirmed(context, STEP_KEY_AUTO_START),
            isCritical = true,
            checkPermission = { ctx -> isManualStepConfirmed(ctx, STEP_KEY_AUTO_START) },
            stepKey = STEP_KEY_AUTO_START,
            openSettings = { ctx -> BackgroundPermissionHelper.openAutoStartSettings(ctx) },
            tips = guide.tips
        ))
    }

    private fun addBackgroundPopupStep(context: Context, manufacturer: String, steps: MutableList<PermissionStep>) {
        val stepId = steps.size + 1
        val guide = PopupGuide.getGuide(manufacturer)
        steps.add(PermissionStep(
            id = stepId, title = "步骤${cnNum(stepId)}：允许后台弹出界面",
            description = "允许应用在您使用其他应用时弹出提醒窗口",
            detailedExplanation = guide.explanation,
            isCompleted = isManualStepConfirmed(context, STEP_KEY_BACKGROUND_POPUP),
            isCritical = true,
            checkPermission = { ctx -> isManualStepConfirmed(ctx, STEP_KEY_BACKGROUND_POPUP) },
            stepKey = STEP_KEY_BACKGROUND_POPUP,
            openSettings = { ctx ->
                try {
                    ReminderPermissionHelper.openPermissionSettings(ctx,
                        ReminderPermissionHelper.PermissionItem(
                            name = "后台弹出界面", description = "", isGranted = false,
                            importance = ReminderPermissionHelper.PermissionImportance.IMPORTANT,
                            intentAction = null, requiresManualCheck = true
                        ))
                } catch (_: Exception) {
                    BackgroundPermissionHelper.openAppSettings(ctx)
                }
            },
            tips = guide.tips
        ))
    }

    fun checkAllCriticalStepsCompleted(context: Context): Boolean {
        val result = getAllSteps(context)
        AppLogger.d("PermissionGuide", buildString {
            append("========== 权限检查 ==========")
            append("\nAndroid版本: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
            append("\n厂商: ${Build.MANUFACTURER}")
            append("\n总步骤数: ${result.totalSteps}")
            append("\n已完成步骤: ${result.completedSteps}")
            result.steps.filter { it.isCritical }.forEach { step ->
                append("\n- ${step.title}: ${if (step.isCompleted) "已完成" else "未完成"}")
            }
            append("\n最终结果: ${if (result.allCriticalStepsCompleted) "所有关键步骤已完成" else "有关键步骤未完成"}")
            append("\n============================")
        })
        return result.allCriticalStepsCompleted
    }

    private fun cnNum(n: Int): String = CHINESE_NUMBERS.getOrNull(n - 1) ?: n.toString()

    private fun checkSelfPermission(ctx: Context, permission: String): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(ctx, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun openAppNotificationSettings(ctx: Context) {
        try {
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
        } catch (_: Exception) {
            BackgroundPermissionHelper.openAppSettings(ctx)
        }
    }

    private fun isAutoStartRequired(mfr: String): Boolean =
        mfr.let { it.contains("xiaomi") || it.contains("huawei") || it.contains("oppo") || it.contains("vivo") }

    private fun isPopupRequired(mfr: String): Boolean =
        mfr.contains("xiaomi") || mfr.contains("oppo")

    private data class ManufacturerGuide(val explanation: String, val tips: List<String>)

    private object AutoStartGuide {
        private val guides = mapOf(
            "xiaomi" to ManufacturerGuide(
                "小米/Redmi手机的自启动权限说明：\n\n由于MIUI系统的严格后台管理，必须开启自启动权限。这样即使手机重启，应用也能自动启动并继续为您提供提醒服务。\n\n点击下方按钮将自动跳转到自启动管理页面。",
                listOf("点击下方按钮进入自启动管理", "找到「课程表」应用", "将开关打开（变成蓝色）", "完成后点击「我已完成此步骤」按钮")
            ),
            "huawei" to ManufacturerGuide(
                "华为/荣耀手机的自启动权限说明：\n\nEMUI/HarmonyOS系统对后台应用管理较为严格，必须开启自启动权限。\n\n点击下方按钮将跳转到应用启动管理页面，将本应用设为「手动管理」，并开启「自动启动」「后台活动」。",
                listOf("点击下方按钮进入应用启动管理", "找到「课程表」，关闭「自动管理」", "手动开启「自动启动」「后台活动」", "完成后点击「我已完成此步骤」按钮")
            ),
            "oppo" to ManufacturerGuide(
                "OPPO手机的自启动权限说明：\n\nColorOS系统为了优化电池使用，会限制后台应用。必须开启自启动权限。\n\n点击下方按钮将跳转到自启动管理页面。",
                listOf("点击下方按钮进入自启动管理", "找到「课程表」应用并允许自启动", "完成后点击「我已完成此步骤」按钮")
            ),
            "vivo" to ManufacturerGuide(
                "VIVO手机的自启动权限说明：\n\nOriginOS/FuntouchOS系统对后台应用有严格控制。必须开启自启动权限。\n\n点击下方按钮将跳转到权限管理页面。",
                listOf("点击下方按钮进入权限管理", "找到「课程表」并开启自启动", "完成后点击「我已完成此步骤」按钮")
            )
        )
        private val defaultGuide = ManufacturerGuide(
            "自启动权限可以确保应用在手机重启后自动启动，持续为您提供提醒服务。",
            listOf("前往系统设置", "找到应用管理或权限管理", "开启自启动相关权限", "完成后点击「我已完成此步骤」按钮")
        )

        fun getGuide(manufacturer: String): ManufacturerGuide =
            guides.entries.find { manufacturer.contains(it.key) }?.value ?: defaultGuide
    }

    private object PopupGuide {
        private val guides = mapOf(
            "xiaomi" to ManufacturerGuide(
                "小米/Redmi手机的后台弹出权限说明：\n\n这个权限允许应用在您使用其他应用时弹出提醒通知。如果不开启此权限，提醒可能只会静默显示在通知栏，容易被忽略。\n\n点击下方按钮将跳转到权限管理页面。",
                listOf("点击下方按钮进入应用权限", "找到「后台弹出界面」权限", "将开关打开", "完成后点击「我已完成此步骤」按钮")
            ),
            "oppo" to ManufacturerGuide(
                "OPPO手机的后台弹出权限说明：\n\n这个权限可以让提醒更加醒目，不易错过。\n\n点击下方按钮将跳转到权限管理页面。",
                listOf("点击下方按钮进入权限管理", "找到「后台弹出」或「显示悬浮窗」", "允许权限", "完成后点击「我已完成此步骤」按钮")
            )
        )
        private val defaultGuide = ManufacturerGuide(
            "后台弹出界面权限可以让提醒更加醒目，确保您不会错过重要的课程提醒。",
            listOf("前往应用权限设置", "找到并允许后台弹出相关权限", "完成后点击「我已完成此步骤」按钮")
        )

        fun getGuide(manufacturer: String): ManufacturerGuide =
            guides.entries.find { manufacturer.contains(it.key) }?.value ?: defaultGuide
    }
}
