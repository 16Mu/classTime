package com.wind.ggbond.classtime.ui.screen.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.domain.usecase.CourseUseCase
import com.wind.ggbond.classtime.service.contract.IAlarmScheduler
import com.wind.ggbond.classtime.util.WeekParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Inject

sealed class SaveState {
    object Idle : SaveState()
    object Saving : SaveState()
    data class Success(val message: String = "保存成功") : SaveState()
    data class Error(val message: String) : SaveState()
}

@HiltViewModel
class CourseEditViewModel @Inject constructor(
    private val courseUseCase: CourseUseCase,
    private val scheduleRepository: ScheduleRepository,
    private val reminderScheduler: IAlarmScheduler
) : ViewModel() {
    
    private var currentCourseId: Long? = null
    private var existingCoursesColors: List<String> = emptyList()
    private var hasAppliedDefaults: Boolean = false
    private var isUserSelectedColor: Boolean = false
    
    private val _courseName = MutableStateFlow("")
    val courseName: StateFlow<String> = _courseName.asStateFlow()
    
    private val _teacher = MutableStateFlow("")
    val teacher: StateFlow<String> = _teacher.asStateFlow()
    
    private val _classroom = MutableStateFlow("")
    val classroom: StateFlow<String> = _classroom.asStateFlow()
    
    private val _dayOfWeek = MutableStateFlow(1)
    val dayOfWeek: StateFlow<Int> = _dayOfWeek.asStateFlow()
    
    private val _startSection = MutableStateFlow(1)
    val startSection: StateFlow<Int> = _startSection.asStateFlow()
    
    private val _sectionCount = MutableStateFlow(2)
    val sectionCount: StateFlow<Int> = _sectionCount.asStateFlow()
    
    private val _selectedWeeks = MutableStateFlow<List<Int>>(emptyList())
    val selectedWeeks: StateFlow<List<Int>> = _selectedWeeks.asStateFlow()
    
    private val _totalWeeks = MutableStateFlow(20)
    val totalWeeks: StateFlow<Int> = _totalWeeks.asStateFlow()
    
    private val _credit = MutableStateFlow(0f)
    val credit: StateFlow<Float> = _credit.asStateFlow()
    
    private val _selectedColor = MutableStateFlow("#42A5F5")
    val selectedColor: StateFlow<String> = _selectedColor.asStateFlow()
    
    private val _reminderEnabled = MutableStateFlow(true)
    val reminderEnabled: StateFlow<Boolean> = _reminderEnabled.asStateFlow()
    
    private val _reminderMinutes = MutableStateFlow(10)
    val reminderMinutes: StateFlow<Int> = _reminderMinutes.asStateFlow()
    
    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()
    
    private val _courseCode = MutableStateFlow("")
    val courseCode: StateFlow<String> = _courseCode.asStateFlow()
    
    private val _showWeekSelector = MutableStateFlow(false)
    val showWeekSelector: StateFlow<Boolean> = _showWeekSelector.asStateFlow()
    
    private val _showColorPicker = MutableStateFlow(false)
    val showColorPicker: StateFlow<Boolean> = _showColorPicker.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()
    
    init {
        viewModelScope.launch {
            try {
                val schedule = scheduleRepository.getCurrentSchedule()
                if (schedule != null) {
                    _totalWeeks.value = schedule.totalWeeks
                }
            } catch (e: Exception) {
                AppLogger.e("CourseEdit", "加载课表信息失败", e)
            }
        }
        
        viewModelScope.launch {
            try {
                val currentSchedule = scheduleRepository.getCurrentSchedule()
                if (currentSchedule != null) {
                    existingCoursesColors = courseUseCase.getExistingCoursesColors(currentSchedule.id)
                }
            } catch (e: Exception) {
            }
        }
    }
    
    fun loadCourse(courseId: Long) {
        currentCourseId = courseId
        _isLoading.value = true
        viewModelScope.launch {
            courseUseCase.getCourseById(courseId)?.let { course ->
                _courseName.value = course.courseName
                _teacher.value = course.teacher
                _classroom.value = course.classroom
                _dayOfWeek.value = course.dayOfWeek
                _startSection.value = course.startSection
                _sectionCount.value = course.sectionCount
                _selectedWeeks.value = course.weeks
                _selectedColor.value = course.color
                _reminderEnabled.value = course.reminderEnabled
                _reminderMinutes.value = course.reminderMinutes
                _note.value = course.note
                _credit.value = course.credit
                _courseCode.value = course.courseCode
            }
            _isLoading.value = false
        }
    }
    
    fun applyDefaultsIfNeeded(
        dayOfWeek: Int? = null,
        startSection: Int? = null,
        sectionCount: Int? = null,
        weekNumber: Int? = null,
        courseName: String? = null
    ) {
        if (currentCourseId != null || hasAppliedDefaults) return
        hasAppliedDefaults = true
        
        courseName?.let { name ->
            if (name.isNotBlank()) {
                _courseName.value = name
                viewModelScope.launch {
                    _selectedColor.value = courseUseCase.getColorForCourse(name, existingCoursesColors)
                }
            }
        }
        
        dayOfWeek?.let { day ->
            if (day in 1..7) {
                _dayOfWeek.value = day
            }
        }
        startSection?.let { section ->
            _startSection.value = section.coerceAtLeast(1)
        }
        sectionCount?.let { count ->
            _sectionCount.value = count.coerceAtLeast(1)
        }
        if (weekNumber != null && weekNumber > 0 && _selectedWeeks.value.isEmpty()) {
            _selectedWeeks.value = listOf(weekNumber)
        }
    }
    
    fun updateCourseName(name: String) {
        _courseName.value = name
        if (currentCourseId == null && name.isNotBlank() && !isUserSelectedColor) {
            viewModelScope.launch {
                _selectedColor.value = courseUseCase.getColorForCourse(name, existingCoursesColors)
            }
        }
    }
    
    fun updateTeacher(teacher: String) {
        _teacher.value = teacher
    }
    
    fun updateClassroom(classroom: String) {
        _classroom.value = classroom
    }
    
    fun updateDayOfWeek(day: Int) {
        _dayOfWeek.value = day
    }
    
    fun updateStartSection(section: Int) {
        _startSection.value = section.coerceAtLeast(1)
    }
    
    fun updateSectionCount(count: Int) {
        _sectionCount.value = count.coerceAtLeast(1)
    }
    
    fun updateSelectedWeeks(weeks: List<Int>) {
        _selectedWeeks.value = weeks.sorted()
    }
    
    fun updateColor(color: String) {
        _selectedColor.value = color
        isUserSelectedColor = true
    }
    
    fun updateReminderEnabled(enabled: Boolean) {
        AppLogger.d("CourseEdit", "✅ updateReminderEnabled called: $enabled")
        _reminderEnabled.value = enabled
        AppLogger.d("CourseEdit", "✅ reminderEnabled state updated to: ${_reminderEnabled.value}")
    }
    
    fun updateReminderMinutes(minutes: Int) {
        _reminderMinutes.value = minutes.coerceAtLeast(0)
    }
    
    fun updateNote(note: String) {
        _note.value = note
    }
    
    fun updateCourseCode(code: String) {
        _courseCode.value = code
    }
    
    fun updateCredit(credit: Float) {
        _credit.value = credit.coerceAtLeast(0f)
    }
    
    fun showWeekSelector() {
        _showWeekSelector.value = true
    }
    
    fun hideWeekSelector() {
        _showWeekSelector.value = false
    }
    
    fun showColorPicker() {
        _showColorPicker.value = true
    }
    
    fun hideColorPicker() {
        _showColorPicker.value = false
    }
    
    fun saveCourse() {
        if (_courseName.value.isBlank()) {
            _saveState.value = SaveState.Error("请输入课程名称")
            AppLogger.e("CourseEdit", "保存失败：课程名称为空")
            return
        }
        
        if (_selectedWeeks.value.isEmpty()) {
            _saveState.value = SaveState.Error("请选择上课周次")
            AppLogger.e("CourseEdit", "保存失败：未选择周次")
            return
        }
        
        _saveState.value = SaveState.Saving
        
        viewModelScope.launch {
            try {
                AppLogger.d("CourseEdit", "开始保存课程：${_courseName.value}")
                
                val currentSchedule = scheduleRepository.getCurrentSchedule()
                if (currentSchedule == null) {
                    _saveState.value = SaveState.Error("未找到当前课表，请先创建课表")
                    AppLogger.e("CourseEdit", "保存失败：getCurrentSchedule返回null")
                    return@launch
                }
                
                val scheduleId = currentSchedule.id
                
                val conflictResult = courseUseCase.checkCourseConflict(
                    scheduleId = scheduleId,
                    dayOfWeek = _dayOfWeek.value,
                    startSection = _startSection.value,
                    sectionCount = _sectionCount.value,
                    weeks = _selectedWeeks.value,
                    excludeCourseId = currentCourseId
                )
                
                if (conflictResult.hasConflict) {
                    _saveState.value = SaveState.Error(conflictResult.message ?: "课程时间冲突")
                    AppLogger.e("CourseEdit", "保存失败：检测到冲突")
                    return@launch
                }
                
                AppLogger.d("CourseEdit", "使用课表ID: $scheduleId")
                AppLogger.d("CourseEdit", "✅ 准备保存课程，reminderEnabled = ${_reminderEnabled.value}, reminderMinutes = ${_reminderMinutes.value}")
                
                val course = Course(
                    id = currentCourseId ?: 0,
                    courseName = _courseName.value,
                    teacher = _teacher.value,
                    classroom = _classroom.value,
                    dayOfWeek = _dayOfWeek.value,
                    startSection = _startSection.value,
                    sectionCount = _sectionCount.value,
                    weeks = _selectedWeeks.value,
                    weekExpression = WeekParser.formatWeekList(_selectedWeeks.value),
                    scheduleId = scheduleId,
                    color = _selectedColor.value,
                    reminderEnabled = _reminderEnabled.value,
                    reminderMinutes = _reminderMinutes.value,
                    note = _note.value,
                    credit = _credit.value,
                    courseCode = _courseCode.value
                )
                
                AppLogger.d("CourseEdit", "✅ Course对象已创建，reminderEnabled = ${course.reminderEnabled}")
                
                val savedCourse: Course
                val courseId = currentCourseId
                if (courseId != null) {
                    AppLogger.d("CourseEdit", "更新课程 ID: $courseId")
                    courseUseCase.updateCourse(course)
                    savedCourse = course.copy(id = courseId)
                    _saveState.value = SaveState.Success("课程更新成功")
                } else {
                    AppLogger.d("CourseEdit", "插入新课程")
                    val newId = courseUseCase.insertCourse(course)
                    AppLogger.d("CourseEdit", "课程插入成功，新ID: $newId")
                    savedCourse = course.copy(id = newId)
                    _saveState.value = SaveState.Success("课程添加成功")
                }
                
                try {
                    if (savedCourse.reminderEnabled) {
                        AppLogger.d("CourseEdit", "创建课程提醒：${savedCourse.courseName}")
                        reminderScheduler.scheduleCourseReminders(savedCourse)
                    } else {
                        AppLogger.d("CourseEdit", "取消课程提醒：${savedCourse.courseName}")
                        reminderScheduler.cancelCourseReminders(savedCourse.id)
                    }
                } catch (e: Exception) {
                    AppLogger.e("CourseEdit", "创建提醒失败：${e.message}", e)
                }
                
                AppLogger.d("CourseEdit", "课程保存成功")
            } catch (e: Exception) {
                _saveState.value = SaveState.Error("保存失败：${e.message ?: "未知错误"}")
                AppLogger.e("CourseEdit", "保存课程时出错", e)
            }
        }
    }
    
    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }
}
