package com.wind.ggbond.classtime.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.data.local.database.CourseDatabase
import com.wind.ggbond.classtime.util.AutoUpdateConfig
import com.wind.ggbond.classtime.util.AutoUpdateManager
import com.wind.ggbond.classtime.util.UpdateLogEntry
import com.wind.ggbond.classtime.util.extractor.SchoolExtractorFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutoUpdateViewModel @Inject constructor(
    application: Application,
    private val database: CourseDatabase,
    private val extractorFactory: SchoolExtractorFactory
) : AndroidViewModel(application) {
    
    // 通过 Hilt 注入 SchoolExtractorFactory，避免手动构造 80+ 个 Extractor
    private val autoUpdateManager = AutoUpdateManager(application, database, extractorFactory)
    
    private val _config = MutableStateFlow(AutoUpdateConfig())
    val config: StateFlow<AutoUpdateConfig> = _config.asStateFlow()
    
    private val _updateLogs = MutableStateFlow<List<UpdateLogEntry>>(emptyList())
    val updateLogs: StateFlow<List<UpdateLogEntry>> = _updateLogs.asStateFlow()
    
    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()
    
    private val _showIntervalDialog = MutableStateFlow(false)
    val showIntervalDialog: StateFlow<Boolean> = _showIntervalDialog.asStateFlow()
    
    private val _showClearStatsDialog = MutableStateFlow(false)
    val showClearStatsDialog: StateFlow<Boolean> = _showClearStatsDialog.asStateFlow()
    
    init {
        refresh()
    }
    
    /**
     * 刷新数据
     */
    fun refresh() {
        _config.value = autoUpdateManager.getConfig()
        _updateLogs.value = autoUpdateManager.getUpdateLogs()
    }
    
    /**
     * 切换启用状态
     */
    fun toggleEnabled(enabled: Boolean) {
        val newConfig = _config.value.copy(enabled = enabled)
        autoUpdateManager.updateConfig(newConfig)
        _config.value = newConfig
    }
    
    /**
     * 设置更新间隔
     */
    fun setInterval(hours: Int) {
        val newConfig = _config.value.copy(minIntervalHours = hours)
        autoUpdateManager.updateConfig(newConfig)
        _config.value = newConfig
    }
    
    /**
     * 显示间隔选择对话框
     */
    fun showIntervalPicker() {
        _showIntervalDialog.value = true
    }
    
    /**
     * 隐藏间隔选择对话框
     */
    fun hideIntervalPicker() {
        _showIntervalDialog.value = false
    }
    
    /**
     * 立即更新
     */
    fun updateNow() {
        viewModelScope.launch {
            _isUpdating.value = true
            try {
                // 获取当前学校ID
                val schoolId = getCurrentSchoolId()
                if (schoolId != null) {
                    autoUpdateManager.updateNow(schoolId)
                    refresh()
                }
            } finally {
                _isUpdating.value = false
            }
        }
    }
    
    /**
     * 清空日志
     */
    fun clearLogs() {
        autoUpdateManager.clearLogs()
        _updateLogs.value = emptyList()
    }
    
    /**
     * 显示清空统计确认对话框
     */
    fun showClearStatsDialog() {
        _showClearStatsDialog.value = true
    }
    
    /**
     * 隐藏清空统计确认对话框
     */
    fun hideClearStatsDialog() {
        _showClearStatsDialog.value = false
    }
    
    /**
     * 清空统计数据
     */
    fun clearStatistics() {
        autoUpdateManager.clearStatistics()
        refresh()
    }
    
    /**
     * 获取当前学校ID
     */
    private fun getCurrentSchoolId(): String? {
        val prefs = getApplication<Application>().getSharedPreferences("app_prefs", Application.MODE_PRIVATE)
        return prefs.getString("current_school_id", null)
    }
}

