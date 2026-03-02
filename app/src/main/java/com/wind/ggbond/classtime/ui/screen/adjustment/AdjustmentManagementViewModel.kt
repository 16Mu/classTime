package com.wind.ggbond.classtime.ui.screen.adjustment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.CourseAdjustment
import com.wind.ggbond.classtime.data.repository.CourseAdjustmentRepository
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 调课记录管理 ViewModel
 */
@HiltViewModel
class AdjustmentManagementViewModel @Inject constructor(
    private val adjustmentRepository: CourseAdjustmentRepository,
    private val courseRepository: CourseRepository,
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {
    
    // 当前课表ID
    private val _currentScheduleId = MutableStateFlow<Long?>(null)
    
    // 所有调课记录
    private val _adjustments = MutableStateFlow<List<CourseAdjustment>>(emptyList())
    val adjustments: StateFlow<List<CourseAdjustment>> = _adjustments.asStateFlow()
    
    // 调课记录及对应的课程信息
    data class AdjustmentWithCourse(
        val adjustment: CourseAdjustment,
        val course: Course?
    )
    
    val adjustmentsWithCourses: StateFlow<List<AdjustmentWithCourse>> = combine(
        _adjustments,
        _currentScheduleId
    ) { adjustments, scheduleId ->
        if (scheduleId == null) return@combine emptyList()
        
        adjustments.map { adjustment ->
            val course = courseRepository.getCourseById(adjustment.originalCourseId)
            AdjustmentWithCourse(adjustment, course)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    // 操作状态
    private val _operationState = MutableStateFlow<OperationState>(OperationState.Idle)
    val operationState: StateFlow<OperationState> = _operationState.asStateFlow()
    
    sealed class OperationState {
        object Idle : OperationState()
        object Loading : OperationState()
        data class Success(val message: String) : OperationState()
        data class Error(val message: String) : OperationState()
    }
    
    init {
        loadCurrentSchedule()
    }
    
    private fun loadCurrentSchedule() {
        viewModelScope.launch {
            scheduleRepository.getCurrentScheduleFlow().collect { schedule ->
                _currentScheduleId.value = schedule?.id
                schedule?.id?.let { scheduleId ->
                    loadAdjustments(scheduleId)
                }
            }
        }
    }
    
    private fun loadAdjustments(scheduleId: Long) {
        viewModelScope.launch {
            adjustmentRepository.getAdjustmentsBySchedule(scheduleId).collect { list ->
                _adjustments.value = list.sortedByDescending { it.createdAt }
            }
        }
    }
    
    /**
     * 取消调课
     */
    fun cancelAdjustment(adjustment: CourseAdjustment) {
        viewModelScope.launch {
            try {
                _operationState.value = OperationState.Loading
                adjustmentRepository.cancelAdjustment(adjustment)
                _operationState.value = OperationState.Success("已取消调课")
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("取消失败: ${e.message}")
            }
        }
    }
    
    /**
     * 重置操作状态
     */
    fun resetOperationState() {
        _operationState.value = OperationState.Idle
    }
}


