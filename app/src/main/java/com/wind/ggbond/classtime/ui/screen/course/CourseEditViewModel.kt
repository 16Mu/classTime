package com.wind.ggbond.classtime.ui.screen.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.service.contract.IAlarmScheduler
import com.wind.ggbond.classtime.util.CourseColorPalette
import com.wind.ggbond.classtime.util.CourseColorProvider
import com.wind.ggbond.classtime.util.WeekParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Inject

/**
 * 保存状态
 */
sealed class SaveState {
    object Idle : SaveState()
    object Saving : SaveState()
    data class Success(val message: String = "保存成功") : SaveState()
    data class Error(val message: String) : SaveState()
}

/**
 * 课程编辑 ViewModel
 */
@HiltViewModel
class CourseEditViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val scheduleRepository: ScheduleRepository,
    private val reminderScheduler: IAlarmScheduler
) : ViewModel() {
    
    private var currentCourseId: Long? = null
    private var existingCoursesColors: List<String> = emptyList()
    private var hasAppliedDefaults: Boolean = false
    private var isUserSelectedColor: Boolean = false
    
    // 课程基本信息
    private val _courseName = MutableStateFlow("")
    val courseName: StateFlow<String> = _courseName.asStateFlow()
    
    private val _teacher = MutableStateFlow("")
    val teacher: StateFlow<String> = _teacher.asStateFlow()
    
    private val _classroom = MutableStateFlow("")
    val classroom: StateFlow<String> = _classroom.asStateFlow()
    
    // 时间信息
    private val _dayOfWeek = MutableStateFlow(1)
    val dayOfWeek: StateFlow<Int> = _dayOfWeek.asStateFlow()
    
    private val _startSection = MutableStateFlow(1)
    val startSection: StateFlow<Int> = _startSection.asStateFlow()
    
    private val _sectionCount = MutableStateFlow(2)
    val sectionCount: StateFlow<Int> = _sectionCount.asStateFlow()
    
    private val _selectedWeeks = MutableStateFlow<List<Int>>(emptyList())
    val selectedWeeks: StateFlow<List<Int>> = _selectedWeeks.asStateFlow()
    
    // 学期总周数（动态从当前学期读取，用于周次选择器范围限制）
    private val _totalWeeks = MutableStateFlow(20)
    val totalWeeks: StateFlow<Int> = _totalWeeks.asStateFlow()
    
    // 学分
    private val _credit = MutableStateFlow(0f)
    val credit: StateFlow<Float> = _credit.asStateFlow()
    
    // 样式和提醒
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
    
    // UI 状态
    private val _showWeekSelector = MutableStateFlow(false)
    val showWeekSelector: StateFlow<Boolean> = _showWeekSelector.asStateFlow()
    
    private val _showColorPicker = MutableStateFlow(false)
    val showColorPicker: StateFlow<Boolean> = _showColorPicker.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // ✅ 保存状态 - 使用密封类管理状态
    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()
    
    init {
        // 从当前课表加载总周数
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
        
        // 加载当前课表中已有课程的颜色，用于智能分配
        viewModelScope.launch {
            try {
                val currentSchedule = scheduleRepository.getCurrentSchedule()
                if (currentSchedule != null) {
                    courseRepository.getAllCoursesBySchedule(currentSchedule.id)
                        .collect { courses ->
                            existingCoursesColors = courses.map { it.color }
                        }
                }
            } catch (e: Exception) {
                // 加载失败不影响使用
            }
        }
    }
    
    fun loadCourse(courseId: Long) {
        currentCourseId = courseId
        _isLoading.value = true
        viewModelScope.launch {
            courseRepository.getCourseById(courseId)?.let { course ->
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
    
    /**
     * 应用从课表格子传入的默认时间信息（仅用于新建课程，且只应用一次）
     * @param courseName 预填充的课程名称，用于添加新时间段场景
     */
    fun applyDefaultsIfNeeded(
        dayOfWeek: Int? = null,
        startSection: Int? = null,
        sectionCount: Int? = null,
        weekNumber: Int? = null,
        courseName: String? = null
    ) {
        if (currentCourseId != null || hasAppliedDefaults) return
        hasAppliedDefaults = true
        
        // 预填充课程名称（用于添加新时间段场景）
        courseName?.let { name ->
            if (name.isNotBlank()) {
                _courseName.value = name
                viewModelScope.launch {
                    _selectedColor.value = CourseColorProvider.getColorForCourse(name, existingCoursesColors)
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
        // 仅当尚未选择任何周次时，才使用默认周次
        if (weekNumber != null && weekNumber > 0 && _selectedWeeks.value.isEmpty()) {
            _selectedWeeks.value = listOf(weekNumber)
        }
    }
    
    fun updateCourseName(name: String) {
        _courseName.value = name
        if (currentCourseId == null && name.isNotBlank() && !isUserSelectedColor) {
            viewModelScope.launch {
                _selectedColor.value = CourseColorProvider.getColorForCourse(name, existingCoursesColors)
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
    
    /**
     * 保存课程
     * ✅ 返回结果通过 saveState Flow
     * ✅ 添加冲突检测
     */
    fun saveCourse() {
        // 验证必填字段
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
        
        // ✅ 设置保存中状态
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
                
                // ✅ 检测冲突
                val conflicts = courseRepository.detectConflictWithWeeks(
                    scheduleId = scheduleId,
                    dayOfWeek = _dayOfWeek.value,
                    startSection = _startSection.value,
                    sectionCount = _sectionCount.value,
                    weeks = _selectedWeeks.value,
                    excludeCourseId = currentCourseId
                )
                
                if (conflicts.isNotEmpty()) {
                    val conflictNames = conflicts.joinToString(", ") { it.courseName }
                    _saveState.value = SaveState.Error("课程时间冲突：与「$conflictNames」冲突")
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
                    courseRepository.updateCourse(course)
                    savedCourse = course.copy(id = courseId)
                    // ✅ 设置成功状态
                    _saveState.value = SaveState.Success("课程更新成功")
                } else {
                    AppLogger.d("CourseEdit", "插入新课程")
                    val newId = courseRepository.insertCourse(course)
                    AppLogger.d("CourseEdit", "课程插入成功，新ID: $newId")
                    savedCourse = course.copy(id = newId)
                    // ✅ 设置成功状态
                    _saveState.value = SaveState.Success("课程添加成功")
                }
                
                // ✅ 添加：保存成功后创建/更新提醒
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
                    // 提醒创建失败不影响课程保存成功
                }
                
                AppLogger.d("CourseEdit", "课程保存成功")
            } catch (e: Exception) {
                // ✅ 设置错误状态
                _saveState.value = SaveState.Error("保存失败：${e.message ?: "未知错误"}")
                AppLogger.e("CourseEdit", "保存课程时出错", e)
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
