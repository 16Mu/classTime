package com.wind.ggbond.classtime.ui.screen.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.CourseAdjustment
import com.wind.ggbond.classtime.data.repository.CourseAdjustmentRepository
import com.wind.ggbond.classtime.data.repository.CourseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 临时调课ViewModel
 */
@HiltViewModel
class CourseAdjustmentViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val adjustmentRepository: CourseAdjustmentRepository
) : ViewModel() {
    
    // 当前课程
    private val _course = MutableStateFlow<Course?>(null)
    val course: StateFlow<Course?> = _course.asStateFlow()
    
    // 原始周次（要调课的周次）
    private val _originalWeekNumber = MutableStateFlow(1)
    val originalWeekNumber: StateFlow<Int> = _originalWeekNumber.asStateFlow()
    
    // 新的周次
    private val _newWeekNumber = MutableStateFlow(1)
    val newWeekNumber: StateFlow<Int> = _newWeekNumber.asStateFlow()
    
    // 新的星期几
    private val _newDayOfWeek = MutableStateFlow(1)
    val newDayOfWeek: StateFlow<Int> = _newDayOfWeek.asStateFlow()
    
    // 新的开始节次
    private val _newStartSection = MutableStateFlow(1)
    val newStartSection: StateFlow<Int> = _newStartSection.asStateFlow()
    
    // 新的持续节数
    private val _newSectionCount = MutableStateFlow(2)
    val newSectionCount: StateFlow<Int> = _newSectionCount.asStateFlow()
    
    // 新的教室（调课时可能换教室）
    private val _newClassroom = MutableStateFlow("")
    val newClassroom: StateFlow<String> = _newClassroom.asStateFlow()
    
    // 调课原因
    private val _reason = MutableStateFlow("")
    val reason: StateFlow<String> = _reason.asStateFlow()
    
    // 是否显示周选择器
    private val _showWeekSelector = MutableStateFlow(false)
    val showWeekSelector: StateFlow<Boolean> = _showWeekSelector.asStateFlow()
    
    // 是否有时间冲突
    private val _hasConflict = MutableStateFlow(false)
    val hasConflict: StateFlow<Boolean> = _hasConflict.asStateFlow()
    
    // 冲突信息
    private val _conflictMessage = MutableStateFlow("")
    val conflictMessage: StateFlow<String> = _conflictMessage.asStateFlow()
    
    // 保存状态
    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()
    
    sealed class SaveState {
        object Idle : SaveState()
        object Saving : SaveState()
        data class Success(val message: String) : SaveState()
        data class Error(val message: String) : SaveState()
    }
    
    /**
     * 加载课程和初始化数据
     */
    fun loadCourse(courseId: Long, weekNumber: Int) {
        viewModelScope.launch {
            try {
                // 加载课程
                val loadedCourse = courseRepository.getCourseById(courseId)
                _course.value = loadedCourse
                
                // 设置原始周次
                _originalWeekNumber.value = weekNumber
                
                // 初始化新时间（默认和原课程相同）
                loadedCourse?.let { course ->
                    _newWeekNumber.value = weekNumber
                    _newDayOfWeek.value = course.dayOfWeek
                    _newStartSection.value = course.startSection
                    _newSectionCount.value = course.sectionCount
                    _newClassroom.value = course.classroom
                }
            } catch (e: Exception) {
                _saveState.value = SaveState.Error("加载失败: ${e.message}")
            }
        }
    }
    
    /**
     * 设置新的周次
     */
    fun setNewWeekNumber(weekNumber: Int) {
        _newWeekNumber.value = weekNumber
        checkConflict()
    }
    
    /**
     * 设置新的星期几
     */
    fun setNewDayOfWeek(dayOfWeek: Int) {
        _newDayOfWeek.value = dayOfWeek
        checkConflict()
    }
    
    /**
     * 设置新的开始节次
     */
    fun setNewStartSection(startSection: Int) {
        _newStartSection.value = startSection
        checkConflict()
    }
    
    /**
     * 设置新的持续节数
     */
    fun setNewSectionCount(sectionCount: Int) {
        _newSectionCount.value = sectionCount
        checkConflict()
    }
    
    /**
     * 设置新的教室（调课时可能换教室）
     */
    fun setNewClassroom(classroom: String) {
        _newClassroom.value = classroom
    }
    
    /**
     * 设置调课原因
     */
    fun setReason(reason: String) {
        _reason.value = reason
    }
    
    /**
     * 显示/隐藏周选择器
     */
    fun setShowWeekSelector(show: Boolean) {
        _showWeekSelector.value = show
    }
    
    /**
     * 检查时间冲突
     */
    private fun checkConflict() {
        viewModelScope.launch {
            try {
                val course = _course.value ?: return@launch
                
                // 检查新时间段是否有其他课程或调课记录
                val conflictingAdjustments = adjustmentRepository.checkNewTimeConflict(
                    scheduleId = course.scheduleId,
                    weekNumber = _newWeekNumber.value,
                    dayOfWeek = _newDayOfWeek.value,
                    startSection = _newStartSection.value,
                    sectionCount = _newSectionCount.value
                )
                
                // 检查是否有原始课程冲突
                val conflictingCourses = courseRepository.getCoursesInTimeRange(
                    scheduleId = course.scheduleId,
                    dayOfWeek = _newDayOfWeek.value,
                    startSection = _newStartSection.value,
                    endSection = _newStartSection.value + _newSectionCount.value
                ).filter { it.weeks.contains(_newWeekNumber.value) && it.id != course.id }
                
                if (conflictingAdjustments.isNotEmpty() || conflictingCourses.isNotEmpty()) {
                    _hasConflict.value = true
                    _conflictMessage.value = when {
                        conflictingCourses.isNotEmpty() -> 
                            "与课程《${conflictingCourses.first().courseName}》时间冲突"
                        else -> 
                            "与其他调课记录时间冲突"
                    }
                } else {
                    _hasConflict.value = false
                    _conflictMessage.value = ""
                }
            } catch (e: Exception) {
                android.util.Log.e("CourseAdjustmentVM", "检查冲突失败", e)
            }
        }
    }
    
    /**
     * 保存调课记录
     */
    fun saveAdjustment() {
        viewModelScope.launch {
            try {
                _saveState.value = SaveState.Saving
                
                val course = _course.value
                if (course == null) {
                    _saveState.value = SaveState.Error("课程信息丢失")
                    return@launch
                }
                
                // 检查是否有冲突
                if (_hasConflict.value) {
                    _saveState.value = SaveState.Error("存在时间冲突，请选择其他时间")
                    return@launch
                }
                
                // 创建调课记录
                val adjustment = CourseAdjustment(
                    originalCourseId = course.id,
                    scheduleId = course.scheduleId,
                    originalWeekNumber = _originalWeekNumber.value,
                    originalDayOfWeek = course.dayOfWeek,
                    originalStartSection = course.startSection,
                    originalSectionCount = course.sectionCount,
                    newWeekNumber = _newWeekNumber.value,
                    newDayOfWeek = _newDayOfWeek.value,
                    newStartSection = _newStartSection.value,
                    newSectionCount = _newSectionCount.value,
                    reason = _reason.value
                )
                
                // 保存到数据库
                val savedId = adjustmentRepository.saveAdjustment(adjustment)
                
                android.util.Log.d("CourseAdjustmentVM", "========== 调课记录已保存 ==========")
                android.util.Log.d("CourseAdjustmentVM", "✓ 调课记录ID: $savedId")
                android.util.Log.d("CourseAdjustmentVM", "✓ 课程: ${course.courseName}")
                android.util.Log.d("CourseAdjustmentVM", "✓ 原时间: 第${_originalWeekNumber.value}周 ${com.wind.ggbond.classtime.util.DateUtils.getDayOfWeekName(course.dayOfWeek)} 第${course.startSection}节")
                android.util.Log.d("CourseAdjustmentVM", "✓ 新时间: 第${_newWeekNumber.value}周 ${com.wind.ggbond.classtime.util.DateUtils.getDayOfWeekName(_newDayOfWeek.value)} 第${_newStartSection.value}节")
                android.util.Log.d("CourseAdjustmentVM", "====================================")
                
                _saveState.value = SaveState.Success("临时调课设置成功")
            } catch (e: Exception) {
                _saveState.value = SaveState.Error("保存失败: ${e.message}")
            }
        }
    }
    
    /**
     * 重置保存状态
     */
    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }
}


