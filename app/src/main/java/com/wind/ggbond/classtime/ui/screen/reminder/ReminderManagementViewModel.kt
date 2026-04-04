package com.wind.ggbond.classtime.ui.screen.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.Reminder
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ReminderRepository
import com.wind.ggbond.classtime.service.contract.IAlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * 按日期分组的提醒数据
 */
data class GroupedReminders(
    val dateGroups: Map<java.time.LocalDate, List<Reminder>>
)

/**
 * 筛选标签类型
 */
enum class ReminderFilterType {
    ALL,        // 全部
    TODAY,      // 今天
    THIS_WEEK,  // 本周
    EXPIRED     // 已过期
}

/**
 * 提醒管理ViewModel
 */
@HiltViewModel
class ReminderManagementViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val courseRepository: CourseRepository,
    private val reminderScheduler: IAlarmScheduler
) : ViewModel() {
    
    // 全部提醒（未筛选）
    private val _allReminders = MutableStateFlow<List<Reminder>>(emptyList())
    
    // 筛选后的提醒列表
    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders.asStateFlow()
    
    // 课程信息映射（courseId -> Course）
    private val _courseMap = MutableStateFlow<Map<Long, Course>>(emptyMap())
    val courseMap: StateFlow<Map<Long, Course>> = _courseMap.asStateFlow()
    
    // 统计信息
    private val _stats = MutableStateFlow<ReminderStats?>(null)
    val stats: StateFlow<ReminderStats?> = _stats.asStateFlow()
    
    // 当前筛选类型
    private val _filterType = MutableStateFlow(ReminderFilterType.ALL)
    val filterType: StateFlow<ReminderFilterType> = _filterType.asStateFlow()
    
    // 删除对话框状态
    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()
    
    // 清除全部对话框状态
    private val _showClearAllDialog = MutableStateFlow(false)
    val showClearAllDialog: StateFlow<Boolean> = _showClearAllDialog.asStateFlow()
    
    // 当前选中的提醒
    private val _selectedReminder = MutableStateFlow<Reminder?>(null)
    val selectedReminder: StateFlow<Reminder?> = _selectedReminder.asStateFlow()

    // 按日期分组的提醒数据（预计算，避免Composable内重复分组）
    private val _groupedReminders = MutableStateFlow(GroupedReminders(emptyMap()))
    val groupedReminders: StateFlow<GroupedReminders> = _groupedReminders.asStateFlow()
    
    init {
        loadReminders()
        loadStats()
    }
    
    /**
     * 加载提醒列表（含已禁用），同时加载课程信息
     */
    private fun loadReminders() {
        viewModelScope.launch {
            reminderRepository.getAllFlow().collect { reminders ->
                _allReminders.value = reminders.sortedBy { it.triggerTime }
                // 加载关联的课程信息
                loadCourseInfo(reminders)
                // 应用筛选
                applyFilter()
            }
        }
    }
    
    /**
     * 加载课程信息映射
     */
    private suspend fun loadCourseInfo(reminders: List<Reminder>) {
        // 提取不重复的courseId
        val courseIds = reminders.map { it.courseId }.distinct()
        val map = mutableMapOf<Long, Course>()
        courseIds.forEach { courseId ->
            courseRepository.getCourseById(courseId)?.let { course ->
                map[courseId] = course
            }
        }
        _courseMap.value = map
    }
    
    /**
     * 加载统计信息
     */
    private fun loadStats() {
        viewModelScope.launch {
            val stats = reminderScheduler.getReminderStats()
            _stats.value = ReminderStats(
                totalReminders = stats.totalReminders,
                activeReminders = stats.activeReminders,
                todayReminders = stats.todayReminders,
                upcomingReminders = stats.upcomingReminders
            )
        }
    }
    
    /**
     * 切换筛选类型
     */
    fun setFilter(type: ReminderFilterType) {
        _filterType.value = type
        applyFilter()
    }
    
    /**
     * 根据当前筛选类型过滤提醒列表
     */
    private fun applyFilter() {
        val now = System.currentTimeMillis()
        val todayStart = LocalDate.now().atStartOfDay()
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayEnd = todayStart + 24 * 60 * 60 * 1000
        val weekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY).atStartOfDay()
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val weekEnd = weekStart + 7 * 24 * 60 * 60 * 1000

        val filtered = when (_filterType.value) {
            ReminderFilterType.ALL -> _allReminders.value
            ReminderFilterType.TODAY -> _allReminders.value.filter {
                it.triggerTime in todayStart until todayEnd
            }
            ReminderFilterType.THIS_WEEK -> _allReminders.value.filter {
                it.triggerTime in weekStart until weekEnd
            }
            ReminderFilterType.EXPIRED -> _allReminders.value.filter {
                it.triggerTime < now
            }
        }
        _reminders.value = filtered

        // 预计算按日期分组的结果，避免Composable内重复计算
        val grouped = filtered.groupBy { reminder ->
            LocalDateTime.ofInstant(
                Instant.ofEpochMilli(reminder.triggerTime),
                ZoneId.systemDefault()
            ).toLocalDate()
        }.toSortedMap()
        _groupedReminders.value = GroupedReminders(grouped)
    }
    
    /**
     * 显示删除对话框
     */
    fun showDeleteDialog(reminder: Reminder) {
        _selectedReminder.value = reminder
        _showDeleteDialog.value = true
    }
    
    /**
     * 隐藏删除对话框
     */
    fun hideDeleteDialog() {
        _showDeleteDialog.value = false
        _selectedReminder.value = null
    }
    
    /**
     * 显示清除全部确认对话框
     */
    fun showClearAllDialog() {
        _showClearAllDialog.value = true
    }
    
    /**
     * 隐藏清除全部确认对话框
     */
    fun hideClearAllDialog() {
        _showClearAllDialog.value = false
    }
    
    /**
     * 确认清除全部提醒
     */
    fun confirmClearAll() {
        viewModelScope.launch {
            reminderScheduler.cancelAllReminders()
            _showClearAllDialog.value = false
            loadStats()
        }
    }
    
    /**
     * 删除提醒
     */
    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            reminderRepository.delete(reminder)
            loadStats()
        }
    }
    
    /**
     * 切换提醒状态
     */
    fun toggleReminder(reminder: Reminder) {
        viewModelScope.launch {
            val updatedReminder = reminder.copy(isEnabled = !reminder.isEnabled)
            reminderRepository.update(updatedReminder)
            
            reminderScheduler.toggleSingleReminder(updatedReminder, updatedReminder.isEnabled)
            
            loadReminders()
            loadStats()
        }
    }
    
    /**
     * 清理过期提醒
     */
    fun cleanExpiredReminders() {
        viewModelScope.launch {
            reminderScheduler.cleanExpiredReminders()
            loadStats()
        }
    }
}


