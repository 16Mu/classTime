package com.wind.ggbond.classtime.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Inject

/**
 * 课表管理 ViewModel（原学期管理，已合并到课表）
 */
@HiltViewModel
class SemesterManagementViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {
    
    // 课表列表（包含学期时间信息）
    private val _schedules = MutableStateFlow<List<Schedule>>(emptyList())
    val schedules: StateFlow<List<Schedule>> = _schedules.asStateFlow()
    
    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()
    
    private val _showEditDialog = MutableStateFlow(false)
    val showEditDialog: StateFlow<Boolean> = _showEditDialog.asStateFlow()
    
    // 编辑中的课表
    private val _editingSchedule = MutableStateFlow<Schedule?>(null)
    val editingSchedule: StateFlow<Schedule?> = _editingSchedule.asStateFlow()
    
    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()
    
    // 待删除的课表
    private val _deletingSchedule = MutableStateFlow<Schedule?>(null)
    val deletingSchedule: StateFlow<Schedule?> = _deletingSchedule.asStateFlow()
    
    init {
        loadSchedules()
    }
    
    // 加载所有课表
    private fun loadSchedules() {
        viewModelScope.launch {
            scheduleRepository.getAllSchedules().collect { scheduleList ->
                _schedules.value = scheduleList
            }
        }
    }
    
    fun showAddDialog() {
        _showAddDialog.value = true
    }
    
    fun hideAddDialog() {
        _showAddDialog.value = false
    }
    
    fun showEditDialog(schedule: Schedule) {
        _editingSchedule.value = schedule
        _showEditDialog.value = true
    }
    
    fun hideEditDialog() {
        _showEditDialog.value = false
        _editingSchedule.value = null
    }
    
    fun showDeleteDialog(schedule: Schedule) {
        _deletingSchedule.value = schedule
        _showDeleteDialog.value = true
    }
    
    fun hideDeleteDialog() {
        _showDeleteDialog.value = false
        _deletingSchedule.value = null
    }
    
    // 添加课表（包含学期时间信息）
    fun addSchedule(name: String, startDate: LocalDate, endDate: LocalDate, totalWeeks: Int) {
        viewModelScope.launch {
            val schedule = Schedule(
                name = name,
                startDate = startDate,
                endDate = endDate,
                totalWeeks = totalWeeks,
                isCurrent = _schedules.value.isEmpty() // 如果是第一个课表，设为当前课表
            )
            scheduleRepository.insertSchedule(schedule)
            hideAddDialog()
        }
    }
    
    // 更新课表
    fun updateSchedule(schedule: Schedule) {
        viewModelScope.launch {
            scheduleRepository.updateSchedule(schedule)
            hideEditDialog()
        }
    }
    
    // 删除课表
    fun deleteSchedule(schedule: Schedule) {
        viewModelScope.launch {
            scheduleRepository.deleteSchedule(schedule)
            hideDeleteDialog()
        }
    }
    
    // 设置当前课表
    fun setCurrentSchedule(schedule: Schedule) {
        viewModelScope.launch {
            scheduleRepository.setCurrentSchedule(schedule.id)
            AppLogger.d("ScheduleVM", "已切换到课表: ${schedule.name}")
        }
    }
}
