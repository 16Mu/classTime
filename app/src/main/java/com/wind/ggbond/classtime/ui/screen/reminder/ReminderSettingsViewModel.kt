package com.wind.ggbond.classtime.ui.screen.reminder

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.service.AlarmReminderScheduler
import com.wind.ggbond.classtime.util.PermissionGuideManager
import com.wind.ggbond.classtime.util.ReminderDiagnostic
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 权限类型枚举
 * 标识各类提醒所需权限
 */
enum class PermissionType {
    /** 通知权限 */
    NOTIFICATION,
    /** 精确闹钟权限 (Android 12+) */
    EXACT_ALARM,
    /** 电池优化白名单 */
    BATTERY_OPTIMIZATION,
    /** 全屏通知权限 (Android 14+) */
    FULL_SCREEN_INTENT,
    /** 自启动权限 (特定厂商) */
    AUTO_START,
    /** 后台弹出界面 (小米/OPPO) */
    BACKGROUND_POPUP,
    /** 悬浮窗权限 */
    OVERLAY
}

/**
 * 权限重要性级别
 */
enum class PermissionImportance {
    /** 必要权限，缺失会直接影响提醒功能 */
    REQUIRED,
    /** 建议权限，缺失可能影响部分场景下的提醒效果 */
    RECOMMENDED
}

/**
 * 权限状态数据类
 * 描述单个权限的当前状态
 */
data class PermissionState(
    val type: PermissionType,       // 权限类型
    val name: String,               // 显示名称
    val description: String,        // 简短描述
    val isGranted: Boolean,         // 是否已授予
    val importance: PermissionImportance  // 重要性级别
)

/**
 * 课程提醒设置页面 ViewModel
 * 管理提醒开关、偏好配置和权限状态
 */
