package com.wind.ggbond.classtime.ui.screen.coursecolor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.util.CourseColorPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CourseColorPickerViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    private val _courseName = MutableStateFlow("")
    val courseName: StateFlow<String> = _courseName.asStateFlow()

    private val _selectedColor = MutableStateFlow("")
    val selectedColor: StateFlow<String> = _selectedColor.asStateFlow()

    private val _originalColor = MutableStateFlow("")
    val originalColor: StateFlow<String> = _originalColor.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveResult = MutableStateFlow<String?>(null)
    val saveResult: StateFlow<String?> = _saveResult.asStateFlow()

    fun initForCourse(courseName: String) {
        _courseName.value = courseName
        viewModelScope.launch {
            try {
                val schedule = scheduleRepository.getCurrentSchedule()
                if (schedule != null) {
                    val courses = courseRepository.getAllCoursesBySchedule(schedule.id).first()
                    val course = courses.find { it.courseName == courseName }
                    if (course != null) {
                        _selectedColor.value = course.color
                        _originalColor.value = course.color
                    }
                }
            } catch (_: Exception) { }
        }
    }

    fun selectColor(color: String) {
        _selectedColor.value = color
    }

    fun saveColor() {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val schedule = scheduleRepository.getCurrentSchedule()
                if (schedule == null) {
                    _saveResult.value = "未找到当前课表"
                    return@launch
                }
                val courses = courseRepository.getAllCoursesBySchedule(schedule.id).first()
                val targetCourses = courses.filter { it.courseName == _courseName.value }
                if (targetCourses.isEmpty()) {
                    _saveResult.value = "未找到该课程"
                    return@launch
                }
                val newColor = _selectedColor.value
                targetCourses.forEach { course ->
                    if (course.color != newColor) {
                        courseRepository.updateCourse(course.copy(color = newColor))
                    }
                }
                _originalColor.value = newColor
                _saveResult.value = "颜色已保存"
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
