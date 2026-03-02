package com.wind.ggbond.classtime.ui.screen.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import com.wind.ggbond.classtime.data.local.entity.AutoUpdateLog
import com.wind.ggbond.classtime.data.repository.AutoUpdateLogRepository
import com.wind.ggbond.classtime.data.repository.LogStatistics
import com.wind.ggbond.classtime.util.ScheduledUpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 自动更新设置页面 ViewModel
 */
@HiltViewModel
class AutoUpdateSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logRepository: AutoUpdateLogRepository,
    private val scheduledUpdateManager: ScheduledUpdateManager
) : ViewModel() {
    
    private val settingsDataStore = DataStoreManager.getSettingsDataStore(context)
    
    companion object {
        private val AUTO_UPDATE_ENABLED_KEY = DataStoreManager.SettingsKeys.AUTO_UPDATE_ENABLED_KEY
        private val AUTO_UPDATE_INTERVAL_HOURS_KEY = DataStoreManager.SettingsKeys.AUTO_UPDATE_INTERVAL_HOURS_KEY
        private val INTERVAL_UPDATE_ENABLED_KEY = DataStoreManager.SettingsKeys.INTERVAL_UPDATE_ENABLED_KEY
        private val SCHEDULED_UPDATE_ENABLED_KEY = DataStoreManager.SettingsKeys.SCHEDULED_UPDATE_ENABLED_KEY
        private val SCHEDULED_UPDATE_TIME_KEY = DataStoreManager.SettingsKeys.SCHEDULED_UPDATE_TIME_KEY
        private const val DEFAULT_AUTO_UPDATE_ENABLED = DataStoreManager.SettingsKeys.DEFAULT_AUTO_UPDATE_ENABLED
        private const val DEFAULT_AUTO_UPDATE_INTERVAL_HOURS = DataStoreManager.SettingsKeys.DEFAULT_AUTO_UPDATE_INTERVAL_HOURS
        private const val DEFAULT_INTERVAL_UPDATE_ENABLED = DataStoreManager.SettingsKeys.DEFAULT_INTERVAL_UPDATE_ENABLED
        private const val DEFAULT_SCHEDULED_UPDATE_ENABLED = DataStoreManager.SettingsKeys.DEFAULT_SCHEDULED_UPDATE_ENABLED
        private const val DEFAULT_SCHEDULED_UPDATE_TIME = DataStoreManager.SettingsKeys.DEFAULT_SCHEDULED_UPDATE_TIME
    }
    
    // 自动更新开关
    private val _autoUpdateEnabled = MutableStateFlow(DEFAULT_AUTO_UPDATE_ENABLED)
    val autoUpdateEnabled: StateFlow<Boolean> = _autoUpdateEnabled.asStateFlow()
    
    // 间隔更新开关
    private val _intervalUpdateEnabled = MutableStateFlow(DEFAULT_INTERVAL_UPDATE_ENABLED)
    val intervalUpdateEnabled: StateFlow<Boolean> = _intervalUpdateEnabled.asStateFlow()
    
    // 更新间隔（小时）
    private val _updateIntervalHours = MutableStateFlow(DEFAULT_AUTO_UPDATE_INTERVAL_HOURS)
    val updateIntervalHours: StateFlow<Int> = _updateIntervalHours.asStateFlow()
    
    // 定时更新开关
    private val _scheduledUpdateEnabled = MutableStateFlow(DEFAULT_SCHEDULED_UPDATE_ENABLED)
    val scheduledUpdateEnabled: StateFlow<Boolean> = _scheduledUpdateEnabled.asStateFlow()
    
    // 定时更新时间
    private val _scheduledUpdateTime = MutableStateFlow(DEFAULT_SCHEDULED_UPDATE_TIME)
    val scheduledUpdateTime: StateFlow<String> = _scheduledUpdateTime.asStateFlow()
    
    // 更新日志列表
    private val _logs = MutableStateFlow<List<AutoUpdateLog>>(emptyList())
    val logs: StateFlow<List<AutoUpdateLog>> = _logs.asStateFlow()
    
    // 统计信息
    private val _statistics = MutableStateFlow<LogStatistics?>(null)
    val statistics: StateFlow<LogStatistics?> = _statistics.asStateFlow()
    
    // 显示清空日志确认对话框
    private val _showClearLogsDialog = MutableStateFlow(false)
    val showClearLogsDialog: StateFlow<Boolean> = _showClearLogsDialog.asStateFlow()
    
    // 显示更新间隔选择对话框
    private val _showIntervalDialog = MutableStateFlow(false)
    val showIntervalDialog: StateFlow<Boolean> = _showIntervalDialog.asStateFlow()
    
    // 显示定时时间选择对话框
    private val _showScheduledTimeDialog = MutableStateFlow(false)
    val showScheduledTimeDialog: StateFlow<Boolean> = _showScheduledTimeDialog.asStateFlow()
    
    init {
        loadSettings()
        loadLogs()
        loadStatistics()
    }
    
    /**
     * 加载设置
     */
    private fun loadSettings() {
        viewModelScope.launch {
            val preferences = settingsDataStore.data.first()
            _autoUpdateEnabled.value = preferences[AUTO_UPDATE_ENABLED_KEY] ?: DEFAULT_AUTO_UPDATE_ENABLED
            _intervalUpdateEnabled.value = preferences[INTERVAL_UPDATE_ENABLED_KEY] ?: DEFAULT_INTERVAL_UPDATE_ENABLED
            _updateIntervalHours.value = preferences[AUTO_UPDATE_INTERVAL_HOURS_KEY] ?: DEFAULT_AUTO_UPDATE_INTERVAL_HOURS
            _scheduledUpdateEnabled.value = preferences[SCHEDULED_UPDATE_ENABLED_KEY] ?: DEFAULT_SCHEDULED_UPDATE_ENABLED
            _scheduledUpdateTime.value = preferences[SCHEDULED_UPDATE_TIME_KEY] ?: DEFAULT_SCHEDULED_UPDATE_TIME
        }
    }
    
    /**
     * 加载日志列表（最近50条）
     */
    private fun loadLogs() {
        viewModelScope.launch {
            logRepository.getRecentLogs(50).collect { logs ->
                _logs.value = logs
            }
        }
    }
    
    /**
     * 加载统计信息
     */
    private fun loadStatistics() {
        viewModelScope.launch {
            val stats = logRepository.getStatistics()
            _statistics.value = stats
        }
    }
    
    /**
     * 更新自动更新开关
     */
    fun updateAutoUpdateEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.edit { preferences ->
                preferences[AUTO_UPDATE_ENABLED_KEY] = enabled
            }
            _autoUpdateEnabled.value = enabled
        }
    }
    
    /**
     * 更新更新间隔
     */
    fun updateIntervalHours(hours: Int) {
        viewModelScope.launch {
            settingsDataStore.edit { preferences ->
                preferences[AUTO_UPDATE_INTERVAL_HOURS_KEY] = hours
            }
            _updateIntervalHours.value = hours
        }
    }
    
    /**
     * 显示清空日志对话框
     */
    fun showClearLogsDialog() {
        _showClearLogsDialog.value = true
    }
    
    /**
     * 隐藏清空日志对话框
     */
    fun hideClearLogsDialog() {
        _showClearLogsDialog.value = false
    }
    
    /**
     * 清空所有日志
     */
    fun clearAllLogs() {
        viewModelScope.launch {
            logRepository.clearAllLogs()
            loadStatistics()
        }
    }
    
    /**
     * 显示更新间隔对话框
     */
    fun showIntervalDialog() {
        _showIntervalDialog.value = true
    }
    
    /**
     * 隐藏更新间隔对话框
     */
    fun hideIntervalDialog() {
        _showIntervalDialog.value = false
    }
    
    /**
     * 更新间隔更新开关
     */
    fun updateIntervalUpdateEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.edit { preferences ->
                preferences[INTERVAL_UPDATE_ENABLED_KEY] = enabled
            }
            _intervalUpdateEnabled.value = enabled
        }
    }
    
    /**
     * 更新定时更新开关
     */
    fun updateScheduledUpdateEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.edit { preferences ->
                preferences[SCHEDULED_UPDATE_ENABLED_KEY] = enabled
            }
            _scheduledUpdateEnabled.value = enabled
            
            // 同步 WorkManager 任务
            scheduledUpdateManager.setScheduledUpdateEnabled(enabled)
        }
    }
    
    /**
     * 更新定时更新时间
     */
    fun updateScheduledUpdateTime(time: String) {
        viewModelScope.launch {
            settingsDataStore.edit { preferences ->
                preferences[SCHEDULED_UPDATE_TIME_KEY] = time
            }
            _scheduledUpdateTime.value = time
            
            // 同步 WorkManager 任务
            scheduledUpdateManager.updateScheduledTime(time)
        }
    }
    
    /**
     * 显示定时时间选择对话框
     */
    fun showScheduledTimeDialog() {
        _showScheduledTimeDialog.value = true
    }
    
    /**
     * 隐藏定时时间选择对话框
     */
    fun hideScheduledTimeDialog() {
        _showScheduledTimeDialog.value = false
    }
    
    /**
     * 刷新日志和统计
     */
    fun refresh() {
        loadLogs()
        loadStatistics()
    }
}


