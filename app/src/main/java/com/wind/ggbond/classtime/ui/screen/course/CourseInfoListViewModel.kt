package com.wind.ggbond.classtime.ui.screen.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject
import android.util.Log

/**
 * 单个时间段数据模型
 * 表示一门课程在某个星期、某个节次的上课安排
 */
data class CourseTimeSlot(
    val id: Long,                           // 课程ID
    val dayOfWeek: Int,                     // 星期几（1=周一，7=周日）
    val startSection: Int,                  // 起始节次
    val sectionCount: Int,                  // 持续节次
    val classroom: String,                  // 教室
    val weeks: List<Int>,                   // 上课周次
    val reminderEnabled: Boolean,          // 是否启用提醒
    val reminderMinutes: Int                // 提前提醒分钟数
)

/**
 * 聚合课程数据模型
 * 将同一课程名称的所有时间段合并显示
 */
data class AggregatedCourse(
    val courseName: String,                 // 课程名称
    val teacher: String,                    // 教师
    val credit: Float,                      // 学分
    val color: String,                      // 课程颜色
    val timeSlots: List<CourseTimeSlot>,    // 所有时间段
    val isExpanded: Boolean = false         // UI状态：是否展开
)

/**
 * 课程信息列表 ViewModel
 * 负责管理当前课程表的所有课程数据
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CourseInfoListViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {
    
    companion object {
        private const val TAG = "CourseInfoListViewModel"
    }
    
    // 加载状态
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // 当前课程表
    private val _currentSchedule = MutableStateFlow<Schedule?>(null)
    val currentSchedule: StateFlow<Schedule?> = _currentSchedule.asStateFlow()
    
    // 展开状态管理
    private val _expandedCourseNames = MutableStateFlow<Set<String>>(emptySet())
    val expandedCourseNames: StateFlow<Set<String>> = _expandedCourseNames.asStateFlow()
    
    // 聚合课程列表
    val aggregatedCourses: StateFlow<List<AggregatedCourse>> = combine(
        _currentSchedule.filterNotNull(),
        _expandedCourseNames
    ) { schedule, expandedNames ->
        courseRepository.getAllCoursesBySchedule(schedule.id)
            .map { courses -> aggregateCourses(courses, expandedNames) }
    }.flatMapLatest { it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    init {
        loadCurrentSchedule()
    }
    
    /**
     * 加载当前课程表信息
     * 修复：避免嵌套协程，使用flatMapLatest处理数据流
     */
    private fun loadCurrentSchedule() {
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                // 合并课程表和学期信息的Flow
                scheduleRepository.getCurrentScheduleFlow()
                    .flatMapLatest { schedule ->
                        _currentSchedule.value = schedule
                        
                        if (schedule != null) {
                            // 课表已包含学期时间信息，无需额外加载
                            _isLoading.value = false
                            flowOf(Unit)
                        } else {
                            // 如果没有当前课表，尝试获取第一个
                            try {
                                val firstSchedule = scheduleRepository.getAllSchedules()
                                    .first()
                                    .firstOrNull()
                                
                                if (firstSchedule != null) {
                                    _currentSchedule.value = firstSchedule
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error loading fallback schedule", e)
                            } finally {
                                _isLoading.value = false
                            }
                            
                            flowOf(Unit)
                        }
                    }
                    .catch { e ->
                        Log.e(TAG, "Error in schedule flow", e)
                        _isLoading.value = false
                    }
                    .collect { /* 消费Flow以触发数据加载 */ }
                    
            } catch (e: Exception) {
                Log.e(TAG, "Critical error loading schedule", e)
                _currentSchedule.value = null
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 刷新数据
     */
    fun refresh() {
        loadCurrentSchedule()
    }
    
    /**
     * 切换课程展开状态
     */
    fun toggleCourseExpanded(courseName: String) {
        val currentExpanded = _expandedCourseNames.value
        _expandedCourseNames.value = if (currentExpanded.contains(courseName)) {
            currentExpanded - courseName
        } else {
            currentExpanded + courseName
        }
    }
    
    /**
     * 将Course列表聚合为AggregatedCourse列表
     * 同一课程名称的所有时间段会合并到一个AggregatedCourse中
     */
    private fun aggregateCourses(courses: List<Course>, expandedNames: Set<String>): List<AggregatedCourse> {
        // 按课程名称分组
        val groupedCourses = courses.groupBy { it.courseName }
        
        return groupedCourses.map { (courseName, courseList) ->
            // 获取该课程的基本信息（以第一个为准）
            val firstCourse = courseList.first()
            
            // 转换时间段
            val timeSlots = courseList.map { course ->
                CourseTimeSlot(
                    id = course.id,
                    dayOfWeek = course.dayOfWeek,
                    startSection = course.startSection,
                    sectionCount = course.sectionCount,
                    classroom = course.classroom,
                    weeks = course.weeks,
                    reminderEnabled = course.reminderEnabled,
                    reminderMinutes = course.reminderMinutes
                )
            }.sortedWith(compareBy<CourseTimeSlot> { it.dayOfWeek }.thenBy { it.startSection })
            
            AggregatedCourse(
                courseName = courseName,
                teacher = firstCourse.teacher,
                credit = firstCourse.credit,
                color = firstCourse.color,
                timeSlots = timeSlots,
                isExpanded = expandedNames.contains(courseName)
            )
        }.sortedBy { it.courseName }
    }
}