@HiltViewModel
class ReminderSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val courseRepository: CourseRepository,
    private val scheduleRepository: ScheduleRepository,
    private val reminderScheduler: AlarmReminderScheduler
) : ViewModel() {

    // DataStore 实例
    private val settingsDataStore = DataStoreManager.getSettingsDataStore(context)

    // DataStore Key 引用
    private val REMINDER_ENABLED_KEY = DataStoreManager.SettingsKeys.REMINDER_ENABLED_KEY
    private val DEFAULT_REMINDER_MINUTES_KEY = DataStoreManager.SettingsKeys.DEFAULT_REMINDER_MINUTES_KEY
    private val HEADS_UP_NOTIFICATION_ENABLED_KEY = DataStoreManager.SettingsKeys.HEADS_UP_NOTIFICATION_ENABLED_KEY

    // 提醒总开关状态
    private val _reminderEnabled = MutableStateFlow(false)
    val reminderEnabled: StateFlow<Boolean> = _reminderEnabled.asStateFlow()

    // 提前提醒分钟数
    private val _defaultReminderMinutes = MutableStateFlow(10)
    val defaultReminderMinutes: StateFlow<Int> = _defaultReminderMinutes.asStateFlow()

    // 悬浮通知开关状态
    private val _headsUpNotificationEnabled = MutableStateFlow(true)
    val headsUpNotificationEnabled: StateFlow<Boolean> = _headsUpNotificationEnabled.asStateFlow()

    // 权限状态列表
    private val _permissionStates = MutableStateFlow<List<PermissionState>>(emptyList())
    val permissionStates: StateFlow<List<PermissionState>> = _permissionStates.asStateFlow()

    // 提前时间选择器对话框状态
    private val _showMinutesSelector = MutableStateFlow(false)
    val showMinutesSelector: StateFlow<Boolean> = _showMinutesSelector.asStateFlow()

    // 提醒测试对话框状态
    private val _showReminderTestDialog = MutableStateFlow(false)
    val showReminderTestDialog: StateFlow<Boolean> = _showReminderTestDialog.asStateFlow()

    init {
        loadSettings()
        refreshPermissionStates()
    }

    /**
     * 从 DataStore 加载所有提醒相关设置
     */
    private fun loadSettings() {
        viewModelScope.launch {
            val preferences = settingsDataStore.data.first()
            _reminderEnabled.value = preferences[REMINDER_ENABLED_KEY] ?: false
            _defaultReminderMinutes.value = preferences[DEFAULT_REMINDER_MINUTES_KEY] ?: 10
            _headsUpNotificationEnabled.value = preferences[HEADS_UP_NOTIFICATION_ENABLED_KEY] ?: true
        }
    }

    /**
     * 刷新所有权限状态
     * 根据当前设备型号和 Android 版本动态检测权限
     */
    fun refreshPermissionStates() {
        val states = mutableListOf<PermissionState>()
        val manufacturer = Build.MANUFACTURER.lowercase()

        // 1. 通知权限（所有设备）
        val hasNotification = NotificationManagerCompat.from(context).areNotificationsEnabled()
        states.add(
            PermissionState(
                type = PermissionType.NOTIFICATION,
                name = "通知权限",
                description = "允许应用发送课程提醒通知",
                isGranted = hasNotification,
                importance = PermissionImportance.REQUIRED
            )
        )

        // 2. 准时提醒权限 (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val canExactAlarm = alarmManager.canScheduleExactAlarms()
            states.add(
                PermissionState(
                    type = PermissionType.EXACT_ALARM,
                    name = "准时提醒权限",
                    description = "确保通知能在精确的时间送达",
                    isGranted = canExactAlarm,
                    importance = PermissionImportance.REQUIRED
                )
            )
        }

        // 3. 电池优化白名单（所有设备）
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val batteryIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
        states.add(
            PermissionState(
                type = PermissionType.BATTERY_OPTIMIZATION,
                name = "电池优化白名单",
                description = "防止系统在后台关闭提醒服务",
                isGranted = batteryIgnored,
                importance = PermissionImportance.REQUIRED
            )
        )

        // 4. 全屏通知权限 (Android 14+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val canFullScreen = nm.canUseFullScreenIntent()
            states.add(
                PermissionState(
                    type = PermissionType.FULL_SCREEN_INTENT,
                    name = "全屏通知权限",
                    description = "允许在锁屏时弹出全屏提醒",
                    isGranted = canFullScreen,
                    importance = PermissionImportance.RECOMMENDED
                )
            )
        }

        // 5. 自启动权限（特定厂商设备）
        if (isManufacturerNeedAutoStart(manufacturer)) {
            val autoStartConfirmed = PermissionGuideManager.isManualStepConfirmed(context, "auto_start")
            states.add(
                PermissionState(
                    type = PermissionType.AUTO_START,
                    name = "自启动权限",
                    description = "允许应用开机自动启动和后台运行",
                    isGranted = autoStartConfirmed,
                    importance = PermissionImportance.RECOMMENDED
                )
            )
        }

        // 6. 后台弹出界面（小米/OPPO）
        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ||
            manufacturer.contains("oppo")
        ) {
            val popupConfirmed = PermissionGuideManager.isManualStepConfirmed(context, "background_popup")
            states.add(
                PermissionState(
                    type = PermissionType.BACKGROUND_POPUP,
                    name = "后台弹出界面",
                    description = "允许应用在后台弹出提醒窗口",
                    isGranted = popupConfirmed,
                    importance = PermissionImportance.RECOMMENDED
                )
            )
        }

        // 7. 悬浮窗权限（特定厂商设备）
        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ||
            manufacturer.contains("oppo") || manufacturer.contains("vivo")
        ) {
            val overlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.provider.Settings.canDrawOverlays(context)
            } else {
                true
            }
            states.add(
                PermissionState(
                    type = PermissionType.OVERLAY,
                    name = "悬浮窗权限",
                    description = "允许应用显示悬浮提醒窗口",
                    isGranted = overlayPermission,
                    importance = PermissionImportance.RECOMMENDED
                )
            )
        }

        _permissionStates.value = states
    }

    /**
     * 判断当前厂商是否需要自启动权限
     */
    private fun isManufacturerNeedAutoStart(manufacturer: String): Boolean {
        return manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ||
                manufacturer.contains("huawei") || manufacturer.contains("honor") ||
                manufacturer.contains("oppo") || manufacturer.contains("realme") ||
                manufacturer.contains("vivo") || manufacturer.contains("iqoo") ||
                manufacturer.contains("oneplus") || manufacturer.contains("meizu") ||
                manufacturer.contains("blackshark")
    }

    /**
     * 切换提醒总开关
     * 开启时批量启用所有课程提醒，关闭时批量取消
     */
    fun updateReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            // 更新 DataStore
            settingsDataStore.edit { preferences ->
                preferences[REMINDER_ENABLED_KEY] = enabled
            }
            _reminderEnabled.value = enabled

            // 获取当前课表
            val schedule = scheduleRepository.getCurrentSchedule()

            if (enabled) {
                // 开启：批量开启所有课程的提醒
                try {
                    schedule?.let {
                        val defaultMinutes = _defaultReminderMinutes.value
                        val updatedCount = courseRepository.enableAllCoursesReminder(it.id, defaultMinutes)
                        reminderScheduler.scheduleAllCourseReminders(it.id)
                        android.widget.Toast.makeText(
                            context, "已为 $updatedCount 门课程开启提醒", android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ReminderSettingsVM", "开启课程提醒失败", e)
                    android.widget.Toast.makeText(
                        context, "开启课程提醒失败：${e.message}", android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                // 关闭：批量取消所有课程的提醒
                try {
                    schedule?.let {
                        val updatedCount = courseRepository.disableAllCoursesReminder(it.id)
                        reminderScheduler.cancelAllCourseReminders(it.id)
                        android.widget.Toast.makeText(
                            context, "已关闭所有课程提醒", android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ReminderSettingsVM", "关闭课程提醒失败", e)
                    android.widget.Toast.makeText(
                        context, "关闭课程提醒失败：${e.message}", android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * 更新提前提醒分钟数
     */
    fun updateDefaultReminderMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsDataStore.edit { preferences ->
                preferences[DEFAULT_REMINDER_MINUTES_KEY] = minutes
            }
            _defaultReminderMinutes.value = minutes
        }
    }

    /**
     * 更新悬浮通知开关
     * 同时同步到 SharedPreferences（供 Worker 读取）
     */
    fun updateHeadsUpNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.edit { preferences ->
                preferences[HEADS_UP_NOTIFICATION_ENABLED_KEY] = enabled
            }
            // 同步到 SharedPreferences 供 Worker 读取
            context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("heads_up_notification_enabled", enabled)
                .apply()
            _headsUpNotificationEnabled.value = enabled
        }
    }

    // ==================== 对话框控制 ====================

    /** 显示提前时间选择器 */
    fun showMinutesSelector() {
        _showMinutesSelector.value = true
    }

    /** 隐藏提前时间选择器 */
    fun hideMinutesSelector() {
        _showMinutesSelector.value = false
    }

    /** 显示提醒测试对话框 */
    fun showReminderTestDialog() {
        _showReminderTestDialog.value = true
    }

    /** 隐藏提醒测试对话框 */
    fun hideReminderTestDialog() {
        _showReminderTestDialog.value = false
    }

    // ==================== 提醒测试功能 ====================

    /**
     * 测试立即提醒（5秒后触发）
     */
    fun testImmediateReminder() {
        viewModelScope.launch {
            try {
                val currentSchedule = scheduleRepository.getCurrentSchedule()
                if (currentSchedule == null) {
                    android.widget.Toast.makeText(context, "未找到当前课表，请先导入课表", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val courses = courseRepository.getAllCoursesBySchedule(currentSchedule.id).first()
                if (courses.isEmpty()) {
                    android.widget.Toast.makeText(context, "当前课表没有课程", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                // 取第一门课程，5秒后触发
                val testCourse = courses[0]
                val triggerTime = System.currentTimeMillis() + 5000
                val success = reminderScheduler.scheduleTestAlarm(
                    courseId = testCourse.id,
                    weekNumber = 1,
                    triggerTime = triggerTime,
                    isNextCourse = false
                )
                if (success) {
                    android.widget.Toast.makeText(context, "立即提醒测试成功，5秒后收到通知", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "立即提醒测试失败", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "测试失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 测试下节课提醒（5秒后触发）
     */
    fun testNextClassReminder() {
        viewModelScope.launch {
            try {
                val currentSchedule = scheduleRepository.getCurrentSchedule()
                if (currentSchedule == null) {
                    android.widget.Toast.makeText(context, "未找到当前课表，请先导入课表", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val courses = courseRepository.getAllCoursesBySchedule(currentSchedule.id).first()
                if (courses.size < 2) {
                    android.widget.Toast.makeText(context, "至少需要2门课程才能测试下节课提醒", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val currentCourse = courses[0]
                val nextCourse = courses[1]
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
                    android.widget.Toast.makeText(context, "下节课提醒测试成功，5秒后收到通知", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "下节课提醒测试失败", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "测试失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 测试上课提醒（5秒后触发）
     */
    fun testCourseStartReminder() {
        viewModelScope.launch {
            try {
                val currentSchedule = scheduleRepository.getCurrentSchedule()
                if (currentSchedule == null) {
                    android.widget.Toast.makeText(context, "未找到当前课表，请先导入课表", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val courses = courseRepository.getAllCoursesBySchedule(currentSchedule.id).first()
                if (courses.isEmpty()) {
                    android.widget.Toast.makeText(context, "当前课表没有课程", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val testCourse = courses[0]
                val triggerTime = System.currentTimeMillis() + 5000
                val success = reminderScheduler.scheduleTestAlarm(
                    courseId = testCourse.id,
                    weekNumber = 1,
                    triggerTime = triggerTime,
                    isNextCourse = false
                )
                if (success) {
                    android.widget.Toast.makeText(context, "上课提醒测试成功，5秒后收到通知", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "上课提醒测试失败", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "测试失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 测试下课提醒
     */
    fun testCourseEndReminder() {
        viewModelScope.launch {
            android.widget.Toast.makeText(context, "下课提醒功能正在开发中", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 测试所有提醒功能（逐个执行）
     */
    fun testAllReminders() {
        viewModelScope.launch {
            try {
                android.widget.Toast.makeText(context, "开始测试所有提醒功能...", android.widget.Toast.LENGTH_SHORT).show()
                kotlinx.coroutines.delay(1000)
                testImmediateReminder()
                kotlinx.coroutines.delay(2000)
                testNextClassReminder()
                kotlinx.coroutines.delay(2000)
                testCourseStartReminder()
                kotlinx.coroutines.delay(2000)
                android.widget.Toast.makeText(context, "所有提醒测试完成", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "批量测试失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 运行提醒诊断，结果复制到剪贴板
     */
    fun runReminderDiagnostic() {
        viewModelScope.launch {
            try {
                val results = ReminderDiagnostic.runDiagnostic(context)
                val summary = ReminderDiagnostic.getDiagnosticSummary(results)
                // 构建诊断报告
                val report = StringBuilder()
                report.appendLine(summary)
                report.appendLine()
                results.forEach { result ->
                    val statusIcon = if (result.status) "OK" else "FAIL"
                    report.appendLine("[$statusIcon] ${result.item}")
                    report.appendLine("   ${result.description}")
                    if (result.suggestion.isNotEmpty()) {
                        report.appendLine("   建议：${result.suggestion}")
                    }
                    report.appendLine()
                }
                // 复制到剪贴板
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("提醒诊断报告", report.toString())
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(context, "$summary\n详细报告已复制到剪贴板", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "诊断失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
