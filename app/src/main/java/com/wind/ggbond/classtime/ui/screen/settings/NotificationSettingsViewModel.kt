package com.wind.ggbond.classtime.ui.screen.settings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.R
import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import com.wind.ggbond.classtime.service.contract.IAlarmScheduler
import com.wind.ggbond.classtime.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminderScheduler: IAlarmScheduler
) : ViewModel() {
    
    companion object {
        private const val TAG = "NotificationVM"
        private const val CHANNEL_ID = "course_reminder"
    }
    
    private val settingsDataStore = DataStoreManager.getSettingsDataStore(context)
    
    // 状态
    private val _reminderEnabled = MutableStateFlow(false)
    val reminderEnabled: StateFlow<Boolean> = _reminderEnabled.asStateFlow()
    
    private val _defaultReminderMinutes = MutableStateFlow(10)
    val defaultReminderMinutes: StateFlow<Int> = _defaultReminderMinutes.asStateFlow()
    
    private val _headsUpNotificationEnabled = MutableStateFlow(true)
    val headsUpNotificationEnabled: StateFlow<Boolean> = _headsUpNotificationEnabled.asStateFlow()
    
    private val _showNotificationPermissionDialog = MutableStateFlow(false)
    val showNotificationPermissionDialog: StateFlow<Boolean> = _showNotificationPermissionDialog.asStateFlow()
    
    private val _showReminderTestDialog = MutableStateFlow(false)
    val showReminderTestDialog: StateFlow<Boolean> = _showReminderTestDialog.asStateFlow()
    
    init {
        loadNotificationSettings()
    }
    
    private fun loadNotificationSettings() {
        viewModelScope.launch {
            try {
                val prefs = settingsDataStore.data.first()
                _reminderEnabled.value = prefs[DataStoreManager.SettingsKeys.REMINDER_ENABLED_KEY] ?: false
                _defaultReminderMinutes.value = prefs[DataStoreManager.SettingsKeys.DEFAULT_REMINDER_MINUTES_KEY] ?: 10
                _headsUpNotificationEnabled.value = prefs[DataStoreManager.SettingsKeys.HEADS_UP_NOTIFICATION_ENABLED_KEY] ?: true
                
                createNotificationChannelIfNeeded()
                AppLogger.d(TAG, "通知设置加载完成")
            } catch (e: Exception) {
                AppLogger.e(TAG, "加载通知设置失败", e)
            }
        }
    }
    
    fun updateReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.edit { it[DataStoreManager.SettingsKeys.REMINDER_ENABLED_KEY] = enabled }
            _reminderEnabled.value = enabled
            
            if (enabled) {
                reminderScheduler.rescheduleAllReminders()
                AppLogger.d(TAG, "提醒已开启并重新调度")
            } else {
                reminderScheduler.cancelAllReminders()
                AppLogger.d(TAG, "提醒已关闭并取消调度")
            }
        }
    }
    
    fun updateDefaultReminderMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsDataStore.edit { it[DataStoreManager.SettingsKeys.DEFAULT_REMINDER_MINUTES_KEY] = minutes }
            _defaultReminderMinutes.value = minutes
            if (_reminderEnabled.value) {
                reminderScheduler.rescheduleAllReminders()
            }
            AppLogger.d(TAG, "默认提醒时间: ${minutes}分钟")
        }
    }
    
    fun updateHeadsUpNotification(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.edit { it[DataStoreManager.SettingsKeys.HEADS_UP_NOTIFICATION_ENABLED_KEY] = enabled }
            _headsUpNotificationEnabled.value = enabled
            updateNotificationChannelImportance(enabled)
            AppLogger.d(TAG, "弹窗通知: $enabled")
        }
    }
    
    fun requestNotificationPermission() {
        _showNotificationPermissionDialog.value = true
    }
    
    fun dismissNotificationPermissionDialog() {
        _showNotificationPermissionDialog.value = false
    }
    
    fun testReminder() {
        _showReminderTestDialog.value = true
    }
    
    fun dismissReminderTestDialog() {
        _showReminderTestDialog.value = false
    }
    
    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            var channel = notificationManager.getNotificationChannel(CHANNEL_ID)
            
            if (channel == null) {
                channel = NotificationChannel(
                    CHANNEL_ID,
                    "课表提醒",
                    NotificationCompat.PRIORITY_DEFAULT
                ).apply {
                    description = "上课提醒与动态课表通知"
                    setSound(
                        Settings.System.DEFAULT_NOTIFICATION_URI,
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build()
                    )
                    enableVibration(true)
                    importance = if (_headsUpNotificationEnabled.value) {
                        NotificationManager.IMPORTANCE_HIGH
                    } else {
                        NotificationManager.IMPORTANCE_DEFAULT
                    }
                }
                notificationManager.createNotificationChannel(channel)
                AppLogger.d(TAG, "通知渠道已创建")
            }
        }
    }
    
    private fun updateNotificationChannelImportance(headsUpEnabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = notificationManager.getNotificationChannel(CHANNEL_ID)
            channel?.let {
                it.importance = if (headsUpEnabled) {
                    NotificationManager.IMPORTANCE_HIGH
                } else {
                    NotificationManager.IMPORTANCE_DEFAULT
                }
                it.setSound(null, null)
                it.enableVibration(true)
                it.vibrationPattern = longArrayOf(0, 400, 200, 400)
                notificationManager.createNotificationChannel(it)
                AppLogger.d(TAG, "通知渠道重要性已更新")
            }
        }
    }
}
