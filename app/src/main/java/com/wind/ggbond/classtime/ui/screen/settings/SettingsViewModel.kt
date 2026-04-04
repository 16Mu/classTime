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
import android.widget.Toast
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

    companion object {
        private const val TAG = "SettingsVM"
    }
    
    private val _reminderEnabled = MutableStateFlow(false)  // 默认关闭，让用户主动开启
    val reminderEnabled: StateFlow<Boolean> = _reminderEnabled.asStateFlow()
    
    private val _defaultReminderMinutes = MutableStateFlow(10)
    val defaultReminderMinutes: StateFlow<Int> = _defaultReminderMinutes.asStateFlow()
    
    private val _showClearDataDialog = MutableStateFlow(false)
    val showClearDataDialog: StateFlow<Boolean> = _showClearDataDialog.asStateFlow()
    
    private val _compactModeEnabled = MutableStateFlow(false)
    val compactModeEnabled: StateFlow<Boolean> = _compactModeEnabled.asStateFlow()
    
    private val _headsUpNotificationEnabled = MutableStateFlow(true)  // 默认开启弹窗
    val headsUpNotificationEnabled: StateFlow<Boolean> = _headsUpNotificationEnabled.asStateFlow()
    
    private val _showWeekendEnabled = MutableStateFlow(true)  // 默认显示周末
    val showWeekendEnabled: StateFlow<Boolean> = _showWeekendEnabled.asStateFlow()
    
    private val _bottomBarBlurEnabled = MutableStateFlow(true)  // 默认开启底部栏高斯模糊
    val bottomBarBlurEnabled: StateFlow<Boolean> = _bottomBarBlurEnabled.asStateFlow()
    
    // ==================== 莫奈课程取色 ====================
    private val _monetEnabled = MutableStateFlow(false)
    val monetEnabled: StateFlow<Boolean> = _monetEnabled.asStateFlow()
    
    private val _courseColorSaturation = MutableStateFlow(1)  // 0=柔和, 1=标准, 2=鲜艳
    val courseColorSaturation: StateFlow<Int> = _courseColorSaturation.asStateFlow()
    
    // ✅ 后台运行状态
    private val _backgroundStatus = MutableStateFlow(BackgroundPermissionHelper.checkBackgroundStatus(context))
    val backgroundStatus: StateFlow<BackgroundPermissionHelper.BackgroundStatus> = _backgroundStatus.asStateFlow()
    
    // ✅ 保活服务状态
    private val _keepAliveEnabled = MutableStateFlow(KeepAliveService.isRunning(context))
    val keepAliveEnabled: StateFlow<Boolean> = _keepAliveEnabled.asStateFlow()

    // 使用可空类型，null 表示尚未加载，避免在加载完成前显示对话框
    private val _disclaimerAccepted = MutableStateFlow<Boolean?>(null)
    val disclaimerAccepted: StateFlow<Boolean?> = _disclaimerAccepted.asStateFlow()
    
    // 功能引导是否完成
    private val _onboardingCompleted = MutableStateFlow<Boolean?>(null)
    val onboardingCompleted: StateFlow<Boolean?> = _onboardingCompleted.asStateFlow()
    
    // 导出导入相关状态
    private val _showExportDialog = MutableStateFlow(false)
    val showExportDialog: StateFlow<Boolean> = _showExportDialog.asStateFlow()
    
    private val _exportResult = MutableStateFlow<IDataExporter.ExportResult?>(null)
    val exportResult: StateFlow<IDataExporter.ExportResult?> = _exportResult.asStateFlow()
    
    // 通知权限提示对话框
    private val _showNotificationPermissionDialog = MutableStateFlow(false)
    val showNotificationPermissionDialog: StateFlow<Boolean> = _showNotificationPermissionDialog.asStateFlow()
    
    private val _showImportDialog = MutableStateFlow(false)
    val showImportDialog: StateFlow<Boolean> = _showImportDialog.asStateFlow()
    
    private val _showDisclaimerDialog = MutableStateFlow(false)
    val showDisclaimerDialog: StateFlow<Boolean> = _showDisclaimerDialog.asStateFlow()
    
    // 权限引导对话框状态
    private val _showPermissionGuideDialog = MutableStateFlow(false)
    val showPermissionGuideDialog: StateFlow<Boolean> = _showPermissionGuideDialog.asStateFlow()
    
    // 提醒测试对话框状态
    private val _showReminderTestDialog = MutableStateFlow(false)
    val showReminderTestDialog: StateFlow<Boolean> = _showReminderTestDialog.asStateFlow()

    // 莫奈课程取色相关状态
    val monetCourseColorsEnabled: StateFlow<Boolean> = settingsRepository
        .observeMonetCourseColorsEnabled()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val monetColorPreview: StateFlow<List<String>> = combine(
        monetCourseColorsEnabled,
        courseColorSaturation,
        backgroundThemeManager.observeCourseColors(
            when (courseColorSaturation.value) {
                0 -> MonetColorPalette.SaturationLevel.SOFT
                2 -> MonetColorPalette.SaturationLevel.VIBRANT
                else -> MonetColorPalette.SaturationLevel.STANDARD
            }
        )
    ) { enabled, saturation, _ ->
        if (enabled) {
            // 使用动态生成的颜色
            val seedColor = backgroundThemeManager.getCurrentSeedColor()
            val level = when (saturation) {
                0 -> MonetColorPalette.SaturationLevel.SOFT
                2 -> MonetColorPalette.SaturationLevel.VIBRANT
                else -> MonetColorPalette.SaturationLevel.STANDARD
            }
            MonetColorPalette.generatePalette(seedColor, level, isDarkMode = false)
        } else {
            // 返回固定调色板的颜色用于对比预览
            CourseColorPalette.getAllColors()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CourseColorPalette.getAllColors()
    )
    
    init {
        loadSettings()
        // 持续观察 DataStore，确保不同页面的 ViewModel 实例都能实时收到更新
        viewModelScope.launch {
            settingsRepository.observeCompactModeEnabled()
                .distinctUntilChanged()
                .collect { value ->
                    _compactModeEnabled.value = value
                }
        }
        viewModelScope.launch {
            settingsRepository.observeShowWeekendEnabled()
                .distinctUntilChanged()
                .collect { value ->
                    _showWeekendEnabled.value = value
                }
        }
        viewModelScope.launch {
            settingsRepository.observeMonetCourseColorsEnabled()
                .distinctUntilChanged()
                .collect { value ->
                    _monetEnabled.value = value
                }
        }
        viewModelScope.launch {
            settingsRepository.observeCourseColorSaturation()
                .distinctUntilChanged()
                .collect { value ->
                    _courseColorSaturation.value = value
                }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _reminderEnabled.value = settingsRepository.isReminderEnabled()
            _defaultReminderMinutes.value = settingsRepository.getDefaultReminderMinutes()
            _compactModeEnabled.value = settingsRepository.isCompactModeEnabled()
            val headsUpEnabled = settingsRepository.isHeadsUpNotificationEnabled()
            _headsUpNotificationEnabled.value = headsUpEnabled
            // 同步到 SharedPreferences（供 Worker 读取）
            context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("heads_up_notification_enabled", headsUpEnabled)
                .apply()
            // 加载完成后设置实际值，而不是默认 false
            _disclaimerAccepted.value = settingsRepository.isDisclaimerAccepted()
            _onboardingCompleted.value = settingsRepository.isOnboardingCompleted()
            _showWeekendEnabled.value = settingsRepository.isShowWeekendEnabled()
            _bottomBarBlurEnabled.value = settingsRepository.isBottomBarBlurEnabled()
            // 莫奈课程取色
            _monetEnabled.value = settingsRepository.isMonetCourseColorsEnabled()
            _courseColorSaturation.value = settingsRepository.getCourseColorSaturation()
        }
    }
    
    fun updateReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            applyReminderEnabledChange(enabled)
        }
        
        // 如果开启提醒，显示建议提示
        if (enabled) {
            viewModelScope.launch {
                // 检查是否有权限缺失
                val hasNotificationPermission = NotificationManagerCompat.from(context).areNotificationsEnabled()
                if (!hasNotificationPermission) {
                    // 显示建议开启通知的提示
                    _showNotificationPermissionDialog.value = true
                }
            }
        }
    }
    
    fun hideNotificationPermissionDialog() {
        _showNotificationPermissionDialog.value = false
    }
    
    fun hidePermissionGuideDialog() {
        _showPermissionGuideDialog.value = false
    }
    
    fun onPermissionGuideCompleted() {
        _showPermissionGuideDialog.value = false
        // 权限授予完成后，开启提醒
        viewModelScope.launch {
            applyReminderEnabledChange(true)
        }
    }
    
    private suspend fun applyReminderEnabledChange(enabled: Boolean) {
        // 1. 更新全局设置
        settingsRepository.setReminderEnabled(enabled)
        _reminderEnabled.value = enabled

        // 2. 获取当前课表
        val schedule = scheduleRepository.getCurrentSchedule()

        if (enabled) {
            // 开启提醒：同时开启所有课程的提醒并创建通知
            try {
                schedule?.let {
                    // 获取默认提醒时间
                    val defaultMinutes = settingsRepository.getDefaultReminderMinutes()

                    // 批量开启所有课程的提醒
                    val updatedCount = courseRepository.enableAllCoursesReminder(it.id, defaultMinutes)

                    android.util.Log.d("SettingsViewModel", "✅ 已开启 $updatedCount 门课程的提醒")

                    // 批量创建提醒任务
                    reminderScheduler.scheduleAllCourseReminders(it.id)

                    android.util.Log.d("SettingsViewModel", "✅ 已创建所有课程的通知任务")

                    android.widget.Toast.makeText(
                        context,
                        "已为 $updatedCount 门课程开启提醒",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "开启课程提醒失败", e)
                android.widget.Toast.makeText(
                    context,
                    "开启课程提醒失败：${e.message}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            // 关闭提醒：同时关闭所有课程的提醒并取消通知任务
            try {
                schedule?.let {
                    // 批量关闭所有课程的提醒
                    val updatedCount = courseRepository.disableAllCoursesReminder(it.id)

                    android.util.Log.d("SettingsViewModel", "✅ 已关闭 $updatedCount 门课程的提醒")

                    // 取消所有提醒任务
                    reminderScheduler.cancelAllCourseReminders(it.id)

                    android.util.Log.d("SettingsViewModel", "✅ 已取消所有课程的通知任务")

                    android.widget.Toast.makeText(
                        context,
                        "已关闭所有课程提醒",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "关闭课程提醒失败", e)
                android.widget.Toast.makeText(
                    context,
                    "关闭课程提醒失败：${e.message}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun updateDefaultReminderMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setDefaultReminderMinutes(minutes)
            _defaultReminderMinutes.value = minutes
        }
    }

    fun updateHeadsUpNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            // 同时保存到 DataStore 和 SharedPreferences（兼容 Worker 读取）
            settingsRepository.setHeadsUpNotificationEnabled(enabled)
            // 保存到 SharedPreferences 供 Worker 读取
            context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("heads_up_notification_enabled", enabled)
                .apply()
            _headsUpNotificationEnabled.value = enabled
        }
    }

    /**
     * 读取弹窗通知设置（用于 Worker 中读取）
     */
    suspend fun isHeadsUpNotificationEnabled(): Boolean {
        return settingsRepository.isHeadsUpNotificationEnabled()
    }
    
    /**
     * 显示导出对话框
     */
    fun showExportDialog() {
        _showExportDialog.value = true
    }
    
    /**
     * 隐藏导出对话框
     */
    fun hideExportDialog() {
        _showExportDialog.value = false
    }
    
    /**
     * 导出课程表（指定格式）
     */
    fun exportSchedule(format: IDataExporter.ExportFormat) {
        viewModelScope.launch {
            val schedule = scheduleRepository.getCurrentSchedule()
            schedule?.let {
                val result = exportService.export(it.id, format)
                _exportResult.value = result
                
                if (result.success) {
                    Toast.makeText(
                        context,
                        "导出成功！文件已保存",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "导出失败: ${result.errorMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    /**
     * 分享导出的文件
     */
    fun shareExportedFile(format: IDataExporter.ExportFormat) {
        viewModelScope.launch {
            val schedule = scheduleRepository.getCurrentSchedule()
            schedule?.let {
                val result = exportService.export(it.id, format)
                if (result.success && result.filePath != null) {
                    exportService.shareFile(result.filePath, getMimeType(format))
                }
            }
        }
    }
    
    /**
     * 显示导入对话框
     */
    fun showImportDialog() {
        _showImportDialog.value = true
    }
    
    /**
     * 隐藏导入对话框
     */
    fun hideImportDialog() {
        _showImportDialog.value = false
    }
    
    /**
     * 清除导出结果
     */
    fun clearExportResult() {
        _exportResult.value = null
    }
    
    /**
     * 分享文件
     */
    fun shareFile(filePath: String) {
        exportService.shareFile(filePath)
    }
    
    /**
     * 导入课程表
     */
    fun importSchedule(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                android.util.Log.d("SettingsViewModel", "开始导入课程表，URI: $uri")
                
                val schedule = scheduleRepository.getCurrentSchedule()
                android.util.Log.d("SettingsViewModel", "获取到当前课表: $schedule")
                
                schedule?.let {
                    android.util.Log.d("SettingsViewModel", "课表ID: ${it.id}, 开始导入")
                    val result = importService.importFromUri(uri, it.id)
                    
                    android.util.Log.d("SettingsViewModel", "导入结果: success=${result.success}, importedCount=${result.importedCount}, errorMessage=${result.errorMessage}")
                    
                    if (result.success) {
                        Toast.makeText(
                            context,
                            "导入成功！共导入 ${result.importedCount} 门课程",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "导入失败: ${result.errorMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } ?: run {
                    android.util.Log.e("SettingsViewModel", "未找到当前课表")
                    Toast.makeText(
                        context,
                        "导入失败：未找到当前课表，请先创建课表",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "导入过程中发生异常", e)
                Toast.makeText(
                    context,
                    "导入失败: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * 获取MIME类型
     */
    private fun getMimeType(format: IDataExporter.ExportFormat): String {
        return when (format) {
            IDataExporter.ExportFormat.JSON -> "application/json"
            IDataExporter.ExportFormat.ICS -> "text/calendar"
            IDataExporter.ExportFormat.CSV -> "text/csv"
            IDataExporter.ExportFormat.TXT -> "text/plain"
            IDataExporter.ExportFormat.HTML -> "text/html"
        }
    }
    
    fun showClearDataDialog() {
        _showClearDataDialog.value = true
    }
    
    fun hideClearDataDialog() {
        _showClearDataDialog.value = false
    }
    
    fun clearAllData() {
        viewModelScope.launch {
            val schedule = scheduleRepository.getCurrentSchedule()
            schedule?.let {
                courseRepository.deleteAllCoursesBySchedule(it.id)
            }
        }
    }
    
    fun updateCompactModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            android.util.Log.d("SettingsViewModel", "updateCompactModeEnabled called with: $enabled")
            settingsRepository.setCompactModeEnabled(enabled)
            _compactModeEnabled.value = enabled
            android.util.Log.d("SettingsViewModel", "紧凑模式已更新为: ${_compactModeEnabled.value}")
        }
    }

    fun updateShowWeekendEnabled(enabled: Boolean) {
        viewModelScope.launch {
            android.util.Log.d("SettingsViewModel", "updateShowWeekendEnabled called with: $enabled")
            settingsRepository.setShowWeekendEnabled(enabled)
            _showWeekendEnabled.value = enabled
            android.util.Log.d("SettingsViewModel", "显示周末已更新为: ${_showWeekendEnabled.value}")
        }
    }

    /**
     * 更新底部导航栏高斯模糊开关
     */
    fun updateBottomBarBlurEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBottomBarBlurEnabled(enabled)
            _bottomBarBlurEnabled.value = enabled
        }
    }
    
    /**
     * 更新莫奈课程取色开关
     */
    fun updateMonetEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setMonetCourseColorsEnabled(enabled)
            _monetEnabled.value = enabled
        }
    }
    
    /**
     * 更新课程颜色饱和度等级 (0=柔和, 1=标准, 2=鲜艳)
     */
    fun updateCourseColorSaturation(saturation: Int) {
        viewModelScope.launch {
            settingsRepository.setCourseColorSaturation(saturation)
            _courseColorSaturation.value = saturation
        }
    }
    
    
    /**
     * 检查通知权限
     */
    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
    
    /**
     * 获取手机品牌
     */
    private fun getPhoneBrand(): String {
        return Build.MANUFACTURER.lowercase()
    }
    
    private fun getOsName(): String {
        val display = Build.DISPLAY.lowercase()
        val brand = getPhoneBrand()
        return when {
            display.contains("coloros") -> "coloros"
            display.contains("hyperos") -> "hyperos"
            display.contains("miui") -> "miui"
            display.contains("harmony") -> "harmonyos"
            display.contains("originos") -> "originos"
            display.contains("flyme") -> "flyme"
            display.contains("oneui") || display.contains("one ui") -> "oneui"
            brand.contains("oppo") || brand.contains("realme") -> "coloros"
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") -> "hyperos"
            brand.contains("huawei") || brand.contains("honor") -> "harmonyos"
            brand.contains("vivo") || brand.contains("iqoo") -> "originos"
            brand.contains("samsung") -> "oneui"
            else -> "android"
        }
    }
    
    /**
     * 检测是否是小米/Redmi/POCO
     */
    private fun isXiaomi(): Boolean {
        val brand = getPhoneBrand()
        return brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")
    }
    
    /**
     * 检测是否是华为/荣耀
     */
    private fun isHuawei(): Boolean {
        val brand = getPhoneBrand()
        return brand.contains("huawei") || brand.contains("honor")
    }
    
    /**
     * 检测是否是OPPO/Realme
     */
    private fun isOppo(): Boolean {
        val brand = getPhoneBrand()
        return brand.contains("oppo") || brand.contains("realme")
    }
    
    /**
     * 检测是否是Vivo/iQOO
     */
    private fun isVivo(): Boolean {
        val brand = getPhoneBrand()
        return brand.contains("vivo") || brand.contains("iqoo")
    }
    
    /**
     * 检测是否是三星
     */
    private fun isSamsung(): Boolean {
        return getPhoneBrand().contains("samsung")
    }
    
    /**
     * 发送悬浮通知测试 - 根据品牌使用不同策略
     */
    fun sendHeadsUpNotificationTest() {
        viewModelScope.launch {
            try {
                if (!hasNotificationPermission()) {
                    Toast.makeText(context, "未授予通知权限", Toast.LENGTH_LONG).show()
                    return@launch
                }
                
                val brand = getPhoneBrand()
                Toast.makeText(context, "检测到品牌: $brand\n使用专属通知策略", Toast.LENGTH_SHORT).show()
                
                when {
                    isXiaomi() -> sendXiaomiNotification()
                    isHuawei() -> sendHuaweiNotification()
                    isOppo() -> sendOppoNotification()
                    isVivo() -> sendVivoNotification()
                    isSamsung() -> sendSamsungNotification()
                    else -> sendGenericNotification()
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "发送失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * 发送悬浮通知 - 统一使用一个通知渠道
     */
    private fun sendXiaomiNotification() = sendUnifiedNotification()
    private fun sendHuaweiNotification() = sendUnifiedNotification()
    private fun sendOppoNotification() = sendUnifiedNotification()
    private fun sendVivoNotification() = sendUnifiedNotification()
    private fun sendSamsungNotification() = sendUnifiedNotification()
    private fun sendGenericNotification() = sendUnifiedNotification()
    
    /**
     * 统一的悬浮通知方法 - 使用自定义悬浮窗实现（终极方案）
     */
    private fun sendUnifiedNotification() {
        val os = getOsName()
        val osLabel = when (os) {
            "coloros" -> "ColorOS (OPPO / realme)"
            "hyperos" -> "HyperOS / MIUI (小米)"
            "miui" -> "MIUI (小米)"
            "harmonyos" -> "HarmonyOS (华为)"
            "originos" -> "OriginOS / FuntouchOS (vivo)"
            "oneui" -> "OneUI (三星)"
            "flyme" -> "Flyme (魅族)"
            else -> "原生 Android"
        }
        
        val settingName = when (os) {
            "coloros" -> "允许横幅通知"
            "hyperos", "miui" -> "悬浮通知/横幅通知"
            "harmonyos" -> "横幅通知"
            "originos" -> "悬浮通知/横幅通知"
            "oneui" -> "简要弹出通知/浮动通知"
            "flyme" -> "悬浮通知"
            else -> "悬浮通知/横幅通知"
        }
        
        val message = buildString {
            append("要让通知从屏幕顶部弹出，需要开启系统权限。\n\n")
            append("检测到系统：$osLabel\n\n")
            append("开启方法：\n")
            append("1. 点击「前往设置」按钮，打开本应用的系统通知设置\n")
            append("2. 在通知设置中开启「$settingName」\n")
            append("3. 返回应用，点击「发送测试通知」\n\n")
            append("开启后，通知将以横幅形式从顶部弹出。")
        }
        
        android.app.AlertDialog.Builder(context)
            .setTitle("开启悬浮通知")
            .setMessage(message)
            .setPositiveButton("前往设置") { dialog, which ->
                openNotificationSettings()
            }
            .setNegativeButton("发送测试通知") { dialog, which ->
                sendNormalNotification()
            }
            .setNeutralButton("取消", null)
            .show()
    }
    
    /**
     * ✅ 刷新后台运行状态
     */
    fun refreshBackgroundStatus() {
        _backgroundStatus.value = BackgroundPermissionHelper.checkBackgroundStatus(context)
    }
    
    /**
     * ✅ 请求精确时间权限
     */
    fun requestExactAlarmPermission(activity: android.app.Activity, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BackgroundPermissionHelper.requestExactAlarmPermission(activity, requestCode)
        }
    }
    
    /**
     * ✅ 请求电池优化白名单
     */
    fun requestBatteryOptimizationWhitelist(activity: android.app.Activity, requestCode: Int) {
        BackgroundPermissionHelper.requestBatteryOptimizationWhitelist(activity, requestCode)
    }
    
    /**
     * ✅ 打开电池优化设置
     */
    fun openBatteryOptimizationSettings() {
        BackgroundPermissionHelper.openBatteryOptimizationSettings(context)
    }
    
    /**
     * ✅ 启动保活服务
     */
    fun startKeepAliveService() {
        try {
            KeepAliveService.start(context)
            _keepAliveEnabled.value = true
            android.widget.Toast.makeText(context, "保活服务已启动", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("SettingsViewModel", "启动保活服务失败", e)
            android.widget.Toast.makeText(context, "启动保活服务失败：${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * ✅ 停止保活服务
     */
    fun stopKeepAliveService() {
        try {
            KeepAliveService.stop(context)
            _keepAliveEnabled.value = false
            android.widget.Toast.makeText(context, "保活服务已停止", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("SettingsViewModel", "停止保活服务失败", e)
            android.widget.Toast.makeText(context, "停止保活服务失败：${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * ✅ 刷新保活服务状态
     */
    fun refreshKeepAliveStatus() {
        _keepAliveEnabled.value = KeepAliveService.isRunning(context)
    }
    
    /**
     * ✅ 打开系统通知设置页面（公开方法，供设置界面调用）
     */
    fun openNotificationSettings() {
        try {
            val intent = Intent().apply {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                        // Android 8.0+ 跳转到应用通知设置
                        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    else -> {
                        // Android 8.0 以下
                        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            
            Toast.makeText(
                context,
                "请在通知设置中开启「悬浮通知」或「横幅」",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                "无法打开设置页面，请手动前往：系统设置 > 通知",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    /**
     * ✅ 发送测试通知（公开方法，供设置界面调用）
     */
    fun sendTestNotification() {
        viewModelScope.launch {
            try {
                // 检查通知权限
                if (!hasNotificationPermission()) {
                    Toast.makeText(context, "未授予通知权限，请先授予通知权限", Toast.LENGTH_LONG).show()
                    return@launch
                }
                
                // 检查通知是否启用
                val notificationManager = NotificationManagerCompat.from(context)
                if (!notificationManager.areNotificationsEnabled()) {
                    Toast.makeText(context, "通知已被禁用，请在系统设置中开启", Toast.LENGTH_LONG).show()
                    return@launch
                }
                
                // 发送测试通知
                sendTestNotificationInternal()
                
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "发送测试通知失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * 发送测试通知（内部实现）
     */
    private fun sendTestNotificationInternal() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "course_reminder"  // 使用与真实提醒相同的渠道
        
        // 创建或获取通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existingChannel = notificationManager.getNotificationChannel(channelId)
            if (existingChannel == null) {
                val channel = NotificationChannel(
                    channelId,
                    context.getString(R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.reminder_channel_description)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 250, 250)
                    enableLights(true)
                    lightColor = android.graphics.Color.BLUE
                    setSound(
                        Settings.System.DEFAULT_NOTIFICATION_URI,
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build()
                    )
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            // 检查渠道是否被禁用
            val channel = notificationManager.getNotificationChannel(channelId)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) {
                Toast.makeText(
                    context,
                    "通知渠道已被禁用，请在系统设置中开启",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }
        
        // 创建打开应用的 Intent
        val intent = Intent(context, com.wind.ggbond.classtime.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 读取弹窗设置
        val headsUpEnabled = try {
            context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getBoolean("heads_up_notification_enabled", true)
        } catch (e: Exception) {
            true
        }
        
        // 创建全屏意图（如果启用弹窗）
        val fullScreenIntent = if (headsUpEnabled) {
            PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt() + 10000,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            null
        }
        
        // 构建测试通知（模拟真实的课程提醒）
        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("测试课程：高等数学")
            .setContentText("地点：A101 | 张老师")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("上课地点：A101\n任课教师：张老师\n时间：周一 第1-2节\n\n这是一条测试通知，用于检查通知功能是否正常"))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
        
        // 如果启用弹窗，添加 FullScreenIntent
        if (headsUpEnabled && fullScreenIntent != null) {
            notificationBuilder
                .setFullScreenIntent(fullScreenIntent, true)
                .setPriority(NotificationCompat.PRIORITY_MAX)  // ✅ 使用最高优先级
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)  // ✅ 使用 MESSAGE 分类
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
        } else {
            // 即使不弹窗，也设置基础分类
            notificationBuilder.setCategory(NotificationCompat.CATEGORY_REMINDER)
        }
        
        val notification = notificationBuilder.build()
        
        // 发送通知
        try {
            val notificationId = System.currentTimeMillis().toInt()
            notificationManager.notify(notificationId, notification)
            
            // 诊断信息
            val diagnosticInfo = buildString {
                append("测试通知已发送\n\n")
                append("弹窗设置：${if (headsUpEnabled) "已开启" else "已关闭"}\n")
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = notificationManager.getNotificationChannel(channelId)
                    append("渠道重要性：${channel?.importance}\n")
                    append("渠道名称：${channel?.name}\n")
                }
                
                if (headsUpEnabled && fullScreenIntent != null) {
                    append("\n提示：如果未看到弹窗：\n")
                    append("1. 检查系统设置中的悬浮通知\n")
                    append("2. 确保应用在前台时允许弹窗\n")
                    append("3. 某些设备需要手动开启悬浮通知")
                } else {
                    append("\n提示：已关闭弹窗通知")
                }
            }
            
            Toast.makeText(
                context,
                diagnosticInfo,
                Toast.LENGTH_LONG
            ).show()
            
        } catch (e: SecurityException) {
            Toast.makeText(
                context,
                "发送通知失败：缺少通知权限",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "发送通知失败：${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    /**
     * 发送普通通知（用户确认后）
     */
    private fun sendNormalNotification() {
        sendTestNotificationInternal()
    }
    
    /**
     * 复制文本到剪贴板
     */
    fun copyToClipboard(text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label 已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    fun showDisclaimerDialog() {
        _showDisclaimerDialog.value = true
    }

    fun hideDisclaimerDialog() {
        _showDisclaimerDialog.value = false
    }

    fun acceptDisclaimer() {
        viewModelScope.launch {
            settingsRepository.setDisclaimerAccepted(true)
            _disclaimerAccepted.value = true
        }
    }

    /**
     * 标记功能引导已完成
     */
    fun markOnboardingCompleted() {
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(true)
            _onboardingCompleted.value = true
        }
    }
    
    /**
     * 批量更新课程颜色
     * 将所有课程的颜色更新为基于课程名称的新配色方案
     */
    fun updateAllCoursesColor() {
        viewModelScope.launch {
            try {
                val currentSchedule = scheduleRepository.getCurrentSchedule()
                if (currentSchedule == null) {
                    Toast.makeText(context, "未找到当前课表，请先创建课表", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val updatedCount = courseRepository.updateCoursesColor(currentSchedule.id)
                Toast.makeText(context, "已更新 $updatedCount 门课程的颜色", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "更新失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 显示提醒测试对话框
     */
    fun showReminderTestDialog() {
        _showReminderTestDialog.value = true
    }
    
    /**
     * 隐藏提醒测试对话框
     */
    fun hideReminderTestDialog() {
        _showReminderTestDialog.value = false
    }
    
    /**
     * 测试立即提醒
     */
    fun testImmediateReminder() {
        viewModelScope.launch {
            try {
                val currentSchedule = scheduleRepository.getCurrentSchedule()
                if (currentSchedule == null) {
                    Toast.makeText(context, "未找到当前课表，请先导入课表", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val courses = courseRepository.getAllCoursesBySchedule(currentSchedule.id).first()
                if (courses.isEmpty()) {
                    Toast.makeText(context, "当前课表没有课程", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // 取第一门课程进行测试
                val testCourse = courses[0]
                
                // 创建一个立即触发的提醒（5秒后）
                val triggerTime = System.currentTimeMillis() + 5000
                
                val success = reminderScheduler.scheduleTestAlarm(
                    courseId = testCourse.id,
                    weekNumber = 1,
                    triggerTime = triggerTime,
                    isNextCourse = false,
                    currentCourseName = testCourse.courseName,
                    isSameCourseClassroom = false
                )
                
                if (success) {
                    Toast.makeText(context, "立即提醒测试成功，5秒后收到通知", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "立即提醒测试失败", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Toast.makeText(context, "测试失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 测试下节课提醒
     */
    fun testNextClassReminder() {
        viewModelScope.launch {
            try {
                val currentSchedule = scheduleRepository.getCurrentSchedule()
                if (currentSchedule == null) {
                    Toast.makeText(context, "未找到当前课表，请先导入课表", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val courses = courseRepository.getAllCoursesBySchedule(currentSchedule.id).first()
                if (courses.size < 2) {
                    Toast.makeText(context, "至少需要2门课程才能测试下节课提醒", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // 取前两门课程进行测试
                val currentCourse = courses[0]
                val nextCourse = courses[1]
                
                // 创建一个立即触发的下节课提醒（5秒后）
                val triggerTime = System.currentTimeMillis() + 5000
                
                val success = reminderScheduler.scheduleTestAlarm(
                    courseId = nextCourse.id,
                    weekNumber = 1,
                    triggerTime = triggerTime,
                    isNextCourse = true,
                    currentCourseName = currentCourse.courseName,
                    isSameCourseClassroom = false
                )
                
                if (success) {
                    Toast.makeText(context, "下节课提醒测试成功，5秒后收到通知", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "下节课提醒测试失败", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Toast.makeText(context, "测试失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 测试上课提醒
     */
    fun testCourseStartReminder() {
        viewModelScope.launch {
            try {
                val currentSchedule = scheduleRepository.getCurrentSchedule()
                if (currentSchedule == null) {
                    Toast.makeText(context, "未找到当前课表，请先导入课表", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val courses = courseRepository.getAllCoursesBySchedule(currentSchedule.id).first()
                if (courses.isEmpty()) {
                    Toast.makeText(context, "当前课表没有课程", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // 取第一门课程进行测试
                val testCourse = courses[0]
                
                // 创建一个立即触发的上课提醒（5秒后）
                val triggerTime = System.currentTimeMillis() + 5000
                
                val success = reminderScheduler.scheduleTestAlarm(
                    courseId = testCourse.id,
                    weekNumber = 1,
                    triggerTime = triggerTime,
                    isNextCourse = false,
                    currentCourseName = testCourse.courseName,
                    isSameCourseClassroom = false
                )
                
                if (success) {
                    Toast.makeText(context, "上课提醒测试成功，5秒后收到通知", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "上课提醒测试失败", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Toast.makeText(context, "测试失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 测试下课提醒
     */
    fun testCourseEndReminder() {
        viewModelScope.launch {
            try {
                Toast.makeText(context, "下课提醒功能正在开发中", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "测试失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 测试所有提醒功能
     */
    fun testAllReminders() {
        viewModelScope.launch {
            try {
                Toast.makeText(context, "开始测试所有提醒功能...", Toast.LENGTH_SHORT).show()
                
                // 延迟1秒后开始逐个测试
                kotlinx.coroutines.delay(1000)
                testImmediateReminder()
                
                kotlinx.coroutines.delay(2000)
                testNextClassReminder()
                
                kotlinx.coroutines.delay(2000)
                testCourseStartReminder()
                
                kotlinx.coroutines.delay(2000)
                Toast.makeText(context, "所有提醒测试完成", Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                Toast.makeText(context, "批量测试失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 运行提醒诊断
     */
    fun runReminderDiagnostic() {
        viewModelScope.launch {
            try {
                val results = ReminderDiagnostic.runDiagnostic(context)
                val summary = ReminderDiagnostic.getDiagnosticSummary(results)
                val hasCriticalIssues = ReminderDiagnostic.hasCriticalIssues(results)

                // 构建详细报告
                val report = StringBuilder()
                report.appendLine("🔍 $summary")
                report.appendLine()

                results.forEach { result ->
                    val statusIcon = if (result.status) "✅" else "❌"
                    report.appendLine("$statusIcon ${result.item}")
                    report.appendLine("   ${result.description}")
                    if (result.suggestion.isNotEmpty()) {
                        report.appendLine("   💡 建议：${result.suggestion}")
                    }
                    report.appendLine()
                }

                if (hasCriticalIssues) {
                    report.appendLine("⚠️ 发现严重问题，可能影响提醒功能")
                } else {
                    report.appendLine("✅ 未发现严重问题")
                }

                // 显示诊断报告
                Toast.makeText(context, summary, Toast.LENGTH_SHORT).show()

                // 可以考虑将报告保存到剪贴板或显示在对话框中
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("提醒诊断报告", report.toString())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "详细报告已复制到剪贴板", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                Toast.makeText(context, "诊断失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 设置莫奈课程取色开关状态
     */
    fun setMonetCourseColorsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setMonetCourseColorsEnabled(enabled)
        }
    }

    /**
     * 设置课程颜色饱和度等级
     * @param saturation 0=柔和, 1=标准, 2=鲜艳
     */
    fun setCourseColorSaturation(saturation: Int) {
        viewModelScope.launch {
            val clampedSaturation = saturation.coerceIn(0, 2)
            settingsRepository.setCourseColorSaturation(clampedSaturation)
        }
    }
}



