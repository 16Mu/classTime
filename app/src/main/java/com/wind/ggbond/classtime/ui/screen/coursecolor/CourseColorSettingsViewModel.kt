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
import com.wind.ggbond.classtime.util.AppLogger

@HiltViewModel
class CourseColorSettingsViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    private val _previewColors = MutableStateFlow<List<String>>(emptyList())
    val previewColors: StateFlow<List<String>> = _previewColors.asStateFlow()

    init {
        loadCourses()
    }

    private fun loadCourses() {
        viewModelScope.launch {
            try {
                val schedule = scheduleRepository.getCurrentSchedule()
                if (schedule != null) {
                    courseRepository.getAllCoursesBySchedule(schedule.id).collect { courseList ->
                        _courses.value = courseList
                        updatePreviewColors(courseList)
                    }
                }
            } catch (e: Exception) { AppLogger.e("Safety", "操作异常", e) }
        }
    }

    private fun updatePreviewColors(courseList: List<Course>) {
        val uniqueNames = courseList.map { it.courseName }.distinct()
        _previewColors.value = uniqueNames.mapIndexed { index, name ->
            courseList.find { it.courseName == name }?.color
                ?: CourseColorPalette.getColorByIndex(index)
        }
    }
}
