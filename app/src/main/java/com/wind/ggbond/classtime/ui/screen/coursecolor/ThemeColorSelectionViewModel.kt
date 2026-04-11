package com.wind.ggbond.classtime.ui.screen.coursecolor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.util.MonetColorPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeColorSelectionViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    private val _selectedThemeColor = MutableStateFlow(0xFF5B9BD5.toInt())
    val selectedThemeColor: StateFlow<Int> = _selectedThemeColor.asStateFlow()

    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    private val _previewColors = MutableStateFlow<List<String>>(emptyList())
    val previewColors: StateFlow<List<String>> = _previewColors.asStateFlow()

    private val _isApplying = MutableStateFlow(false)
    val isApplying: StateFlow<Boolean> = _isApplying.asStateFlow()

    private val _applyResult = MutableStateFlow<String?>(null)
    val applyResult: StateFlow<String?> = _applyResult.asStateFlow()

    init {
        loadCourses()
    }

    private fun loadCourses() {
        viewModelScope.launch {
            try {
                val schedule = scheduleRepository.getCurrentSchedule()
                if (schedule != null) {
                    courseRepository.getAllCoursesBySchedule(schedule.id).collect { list ->
                        _courses.value = list
                        updatePreview()
                    }
                }
            } catch (_: Exception) { }
        }
    }

    fun selectThemeColor(color: Int) {
        _selectedThemeColor.value = color
        updatePreview()
    }

    private fun updatePreview() {
        val seedColor = _selectedThemeColor.value
        val palette = MonetColorPalette.generatePalette(seedColor, MonetColorPalette.SaturationLevel.STANDARD)
        val uniqueNames = _courses.value.map { it.courseName }.distinct()
        _previewColors.value = uniqueNames.mapIndexed { index, _ ->
            palette[index % palette.size]
        }
    }

    fun applyThemeColors() {
        viewModelScope.launch {
            _isApplying.value = true
            try {
                val schedule = scheduleRepository.getCurrentSchedule()
                if (schedule == null) {
                    _applyResult.value = "未找到当前课表"
                    return@launch
                }
                val courses = _courses.value
                if (courses.isEmpty()) {
                    _applyResult.value = "当前课表没有课程"
                    return@launch
                }

                val seedColor = _selectedThemeColor.value
                val palette = MonetColorPalette.generatePalette(seedColor, MonetColorPalette.SaturationLevel.STANDARD)
                val uniqueNames = courses.map { it.courseName }.distinct()
                val colorMap = uniqueNames.mapIndexed { index, name ->
                    name to palette[index % palette.size]
                }.toMap()

                var count = 0
                courses.forEach { course ->
                    val newColor = colorMap[course.courseName] ?: course.color
                    if (course.color != newColor) {
                        courseRepository.updateCourse(course.copy(color = newColor))
                        count++
                    }
                }
                _applyResult.value = "已更新 $count 门课程的颜色"
            } catch (e: Exception) {
                _applyResult.value = "应用失败: ${e.message}"
            } finally {
                _isApplying.value = false
            }
        }
    }

    fun clearApplyResult() {
        _applyResult.value = null
    }
}
