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
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random
import com.wind.ggbond.classtime.util.AppLogger

@HiltViewModel
class RandomColorSchemeViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    private val _previewColors = MutableStateFlow<List<String>>(emptyList())
    val previewColors: StateFlow<List<String>> = _previewColors.asStateFlow()

    private val _pendingColorMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val pendingColorMap: StateFlow<Map<String, String>> = _pendingColorMap.asStateFlow()

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
                        if (_pendingColorMap.value.isEmpty()) {
                            _previewColors.value = list.map { it.courseName }.distinct().mapIndexed { index, _ ->
                                list.find { it.courseName == list.map { c -> c.courseName }.distinct()[index] }?.color
                                    ?: CourseColorPalette.getColorByIndex(index)
                            }
                        }
                    }
                }
            } catch (e: Exception) { AppLogger.e("Safety", "操作异常", e) }
        }
    }

    fun generateRandomColors() {
        CourseColorPalette.clearCache()
        val uniqueNames = _courses.value.map { it.courseName }.distinct()
        val shuffledColors = CourseColorPalette.getAllColors().shuffled(Random)
        val colorMap = uniqueNames.mapIndexed { index, name ->
            name to shuffledColors[index % shuffledColors.size]
        }.toMap()
        _pendingColorMap.value = colorMap
        _previewColors.value = uniqueNames.map { colorMap[it] ?: CourseColorPalette.getColorByIndex(0) }
    }

    fun applyRandomColors() {
        viewModelScope.launch {
            _isApplying.value = true
            try {
                val schedule = scheduleRepository.getCurrentSchedule()
                if (schedule == null) {
                    _applyResult.value = "未找到当前课表"
                    return@launch
                }
                val colorMap = _pendingColorMap.value
                if (colorMap.isEmpty()) {
                    _applyResult.value = "请先随机生成配色"
                    return@launch
                }
                var count = 0
                _courses.value.forEach { course ->
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
