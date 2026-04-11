package com.wind.ggbond.classtime.ui.screen.coursecolor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.data.local.entity.Course
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CourseColorItem(
    val courseName: String,
    val color: String,
    val timeInfo: String
)

@HiltViewModel
class ManualColorAdjustmentViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    private val _courseItems = MutableStateFlow<List<CourseColorItem>>(emptyList())
    val courseItems: StateFlow<List<CourseColorItem>> = _courseItems.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveResult = MutableStateFlow<String?>(null)
    val saveResult: StateFlow<String?> = _saveResult.asStateFlow()

    private val _pendingColorChanges = MutableStateFlow<Map<String, String>>(emptyMap())
    val pendingColorChanges: StateFlow<Map<String, String>> = _pendingColorChanges.asStateFlow()

    init {
        loadCourses()
    }

    private fun loadCourses() {
        viewModelScope.launch {
            try {
                val schedule = scheduleRepository.getCurrentSchedule()
                if (schedule != null) {
                    courseRepository.getAllCoursesBySchedule(schedule.id).collect { list ->
                        val dayNames = mapOf(1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四", 5 to "周五", 6 to "周六", 7 to "周日")
                        val grouped = list.groupBy { it.courseName }
                        _courseItems.value = grouped.map { (name, courses) ->
                            val timeInfos = courses.map { course ->
                                "${dayNames[course.dayOfWeek] ?: "周${course.dayOfWeek}"} ${course.startSection}-${course.startSection + course.sectionCount - 1}节"
                            }.distinct().sorted().joinToString("、")
                            CourseColorItem(
                                courseName = name,
                                color = courses.first().color,
                                timeInfo = timeInfos
                            )
                        }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    fun updateCourseColor(courseName: String, newColor: String) {
        _pendingColorChanges.value = _pendingColorChanges.value.toMutableMap().apply {
            this[courseName] = newColor
        }
        _courseItems.value = _courseItems.value.map {
            if (it.courseName == courseName) it.copy(color = newColor) else it
        }
    }

    fun saveAllChanges() {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val schedule = scheduleRepository.getCurrentSchedule()
                if (schedule == null) {
                    _saveResult.value = "未找到当前课表"
                    return@launch
                }
                val changes = _pendingColorChanges.value
                if (changes.isEmpty()) {
                    _saveResult.value = "没有需要保存的修改"
                    return@launch
                }
                val courses = courseRepository.getAllCoursesBySchedule(schedule.id).first()
                var count = 0
                courses.forEach { course ->
                    val newColor = changes[course.courseName]
                    if (newColor != null && course.color != newColor) {
                        courseRepository.updateCourse(course.copy(color = newColor))
                        count++
                    }
                }
                _pendingColorChanges.value = emptyMap()
                _saveResult.value = "已保存 $count 门课程的颜色修改"
            } catch (e: Exception) {
                _saveResult.value = "保存失败: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearSaveResult() {
        _saveResult.value = null
    }
}
