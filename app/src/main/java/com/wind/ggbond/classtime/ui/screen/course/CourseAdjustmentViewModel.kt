package com.wind.ggbond.classtime.ui.screen.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.CourseAdjustment
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.domain.usecase.AdjustmentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Inject

@HiltViewModel
class CourseAdjustmentViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val adjustmentUseCase: AdjustmentUseCase
) : ViewModel() {
    
    private val _course = MutableStateFlow<Course?>(null)
    val course: StateFlow<Course?> = _course.asStateFlow()
    
    private val _originalWeekNumber = MutableStateFlow(1)
    val originalWeekNumber: StateFlow<Int> = _originalWeekNumber.asStateFlow()
    
    private val _newWeekNumber = MutableStateFlow(1)
    val newWeekNumber: StateFlow<Int> = _newWeekNumber.asStateFlow()
    
    private val _newDayOfWeek = MutableStateFlow(1)
    val newDayOfWeek: StateFlow<Int> = _newDayOfWeek.asStateFlow()
    
    private val _newStartSection = MutableStateFlow(1)
    val newStartSection: StateFlow<Int> = _newStartSection.asStateFlow()
    
    private val _newSectionCount = MutableStateFlow(2)
    val newSectionCount: StateFlow<Int> = _newSectionCount.asStateFlow()
    
    private val _newClassroom = MutableStateFlow("")
    val newClassroom: StateFlow<String> = _newClassroom.asStateFlow()
    
    private val _reason = MutableStateFlow("")
    val reason: StateFlow<String> = _reason.asStateFlow()
    
    private val _showWeekSelector = MutableStateFlow(false)
    val showWeekSelector: StateFlow<Boolean> = _showWeekSelector.asStateFlow()
    
    private val _hasConflict = MutableStateFlow(false)
    val hasConflict: StateFlow<Boolean> = _hasConflict.asStateFlow()
    
    private val _conflictMessage = MutableStateFlow("")
    val conflictMessage: StateFlow<String> = _conflictMessage.asStateFlow()
    
    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()
    
    private var conflictCheckJob: Job? = null
    
    sealed class SaveState {
        object Idle : SaveState()
        object Saving : SaveState()
        data class Success(val message: String) : SaveState()
        data class Error(val message: String) : SaveState()
    }
    
    fun loadCourse(courseId: Long, weekNumber: Int) {
        viewModelScope.launch {
            try {
                val loadedCourse = courseRepository.getCourseById(courseId)
                _course.value = loadedCourse
                
                _originalWeekNumber.value = weekNumber
                
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
    
    fun setNewWeekNumber(weekNumber: Int) {
        _newWeekNumber.value = weekNumber
        checkConflict()
    }
    
    fun setNewDayOfWeek(dayOfWeek: Int) {
        _newDayOfWeek.value = dayOfWeek
        checkConflict()
    }
    
    fun setNewStartSection(startSection: Int) {
        _newStartSection.value = startSection
        checkConflict()
    }
    
    fun setNewSectionCount(sectionCount: Int) {
        _newSectionCount.value = sectionCount
        checkConflict()
    }
    
    fun setNewClassroom(classroom: String) {
        _newClassroom.value = classroom
    }
    
    fun setReason(reason: String) {
        _reason.value = reason
    }
    
    fun setShowWeekSelector(show: Boolean) {
        _showWeekSelector.value = show
    }
    
    private fun checkConflict() {
        conflictCheckJob?.cancel()
        conflictCheckJob = viewModelScope.launch {
            delay(300)
            try {
                val course = _course.value ?: return@launch
                
                val result = adjustmentUseCase.checkAdjustmentConflict(
                    scheduleId = course.scheduleId,
                    weekNumber = _newWeekNumber.value,
                    dayOfWeek = _newDayOfWeek.value,
                    startSection = _newStartSection.value,
                    sectionCount = _newSectionCount.value,
                    excludeCourseId = course.id
                )
                
                _hasConflict.value = result.hasConflict
                _conflictMessage.value = result.message
            } catch (e: Exception) {
                AppLogger.e("CourseAdjustmentVM", "检查冲突失败", e)
            }
        }
    }
    
    fun saveAdjustment() {
        viewModelScope.launch {
            try {
                _saveState.value = SaveState.Saving
                
                val course = _course.value
                if (course == null) {
                    _saveState.value = SaveState.Error("课程信息丢失")
                    return@launch
                }
                
                if (_hasConflict.value) {
                    _saveState.value = SaveState.Error("存在时间冲突，请选择其他时间")
                    return@launch
                }
                
                val adjustment = adjustmentUseCase.createAdjustment(
                    course = course,
                    originalWeekNumber = _originalWeekNumber.value,
                    newWeekNumber = _newWeekNumber.value,
                    newDayOfWeek = _newDayOfWeek.value,
                    newStartSection = _newStartSection.value,
                    newSectionCount = _newSectionCount.value,
                    newClassroom = _newClassroom.value,
                    reason = _reason.value
                )
                
                adjustmentUseCase.saveAdjustment(adjustment)
                
                _saveState.value = SaveState.Success("临时调课设置成功")
            } catch (e: Exception) {
                _saveState.value = SaveState.Error("保存失败: ${e.message}")
            }
        }
    }
    
    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }
}


