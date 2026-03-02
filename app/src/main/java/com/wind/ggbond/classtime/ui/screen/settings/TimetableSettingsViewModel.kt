package com.wind.ggbond.classtime.ui.screen.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * 课程表设置 ViewModel
 * ✅ 使用 DataStore 统一管理周末显示设置，与 SettingsViewModel 保持同步
 */
@HiltViewModel
class TimetableSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {
    
    // 使用统一的 DataStore 管理器
    private val settingsDataStore = DataStoreManager.getSettingsDataStore(context)
    
    private val _semesterStartDate = MutableStateFlow<LocalDate?>(null)
    val semesterStartDate: StateFlow<LocalDate?> = _semesterStartDate.asStateFlow()
    
    private val _currentWeekNumber = MutableStateFlow<Int?>(null)
    val currentWeekNumber: StateFlow<Int?> = _currentWeekNumber.asStateFlow()
    
    private val _totalWeeks = MutableStateFlow<Int?>(null)
    val totalWeeks: StateFlow<Int?> = _totalWeeks.asStateFlow()
    
    // ✅ 使用 DataStore 管理周末显示设置，确保与 SettingsViewModel 同步
    private val _showWeekend = MutableStateFlow(true)
    val showWeekend: StateFlow<Boolean> = _showWeekend.asStateFlow()
    
    private val _showNonCurrentWeekCourses = MutableStateFlow(true)
    val showNonCurrentWeekCourses: StateFlow<Boolean> = _showNonCurrentWeekCourses.asStateFlow()
    
    init {
        loadSettings()
        
        // ✅ 持续观察 DataStore，确保实时同步
        viewModelScope.launch {
            settingsDataStore.data
                .map { it[DataStoreManager.SettingsKeys.SHOW_WEEKEND_KEY] ?: true }
                .distinctUntilChanged()
                .collect { value ->
                    _showWeekend.value = value
                }
        }
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            // 从课表加载学期时间信息
            scheduleRepository.getCurrentScheduleFlow().collect { schedule ->
                schedule?.let {
                    _semesterStartDate.value = it.startDate
                    _totalWeeks.value = it.totalWeeks
                }
            }
        }
        
        // 加载周末显示设置
        viewModelScope.launch {
            val preferences = settingsDataStore.data.first()
            _showWeekend.value = preferences[DataStoreManager.SettingsKeys.SHOW_WEEKEND_KEY] ?: true
        }
    }
    
    /**
     * ✅ 更新周末显示设置，保存到 DataStore
     */
    fun updateShowWeekend(enabled: Boolean) {
        viewModelScope.launch {
            android.util.Log.d("TimetableSettingsVM", "updateShowWeekend called with: $enabled")
            settingsDataStore.edit { preferences ->
                preferences[DataStoreManager.SettingsKeys.SHOW_WEEKEND_KEY] = enabled
            }
            _showWeekend.value = enabled
            android.util.Log.d("TimetableSettingsVM", "显示周末已更新为: ${_showWeekend.value}")
        }
    }
    
    fun updateShowNonCurrentWeekCourses(enabled: Boolean) {
        _showNonCurrentWeekCourses.value = enabled
        // TODO: 保存到DataStore
    }
}

