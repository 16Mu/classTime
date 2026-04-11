package com.wind.ggbond.classtime.ui.screen.settings

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.wind.ggbond.classtime.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.data.repository.SettingsRepository
import com.wind.ggbond.classtime.service.contract.IDataExporter
import com.wind.ggbond.classtime.service.contract.IAlarmScheduler
import com.wind.ggbond.classtime.service.KeepAliveService
import com.wind.ggbond.classtime.util.BackgroundPermissionHelper
import com.wind.ggbond.classtime.util.ReminderDiagnostic
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import com.wind.ggbond.classtime.util.MonetColorPalette
import com.wind.ggbond.classtime.util.CourseColorPalette
import com.wind.ggbond.classtime.ui.theme.BackgroundThemeManager
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val courseRepository: CourseRepository,
    private val scheduleRepository: ScheduleRepository,
    private val settingsRepository: SettingsRepository,
    private val exportService: IDataExporter,
    private val importService: com.wind.ggbond.classtime.service.ImportService,
    private val reminderScheduler: IAlarmScheduler,
    private val backgroundThemeManager: BackgroundThemeManager
) : ViewModel() {

    private val _messageEvent = MutableSharedFlow<String>()
    val messageEvent = _messageEvent.asSharedFlow()

    companion object {
        private const val TAG = "SettingsVM"
        private const val PREFS_NAME = "app_settings"
        private val OS_LABELS = mapOf(
            "coloros" to "ColorOS (OPPO / realme)", "hyperos" to "HyperOS / MIUI (小米)",
            "miui" to "MIUI (小米)", "harmonyos" to "HarmonyOS (华为)",
            "originos" to "OriginOS / FuntouchOS (vivo)", "oneui" to "OneUI (三星)", "flyme" to "Flyme (魅族)"
        )
        private val SETTING_NAMES = mapOf(
            "coloros" to "允许横幅通知", "hyperos" to "悬浮通知/横幅通知",
            "miui" to "悬浮通知/横幅通知", "harmonyos" to "横幅通知",
            "originos" to "悬浮通知/横幅通知", "oneui" to "简要弹出通知/浮动通知",
            "flyme" to "悬浮通知通知"
        )
    }

    private fun <T> settingFlow(initial: T) = MutableStateFlow(initial)
    private inline fun <T> settingUpdate(flow: MutableStateFlow<T>, value: T, crossinline setter: suspend (T) -> Unit) {
        viewModelScope.launch { setter(value); flow.value = value }
    }

    val reminderEnabled = settingFlow(false); val defaultReminderMinutes = settingFlow(10)
    val showClearDataDialog = settingFlow(false); val compactModeEnabled = settingFlow(false)
    val headsUpNotificationEnabled = settingFlow(true); val showWeekendEnabled = settingFlow(true)
    val glassEffectEnabled = settingFlow(true); val monetEnabled = settingFlow(false)
    val desktopModeEnabled = settingFlow(false)
    val courseColorSaturation = settingFlow(1)
    val backgroundStatus = settingFlow(BackgroundPermissionHelper.checkBackgroundStatus(context))
    val keepAliveEnabled = settingFlow(KeepAliveService.isRunning(context))
    val disclaimerAccepted = settingFlow<Boolean?>(null); val onboardingCompleted = settingFlow<Boolean?>(null)
    val showExportDialog = settingFlow(false); val exportResult = settingFlow<IDataExporter.ExportResult?>(null)
    val showNotificationPermissionDialog = settingFlow(false); val showImportDialog = settingFlow(false)
    val showDisclaimerDialog = settingFlow(false); val showPermissionGuideDialog = settingFlow(false)
    val showReminderTestDialog = settingFlow(false)

    fun observeCourseColors(saturationLevel: MonetColorPalette.SaturationLevel, isDarkMode: Boolean = false) =
        backgroundThemeManager.observeCourseColors(saturationLevel, isDarkMode)

    val monetCourseColorsEnabled: StateFlow<Boolean> = settingsRepository.observeMonetCourseColorsEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val monetColorPreview: StateFlow<List<String>> = combine(monetCourseColorsEnabled, courseColorSaturation,
        backgroundThemeManager.observeCourseColors(courseColorSaturation.value.toSaturationLevel()))
    { enabled, saturation, _ ->
        if (enabled) MonetColorPalette.generatePalette(backgroundThemeManager.getCurrentSeedColor(), saturation.toSaturationLevel(), false)
        else CourseColorPalette.getAllColors()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CourseColorPalette.getAllColors())

    init {
        loadSettings()
        observeSetting(settingsRepository::observeCompactModeEnabled, compactModeEnabled)
        observeSetting(settingsRepository::observeShowWeekendEnabled, showWeekendEnabled)
        observeSetting(settingsRepository::observeMonetCourseColorsEnabled, monetEnabled)
        observeSetting(settingsRepository::observeCourseColorSaturation, courseColorSaturation)
        observeSetting(settingsRepository::observeGlassEffectEnabled, glassEffectEnabled)
        observeSetting(settingsRepository::observeDesktopModeEnabled, desktopModeEnabled)
    }

    private fun <T> observeSetting(observer: () -> kotlinx.coroutines.flow.Flow<T>, flow: MutableStateFlow<T>) {
        viewModelScope.launch { observer().distinctUntilChanged().collect { flow.value = it } }
    }

    private fun Int.toSaturationLevel() = when (this) { 0 -> MonetColorPalette.SaturationLevel.SOFT; 2 -> MonetColorPalette.SaturationLevel.VIBRANT; else -> MonetColorPalette.SaturationLevel.STANDARD }

    private fun loadSettings() {
        viewModelScope.launch {
            reminderEnabled.value = settingsRepository.isReminderEnabled()
            defaultReminderMinutes.value = settingsRepository.getDefaultReminderMinutes()
            compactModeEnabled.value = settingsRepository.isCompactModeEnabled()
            val headsUp = settingsRepository.isHeadsUpNotificationEnabled()
            headsUpNotificationEnabled.value = headsUp
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("heads_up_notification_enabled", headsUp).apply()
            disclaimerAccepted.value = settingsRepository.isDisclaimerAccepted()
            onboardingCompleted.value = settingsRepository.isOnboardingCompleted()
            showWeekendEnabled.value = settingsRepository.isShowWeekendEnabled()
            glassEffectEnabled.value = settingsRepository.isGlassEffectEnabled()
            desktopModeEnabled.value = settingsRepository.isDesktopModeEnabled()
            monetEnabled.value = settingsRepository.isMonetCourseColorsEnabled()
            courseColorSaturation.value = settingsRepository.getCourseColorSaturation()
        }
    }

    fun updateReminderEnabled(enabled: Boolean) {
        viewModelScope.launch { applyReminderEnabledChange(enabled) }
        if (enabled) viewModelScope.launch {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) showNotificationPermissionDialog.value = true
        }
    }

    fun hideNotificationPermissionDialog() { showNotificationPermissionDialog.value = false }
    fun hidePermissionGuideDialog() { showPermissionGuideDialog.value = false }
    fun onPermissionGuideCompleted() { showPermissionGuideDialog.value = false; viewModelScope.launch { applyReminderEnabledChange(true) } }

    private suspend fun applyReminderEnabledChange(enabled: Boolean) {
        settingsRepository.setReminderEnabled(enabled); reminderEnabled.value = enabled
        val schedule = scheduleRepository.getCurrentSchedule() ?: return
        if (enabled) try {
            val minutes = settingsRepository.getDefaultReminderMinutes()
            val count = courseRepository.enableAllCoursesReminder(schedule.id, minutes)
            reminderScheduler.scheduleAllCourseReminders(schedule.id)
            toast("已为 $count 门课程开启提醒")
        } catch (e: Exception) { toast("开启课程提醒失败：${e.message}") }
        else try {
            val count = courseRepository.disableAllCoursesReminder(schedule.id)
            reminderScheduler.cancelAllCourseReminders(schedule.id)
            toast("已关闭所有课程提醒")
        } catch (e: Exception) { toast("关闭课程提醒失败：${e.message}") }
    }

    fun updateDefaultReminderMinutes(minutes: Int) { settingUpdate(defaultReminderMinutes, minutes) { settingsRepository.setDefaultReminderMinutes(it) } }
    fun updateHeadsUpNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHeadsUpNotificationEnabled(enabled)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("heads_up_notification_enabled", enabled).apply()
            headsUpNotificationEnabled.value = enabled
        }
    }

    suspend fun isHeadsUpNotificationEnabled(): Boolean = settingsRepository.isHeadsUpNotificationEnabled()

    fun showExportDialog() { showExportDialog.value = true }
    fun hideExportDialog() { showExportDialog.value = false }

    fun exportSchedule(format: IDataExporter.ExportFormat) {
        viewModelScope.launch {
            scheduleRepository.getCurrentSchedule()?.let {
                val result = exportService.export(it.id, format); exportResult.value = result
                toast(if (result.success) "导出成功！文件已保存" else "导出失败: ${result.errorMessage}")
            }
        }
    }

    fun shareExportedFile(format: IDataExporter.ExportFormat) {
        viewModelScope.launch {
            scheduleRepository.getCurrentSchedule()?.let {
                val result = exportService.export(it.id, format)
                if (result.success && result.filePath != null) exportService.shareFile(result.filePath, getMimeType(format))
            }
        }
    }

    fun showImportDialog() { showImportDialog.value = true }
    fun hideImportDialog() { showImportDialog.value = false }
    fun clearExportResult() { exportResult.value = null }
    fun shareFile(filePath: String) { exportService.shareFile(filePath) }

    fun importSchedule(uri: Uri) {
        viewModelScope.launch {
            try {
                val schedule = scheduleRepository.getCurrentSchedule()
                if (schedule == null) { toast("导入失败：未找到当前课表，请先创建课表"); return@launch }
                val result = importService.importFromUri(uri, schedule.id)
                toast(if (result.success) "导入成功！共导入 ${result.importedCount} 门课程" else "导入失败: ${result.errorMessage}")
            } catch (e: Exception) { toast("导入失败: ${e.message}") }
        }
    }

    private fun getMimeType(format: IDataExporter.ExportFormat): String = when (format) {
        IDataExporter.ExportFormat.JSON -> "application/json"; IDataExporter.ExportFormat.ICS -> "text/calendar"
        IDataExporter.ExportFormat.CSV -> "text/csv"; IDataExporter.ExportFormat.TXT -> "text/plain"
        IDataExporter.ExportFormat.HTML -> "text/html"
    }

    fun showClearDataDialog() { showClearDataDialog.value = true }
    fun hideClearDataDialog() { showClearDataDialog.value = false }

    fun clearAllData() {
        viewModelScope.launch { scheduleRepository.getCurrentSchedule()?.let { courseRepository.deleteAllCoursesBySchedule(it.id) } }
    }

    fun updateCompactModeEnabled(enabled: Boolean) { settingUpdate(compactModeEnabled, enabled) { settingsRepository.setCompactModeEnabled(it) } }
    fun updateShowWeekendEnabled(enabled: Boolean) { settingUpdate(showWeekendEnabled, enabled) { settingsRepository.setShowWeekendEnabled(it) } }
    fun updateGlassEffectEnabled(enabled: Boolean) { settingUpdate(glassEffectEnabled, enabled) { settingsRepository.setGlassEffectEnabled(it) } }
    fun updateMonetEnabled(enabled: Boolean) { settingUpdate(monetEnabled, enabled) { settingsRepository.setMonetCourseColorsEnabled(it) } }
    fun updateDesktopModeEnabled(enabled: Boolean) { settingUpdate(desktopModeEnabled, enabled) { settingsRepository.setDesktopModeEnabled(it) } }
    fun updateCourseColorSaturation(saturation: Int) { settingUpdate(courseColorSaturation, saturation.coerceIn(0, 2)) { settingsRepository.setCourseColorSaturation(it) } }

    private fun hasNotificationPermission(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else NotificationManagerCompat.from(context).areNotificationsEnabled()

    private val phoneBrand: String get() = Build.MANUFACTURER.lowercase()

    private val osName: String get() {
        val display = Build.DISPLAY.lowercase(); val brand = phoneBrand
        return when {
            display.contains("coloros") -> "coloros"; display.contains("hyperos") -> "hyperos"
            display.contains("miui") -> "miui"; display.contains("harmony") -> "harmonyos"
            display.contains("originos") -> "originos"; display.contains("flyme") -> "flyme"
            display.contains("oneui") || display.contains("one ui") -> "oneui"
            brand.contains("oppo") || brand.contains("realme") -> "coloros"
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") -> "hyperos"
            brand.contains("huawei") || brand.contains("honor") -> "harmonyos"
            brand.contains("vivo") || brand.contains("iqoo") -> "originos"
            brand.contains("samsung") -> "oneui"; else -> "android"
        }
    }

    fun sendHeadsUpNotificationTest() {
        viewModelScope.launch {
            try {
                if (!hasNotificationPermission()) { toast("未授予通知权限"); return@launch }
                sendUnifiedNotification()
            } catch (e: Exception) { toast("发送失败: ${e.message}") }
        }
    }

    private fun sendUnifiedNotification() {
        val os = osName; val osLabel = OS_LABELS[os] ?: "原生 Android"
        val settingName = SETTING_NAMES[os] ?: "悬浮通知/横幅通知"

        android.app.AlertDialog.Builder(context).setTitle("开启悬浮通知")
            .setMessage(buildString {
                append("要让通知从屏幕顶部弹出，需要开启系统权限。\n\n检测到系统：$osLabel\n\n")
                append("开启方法：\n1. 点击「前往设置」按钮，打开本应用的通知设置\n")
                append("2. 在通知设置中开启「$settingName」\n3. 返回应用，点击「发送测试通知」\n\n")
                append("开启后，通知将以横幅形式从顶部弹出。")
            }).setPositiveButton("前往设置") { _, _ -> openNotificationSettings() }
            .setNegativeButton("发送测试通知") { _, _ -> sendNormalNotification() }
            .setNeutralButton("取消", null).show()
    }

    fun refreshBackgroundStatus() { backgroundStatus.value = BackgroundPermissionHelper.checkBackgroundStatus(context) }
    fun requestExactAlarmPermission(activity: android.app.Activity, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) BackgroundPermissionHelper.requestExactAlarmPermission(activity, requestCode)
    }
    fun requestBatteryOptimizationWhitelist(activity: android.app.Activity, requestCode: Int) {
        BackgroundPermissionHelper.requestBatteryOptimizationWhitelist(activity, requestCode)
    }
    fun openBatteryOptimizationSettings() { BackgroundPermissionHelper.openBatteryOptimizationSettings(context) }

    fun startKeepAliveService() {
        try { KeepAliveService.start(context); keepAliveEnabled.value = true; toast("保活服务已启动") }
        catch (e: Exception) { toast("启动保活服务失败：${e.message}") }
    }

    fun stopKeepAliveService() {
        try { KeepAliveService.stop(context); keepAliveEnabled.value = false; toast("保活服务已停止") }
        catch (e: Exception) { toast("停止保活服务失败：${e.message}") }
    }

    fun refreshKeepAliveStatus() { keepAliveEnabled.value = KeepAliveService.isRunning(context) }

    fun openNotificationSettings() {
        try {
            val intent = Intent().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS; putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                } else { action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS; data = Uri.fromParts("package", context.packageName, null) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent); toast("请在通知设置中开启「悬浮通知」或「横幅」")
        } catch (_: Exception) { toast("无法打开设置页面，请手动前往：系统设置 > 通知") }
    }

    fun sendTestNotification() {
        viewModelScope.launch {
            try {
                if (!hasNotificationPermission()) { toast("未授予通知权限，请先授予通知权限"); return@launch }
                if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) { toast("通知已被禁用，请在系统设置中开启"); return@launch }
                sendTestNotificationInternal()
            } catch (e: Exception) { toast("发送测试通知失败：${e.message}") }
        }
    }

    private fun sendTestNotificationInternal() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "course_reminder"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(channelId) == null) {
                notificationManager.createNotificationChannel(NotificationChannel(channelId, context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.reminder_channel_description); enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 250, 250); enableLights(true); lightColor = android.graphics.Color.BLUE
                    setSound(Settings.System.DEFAULT_NOTIFICATION_URI, AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
                })
            }
            if (notificationManager.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE) { toast("通知渠道已被禁用，请在系统设置中开启"); return }
        }

        val intent = Intent(context, com.wind.ggbond.classtime.MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        val pendingIntent = PendingIntent.getActivity(context, System.currentTimeMillis().toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val headsUpEnabled = try { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("heads_up_notification_enabled", true) } catch (_: Exception) { true }
        val fullScreenIntent = if (headsUpEnabled) PendingIntent.getActivity(context, System.currentTimeMillis().toInt() + 10000, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) else null

        val builder = NotificationCompat.Builder(context, channelId).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("测试课程：高等数学").setContentText("地点：A101 | 张老师")
            .setStyle(NotificationCompat.BigTextStyle().bigText("上课地点：A101\n任课教师：张老师\n时间：周一 第1-2节\n\n这是一条测试通知"))
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setContentIntent(pendingIntent).setAutoCancel(true).setDefaults(NotificationCompat.DEFAULT_ALL)

        if (headsUpEnabled && fullScreenIntent != null) builder.setFullScreenIntent(fullScreenIntent, true).setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_MESSAGE).setShowWhen(true).setWhen(System.currentTimeMillis())

        try {
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
            toast("测试通知已发送 | 弹窗：${if (headsUpEnabled) "开" else "关"}")
        } catch (_: SecurityException) { toast("发送通知失败：缺少通知权限") } catch (e: Exception) { toast("发送通知失败：${e.message}") }
    }

    private fun sendNormalNotification() { sendTestNotificationInternal() }

    fun copyToClipboard(text: String, label: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
        toast("$label 已复制到剪贴板")
    }

    fun showDisclaimerDialog() { showDisclaimerDialog.value = true }
    fun hideDisclaimerDialog() { showDisclaimerDialog.value = false }
    fun acceptDisclaimer() { disclaimerAccepted.value = true; viewModelScope.launch { settingsRepository.setDisclaimerAccepted(true) } }
    fun markOnboardingCompleted() { settingUpdate(onboardingCompleted, true) { settingsRepository.setOnboardingCompleted(true) } }

    fun updateAllCoursesColor() {
        viewModelScope.launch {
            try {
                val s = scheduleRepository.getCurrentSchedule() ?: run { toast("未找到当前课表"); return@launch }
                toast("已更新 ${courseRepository.updateCoursesColor(s.id)} 门课程的颜色")
            } catch (e: Exception) { toast("更新失败: ${e.message}") }
        }
    }

    fun showReminderTestDialog() { showReminderTestDialog.value = true }
    fun hideReminderTestDialog() { showReminderTestDialog.value = false }

    private suspend fun testReminderCore(isNextCourse: Boolean, label: String): Boolean {
        val schedule = scheduleRepository.getCurrentSchedule() ?: run { toast("未找到当前课表"); return false }
        val courses = courseRepository.getAllCoursesBySchedule(schedule.id).first()
        if (courses.isEmpty()) { toast("当前课表没有课程"); return false }
        val target = if (isNextCourse && courses.size >= 2) courses[1] else courses[0]
        return reminderScheduler.scheduleTestAlarm(target.id, 1, System.currentTimeMillis() + 5000, isNextCourse, target.courseName, false)
    }

    private suspend fun testReminderAndToast(action: suspend () -> Boolean, successMsg: String, failMsg: String) {
        toast(if (action()) successMsg else failMsg)
    }

    fun testImmediateReminder() { viewModelScope.launch { testReminderAndToast({ testReminderCore(false, "立即") }, "立即提醒测试成功，5秒后收到通知", "立即提醒测试失败") } }
    fun testNextClassReminder() {
        viewModelScope.launch {
            val schedule = scheduleRepository.getCurrentSchedule() ?: run { toast("未找到当前课表"); return@launch }
            if (courseRepository.getAllCoursesBySchedule(schedule.id).first().size < 2) { toast("至少需要2门课程才能测试下节课提醒"); return@launch }
            testReminderAndToast({ testReminderCore(true, "下节") }, "下节课提醒测试成功，5秒后收到通知", "下节课提醒测试失败")
        }
    }
    fun testCourseStartReminder() { viewModelScope.launch { testReminderAndToast({ testReminderCore(false, "上课") }, "上课提醒测试成功，5秒后收到通知", "上课提醒测试失败") } }
    fun testCourseEndReminder() {
        viewModelScope.launch {
            try {
                val schedule = scheduleRepository.getCurrentSchedule() ?: run { toast("未找到当前课表"); return@launch }
                val courses = courseRepository.getAllCoursesBySchedule(schedule.id).first()
                if (courses.isEmpty()) { toast("当前课表没有课程"); return@launch }
                val target = courses[0]
                val success = reminderScheduler.scheduleTestAlarm(target.id, 1, System.currentTimeMillis() + 5000, false, "", false, true)
                toast(if (success) "下课提醒测试成功，5秒后收到通知" else "下课提醒测试失败")
            } catch (e: Exception) { toast("下课提醒测试失败: ${e.message}") }
        }
    }

    fun testAllReminders() {
        viewModelScope.launch {
            try {
                toast("开始测试所有提醒功能...")
                kotlinx.coroutines.delay(1000); testImmediateReminder()
                kotlinx.coroutines.delay(2000); testNextClassReminder()
                kotlinx.coroutines.delay(2000); testCourseStartReminder()
                kotlinx.coroutines.delay(2000); toast("所有提醒测试完成")
            } catch (e: Exception) { toast("批量测试失败: ${e.message}") }
        }
    }

    fun runReminderDiagnostic() {
        viewModelScope.launch {
            try {
                val results = ReminderDiagnostic.runDiagnostic(context)
                val summary = ReminderDiagnostic.getDiagnosticSummary(results)
                val report = buildString {
                    appendLine("🔍 $summary").appendLine()
                    results.forEach { r ->
                        appendLine("${if (r.status) "✅" else "❌"} ${r.item}").appendLine("   ${r.description}")
                        if (r.suggestion.isNotEmpty()) appendLine("   💡 建议：${r.suggestion}")
                        appendLine()
                    }
                    appendLine(if (ReminderDiagnostic.hasCriticalIssues(results)) "⚠️ 发现严重问题" else "✅ 未发现严重问题")
                }
                toast(summary)
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("提醒诊断报告", report))
                toast("详细报告已复制到剪贴板")
            } catch (e: Exception) { toast("诊断失败: ${e.message}") }
        }
    }

    fun setMonetCourseColorsEnabled(enabled: Boolean) { viewModelScope.launch { settingsRepository.setMonetCourseColorsEnabled(enabled) } }
    fun setCourseColorSaturation(saturation: Int) { viewModelScope.launch { settingsRepository.setCourseColorSaturation(saturation.coerceIn(0, 2)) } }

    private fun toast(msg: String) { viewModelScope.launch { _messageEvent.emit(msg) } }
}
