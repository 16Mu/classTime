package com.wind.ggbond.classtime.ui.screen.scheduleimport

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.service.contract.IAlarmScheduler
import com.wind.ggbond.classtime.ui.components.ScheduleSelectionState
import com.wind.ggbond.classtime.ui.components.checkScheduleState
import com.wind.ggbond.classtime.util.CourseColorPalette
import com.wind.ggbond.classtime.util.CourseColorProvider
import com.wind.ggbond.classtime.util.TextCourseParser
import com.wind.ggbond.classtime.util.WeekParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * 单个时间段数据模型
 * 表示一门课程在某个星期、某个节次的上课安排
 */
data class TimeSlot(
    val id: Long = System.nanoTime(),       // 唯一标识，用于列表操作
    val dayOfWeek: Int = 1,                 // 星期几（1=周一，7=周日）
    val startSection: Int = 1,              // 起始节次
    val sectionCount: Int = 2,              // 持续节次（连续上多少节）
    val classroom: String = "",             // 教室（可单独设置）
    val customWeeks: List<Int> = emptyList() // 自定义周次列表（为空则使用课程全局周次）
)

/**
 * 单门课程数据模型
 * 包含基础信息和多个时间段
 */
data class BatchCourseItem(
    val id: Long = System.nanoTime(),       // 唯一标识
    val courseName: String = "",            // 课程名称
    val teacher: String = "",               // 教师
    val defaultClassroom: String = "",      // 默认教室
    val credit: Float = 0f,                 // 学分
    val weeks: List<Int> = emptyList(),     // 全局周次
    val timeSlots: List<TimeSlot> = listOf(TimeSlot()), // 时间段列表（至少一个）
    val color: String = "",                 // 课程颜色（空字符串表示自动分配）
    val reminderEnabled: Boolean = true,    // 是否启用提醒
    val reminderMinutes: Int = 10,          // 提前提醒分钟数
    val note: String = "",                  // 备注
    val isExpanded: Boolean = true,         // UI状态：整体是否展开
    val isBasicInfoExpanded: Boolean = true // UI状态：基础信息区域是否展开
)

/**
 * 批量创建保存状态
 */
sealed class BatchSaveState {
    /** 空闲状态 */
    object Idle : BatchSaveState()
    /** 保存中 */
    object Saving : BatchSaveState()
    /** 保存成功 */
    data class Success(val courseCount: Int, val recordCount: Int) : BatchSaveState()
    /** 保存失败 */
    data class Error(val message: String) : BatchSaveState()
}

/**
 * 冲突类型枚举
 */
enum class ConflictType {
    /** 课程内部时间段冲突 */
    INTERNAL_SLOT_CONFLICT,
    /** 批次内课程间冲突 */
    BATCH_COURSE_CONFLICT,
    /** 与课表已有课程冲突 */
    SCHEDULE_CONFLICT
}

/**
 * 冲突详情信息
 */
data class ConflictInfo(
    val conflictType: ConflictType,              // 冲突类型
    val conflictingSlot: TimeSlot? = null,        // 冲突的时间段（内部/批次冲突时）
    val conflictingCourseName: String = "",       // 冲突的课程名称
    val conflictingCourseId: Long = 0,            // 冲突的课程ID
    val conflictWeeks: List<Int> = emptyList(),   // 冲突的周次
    val conflictSections: IntRange = IntRange.EMPTY, // 冲突的节次范围
    val dayOfWeek: Int = 0,                       // 冲突发生的星期几
    val message: String = ""                      // 可读的冲突描述
)

/**
 * 批量创建课程的ViewModel
 * 支持一次创建多门课程，每门课程可设置多个时间段
 */
@HiltViewModel
class BatchCourseCreateViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val scheduleRepository: ScheduleRepository,
    private val reminderScheduler: IAlarmScheduler,
    private val textCourseParser: TextCourseParser,
    private val classTimeRepository: com.wind.ggbond.classtime.data.repository.ClassTimeRepository
) : ViewModel() {

    companion object {
        private const val TAG = "BatchCourseCreate"
    }

    // 课程列表（支持多门课程）
    private val _courseItems = MutableStateFlow(listOf(BatchCourseItem()))
    val courseItems: StateFlow<List<BatchCourseItem>> = _courseItems.asStateFlow()

    // 保存状态
    private val _saveState = MutableStateFlow<BatchSaveState>(BatchSaveState.Idle)
    val saveState: StateFlow<BatchSaveState> = _saveState.asStateFlow()

    // 周次选择器状态
    private val _showWeekSelector = MutableStateFlow<Pair<Long, Long?>?>(null) // Pair(courseId, timeSlotId?)
    val showWeekSelector: StateFlow<Pair<Long, Long?>?> = _showWeekSelector.asStateFlow()

    // 剪贴板导入对话框状态
    private val _showClipboardImport = MutableStateFlow<Long?>(null) // 正在导入的课程ID
    val showClipboardImport: StateFlow<Long?> = _showClipboardImport.asStateFlow()

    // 课表选择状态（用于检查是否需要创建课表或提示过期）
    private val _scheduleState = MutableStateFlow<ScheduleSelectionState>(ScheduleSelectionState.Loading)
    val scheduleState: StateFlow<ScheduleSelectionState> = _scheduleState.asStateFlow()

    // 当前课表中已有课程的颜色（用于智能分配）
    private var existingColors: List<String> = emptyList()
    
    // 最大节次数
    private val _maxSectionCount = MutableStateFlow(14) // 默认14节
    val maxSectionCount: StateFlow<Int> = _maxSectionCount.asStateFlow()

    // 当前冲突状态
    private val _currentConflict = MutableStateFlow<ConflictInfo?>(null)
    val currentConflict: StateFlow<ConflictInfo?> = _currentConflict.asStateFlow()

    init {
        // 初始化时检查课表状态
        checkCurrentScheduleState()
        // 初始化时获取最大节次数
        viewModelScope.launch {
            try {
                val classTimes = classTimeRepository.getClassTimesByConfigSync()
                _maxSectionCount.value = classTimes.maxOfOrNull { it.sectionNumber } ?: 14
            } catch (e: Exception) {
                Log.w(TAG, "获取最大节次数失败: ${e.message}")
            }
        }
    }

    // ===================== 课表状态检查 =====================

    /**
     * 检查当前课表状态
     * 判断是否需要创建课表或提示课表过期
     */
    private fun checkCurrentScheduleState() {
        viewModelScope.launch {
            try {
                val schedule = scheduleRepository.getCurrentSchedule()
                // 使用统一的检查方法
                _scheduleState.value = checkScheduleState(schedule)
                
                // 如果有课表，加载已有课程颜色
                if (schedule != null) {
                    existingColors = courseRepository.getAllCoursesBySchedule(schedule.id)
                        .first()
                        .map { it.color }
                }
            } catch (e: Exception) {
                Log.w(TAG, "检查课表状态失败: ${e.message}")
                _scheduleState.value = ScheduleSelectionState.NeedCreate
            }
        }
    }

    /**
     * 创建新课表
     * @param name 课表名称
     * @param startDate 开始日期
     * @param totalWeeks 总周数
     */
    fun createSchedule(name: String, startDate: LocalDate, totalWeeks: Int) {
        viewModelScope.launch {
            try {
                val endDate = startDate.plusWeeks(totalWeeks.toLong()).minusDays(1)
                val schedule = Schedule(
                    name = name,
                    startDate = startDate,
                    endDate = endDate,
                    totalWeeks = totalWeeks,
                    isCurrent = true
                )
                val scheduleId = scheduleRepository.insertSchedule(schedule)
                // 设置为当前课表
                scheduleRepository.setCurrentSchedule(scheduleId)
                Log.d(TAG, "创建课表成功: $name, ID: $scheduleId")
                // 更新状态为就绪
                _scheduleState.value = ScheduleSelectionState.Ready
            } catch (e: Exception) {
                Log.e(TAG, "创建课表失败", e)
                _saveState.value = BatchSaveState.Error("创建课表失败：${e.message}")
            }
        }
    }

    /**
     * 确认继续使用过期课表
     */
    fun confirmUseExpiredSchedule() {
        _scheduleState.value = ScheduleSelectionState.Ready
        Log.d(TAG, "用户选择继续使用过期课表")
    }

    /**
     * 用户选择创建新课表（从过期提示）
     * 将状态切换为需要创建
     */
    fun switchToCreateNewSchedule() {
        _scheduleState.value = ScheduleSelectionState.NeedCreate
        Log.d(TAG, "用户选择创建新课表")
    }

    // ===================== 课程级操作 =====================

    /**
     * 收缩所有已有课程卡片（动画序列第1步）
     */
    fun collapseAllCourses() {
        _courseItems.value = _courseItems.value.map { it.copy(isExpanded = false) }
        Log.d(TAG, "收缩所有课程卡片")
    }

    /**
     * 添加一门新课程（初始状态为折叠，动画序列第2步）
     * @return 新课程的唯一标识ID，用于后续展开
     */
    fun addCourseItemCollapsed(): Long {
        val newItem = BatchCourseItem(isExpanded = false)
        _courseItems.value = _courseItems.value + listOf(newItem)
        Log.d(TAG, "添加新课程（折叠态），当前共 ${_courseItems.value.size} 门")
        return newItem.id
    }

    /**
     * 展开指定课程卡片（动画序列第3步）
     * @param courseId 要展开的课程唯一标识
     */
    fun expandCourse(courseId: Long) {
        _courseItems.value = _courseItems.value.map {
            if (it.id == courseId) it.copy(isExpanded = true) else it
        }
        Log.d(TAG, "展开课程 $courseId")
    }

    /**
     * 删除一门课程
     * @param courseId 要删除的课程唯一标识
     */
    fun removeCourseItem(courseId: Long) {
        // 至少保留一门课程
        if (_courseItems.value.size <= 1) return
        _courseItems.value = _courseItems.value.filter { it.id != courseId }
        Log.d(TAG, "删除课程，当前共 ${_courseItems.value.size} 门")
    }

    /**
     * 切换课程的展开/折叠状态
     */
    fun toggleCourseExpanded(courseId: Long) {
        _courseItems.value = _courseItems.value.map {
            if (it.id == courseId) it.copy(isExpanded = !it.isExpanded) else it
        }
    }

    /**
     * 切换基础信息区域的展开/折叠状态
     */
    fun toggleBasicInfoExpanded(courseId: Long) {
        _courseItems.value = _courseItems.value.map {
            if (it.id == courseId) it.copy(isBasicInfoExpanded = !it.isBasicInfoExpanded) else it
        }
    }

    /**
     * 收缩基础信息区域（填写完成后自动调用）
     */
    fun collapseBasicInfo(courseId: Long) {
        _courseItems.value = _courseItems.value.map {
            if (it.id == courseId) it.copy(isBasicInfoExpanded = false) else it
        }
    }

    /**
     * 更新课程名称
     */
    fun updateCourseName(courseId: Long, name: String) {
        _courseItems.value = _courseItems.value.map {
            if (it.id == courseId) {
                it.copy(courseName = name)
            } else it
        }
        if (name.isNotBlank()) {
            viewModelScope.launch {
                val color = CourseColorProvider.getColorForCourse(name, existingColors)
                _courseItems.value = _courseItems.value.map {
                    if (it.id == courseId && it.color.isEmpty()) {
                        it.copy(color = color)
                    } else it
                }
            }
        }
    }

    /**
     * 更新教师
     */
    fun updateTeacher(courseId: Long, teacher: String) {
        _courseItems.value = _courseItems.value.map {
            if (it.id == courseId) it.copy(teacher = teacher) else it
        }
    }

    /**
     * 更新默认教室
     */
    fun updateDefaultClassroom(courseId: Long, classroom: String) {
        _courseItems.value = _courseItems.value.map {
            if (it.id == courseId) it.copy(defaultClassroom = classroom) else it
        }
    }

    /**
     * 更新课程颜色
     */
    fun updateCourseColor(courseId: Long, color: String) {
        _courseItems.value = _courseItems.value.map {
            if (it.id == courseId) it.copy(color = color) else it
        }
    }

    fun autoAssignAllColors() {
        viewModelScope.launch {
            val usedColors = mutableSetOf<String>()
            _courseItems.value = _courseItems.value.mapIndexed { index, course ->
                val assignedColor = CourseColorProvider.getColorForCourse(
                    course.courseName,
                    existingColors + usedColors.toList()
                ).also { usedColors.add(it) }
                course.copy(color = assignedColor)
            }
            Log.d(TAG, "一键分配颜色完成，共 ${_courseItems.value.size} 门课程")
        }
    }

    // ===================== 剪贴板导入 =====================

    /**
     * 显示剪贴板导入对话框
     */
    fun showClipboardImport(courseId: Long) {
        _showClipboardImport.value = courseId
    }

    /**
     * 隐藏剪贴板导入对话框
     */
    fun hideClipboardImport() {
        _showClipboardImport.value = null
    }

    /**
     * 从剪贴板文本解析并应用课程信息
     * @param courseId 目标课程ID
     * @param clipboardText 剪贴板文本
     * @return 解析结果描述，用于UI显示
     */
    fun applyFromClipboard(courseId: Long, clipboardText: String): String {
        if (clipboardText.isBlank()) {
            return "剪贴板内容为空"
        }

        val parsedCourses = textCourseParser.parse(clipboardText)
        if (parsedCourses.isEmpty()) {
            return "未能识别课程信息"
        }

        // 取第一个解析结果（单课程导入）
        val parsed = parsedCourses.first()

        // 更新课程信息
        _courseItems.value = _courseItems.value.map { course ->
            if (course.id == courseId) {
                course.copy(
                    courseName = parsed.courseName.ifBlank { course.courseName },
                    teacher = parsed.teacher.ifBlank { course.teacher },
                    defaultClassroom = parsed.classroom.ifBlank { course.defaultClassroom },
                    weeks = parsed.weeks.ifEmpty { course.weeks },
                    credit = parsed.credit.takeIf { it > 0 } ?: course.credit
                )
            } else course
        }
        if (parsed.courseName.isNotBlank()) {
            viewModelScope.launch {
                val color = CourseColorProvider.getColorForCourse(parsed.courseName, existingColors)
                _courseItems.value = _courseItems.value.map {
                    if (it.id == courseId && it.color.isEmpty()) {
                        it.copy(color = color)
                    } else it
                }
            }
        }

        // 构建结果描述
        val result = StringBuilder()
        result.append("课程: ${parsed.courseName}\n")
        if (parsed.teacher.isNotBlank()) result.append("教师: ${parsed.teacher}\n")
        if (parsed.classroom.isNotBlank()) result.append("教室: ${parsed.classroom}\n")
        if (parsed.weeks.isNotEmpty()) result.append("周次: ${WeekParser.formatWeekList(parsed.weeks)}\n")
        if (parsed.credit > 0) result.append("学分: ${parsed.credit}")
        if (parsed.startSection > 0) result.append("\n节次: 第${parsed.startSection}节开始，持续${parsed.endSection - parsed.startSection + 1}节")

        return result.toString().trimEnd('\n')
    }

    /**
     * 从剪贴板批量导入多门课程
     * @param clipboardText 剪贴板文本
     * @return 导入的课程数量
     */
    fun batchImportFromClipboard(clipboardText: String): Int {
        if (clipboardText.isBlank()) return 0

        val parsedCourses = textCourseParser.parse(clipboardText)
        if (parsedCourses.isEmpty()) return 0

        // 为每门解析出的课程创建新的课程项
        val newCourses = parsedCourses.map { parsed ->
            BatchCourseItem(
                courseName = parsed.courseName,
                teacher = parsed.teacher,
                defaultClassroom = parsed.classroom,
                weeks = parsed.weeks,
                credit = parsed.credit,
                isExpanded = true
            )
        }
        _courseItems.value = newCourses + _courseItems.value
        viewModelScope.launch {
            val colorMapping = mutableMapOf<String, String>()
            for (parsed in parsedCourses) {
                colorMapping[parsed.courseName] = CourseColorProvider.getColorForCourse(
                    parsed.courseName,
                    existingColors + _courseItems.value.map { it.color }
                )
            }
            _courseItems.value = _courseItems.value.map { course ->
                if (course.color.isEmpty() && colorMapping.containsKey(course.courseName)) {
                    course.copy(color = colorMapping[course.courseName]!!)
                } else course
            }
        }

        return newCourses.size
    }

    /**
     * 更新课程学分
     */
    fun updateCredit(courseId: Long, credit: Float) {
        _courseItems.value = _courseItems.value.map {
            if (it.id == courseId) it.copy(credit = credit) else it
        }
    }

    /**
     * 更新课程提醒开关
     */
    fun updateReminderEnabled(courseId: Long, enabled: Boolean) {
        _courseItems.value = _courseItems.value.map {
            if (it.id == courseId) it.copy(reminderEnabled = enabled) else it
        }
    }

    /**
     * 更新课程提醒提前分钟数
     */
    fun updateReminderMinutes(courseId: Long, minutes: Int) {
        _courseItems.value = _courseItems.value.map {
            if (it.id == courseId) it.copy(reminderMinutes = minutes.coerceAtLeast(0)) else it
        }
    }

    /**
     * 更新课程备注
     */
    fun updateNote(courseId: Long, note: String) {
        _courseItems.value = _courseItems.value.map {
            if (it.id == courseId) it.copy(note = note) else it
        }
    }
    
    /**
     * 更新整个课程项（用于快捷编辑）
     */
    fun updateCourseItem(updatedCourse: BatchCourseItem) {
        _courseItems.value = _courseItems.value.map {
            if (it.id == updatedCourse.id) updatedCourse else it
        }
    }

    // ===================== 时间段操作 =====================

    /**
     * 为指定课程添加一个时间段
     * ✅ 新时间段插入到列表头部，最新的时间段显示在最上面
     */
    fun addTimeSlot(courseId: Long) {
        _courseItems.value = _courseItems.value.map { course ->
            if (course.id == courseId) {
                course.copy(timeSlots = listOf(TimeSlot()) + course.timeSlots)
            } else course
        }
    }

    /**
     * 删除指定课程的一个时间段
     */
    fun removeTimeSlot(courseId: Long, slotId: Long) {
        _courseItems.value = _courseItems.value.map { course ->
            if (course.id == courseId) {
                // 至少保留一个时间段
                if (course.timeSlots.size <= 1) return@map course
                course.copy(timeSlots = course.timeSlots.filter { it.id != slotId })
            } else course
        }
    }

    /**
     * 更新时间段的星期（带冲突检测）
     */
    fun updateSlotDayOfWeek(courseId: Long, slotId: Long, dayOfWeek: Int) {
        updateTimeSlot(courseId, slotId) { it.copy(dayOfWeek = dayOfWeek) }
        detectAndSetConflict(courseId, slotId)
    }

    /**
     * 更新时间段的起始节次（带冲突检测）
     */
    fun updateSlotStartSection(courseId: Long, slotId: Long, startSection: Int) {
        updateTimeSlot(courseId, slotId) {
            it.copy(startSection = startSection.coerceAtLeast(1))
        }
        detectAndSetConflict(courseId, slotId)
    }

    /**
     * 更新时间段的持续节次（带冲突检测）
     */
    fun updateSlotEndSection(courseId: Long, slotId: Long, sectionCount: Int) {
        updateTimeSlot(courseId, slotId) {
            it.copy(sectionCount = sectionCount.coerceAtLeast(1))
        }
        detectAndSetConflict(courseId, slotId)
    }

    /**
     * 更新时间段的教室
     */
    fun updateSlotClassroom(courseId: Long, slotId: Long, classroom: String) {
        updateTimeSlot(courseId, slotId) { it.copy(classroom = classroom) }
    }

    /**
     * 更新时间段的自定义周次
     */
    fun updateSlotCustomWeeks(courseId: Long, slotId: Long, weeks: List<Int>) {
        updateTimeSlot(courseId, slotId) { it.copy(customWeeks = weeks.sorted()) }
    }

    /**
     * 通用时间段更新方法
     */
    private fun updateTimeSlot(courseId: Long, slotId: Long, transform: (TimeSlot) -> TimeSlot) {
        _courseItems.value = _courseItems.value.map { course ->
            if (course.id == courseId) {
                course.copy(timeSlots = course.timeSlots.map { slot ->
                    if (slot.id == slotId) transform(slot) else slot
                })
            } else course
        }
    }

    /**
     * 检测冲突并更新冲突状态（异步）
     */
    private fun detectAndSetConflict(courseId: Long, slotId: Long) {
        viewModelScope.launch {
            val course = _courseItems.value.find { it.id == courseId } ?: return@launch
            val slot = course.timeSlots.find { it.id == slotId } ?: return@launch

            val conflict = checkTimeSlotConflict(courseId, slotId, slot)
            _currentConflict.value = conflict
        }
    }

    // ===================== 周次选择器 =====================

    /**
     * 显示周次选择器
     * @param courseId 课程ID
     * @param slotId 时间段ID（null表示编辑课程全局周次）
     */
    fun showWeekSelector(courseId: Long, slotId: Long? = null) {
        _showWeekSelector.value = Pair(courseId, slotId)
    }

    /**
     * 隐藏周次选择器
     */
    fun hideWeekSelector() {
        _showWeekSelector.value = null
    }

    /**
     * 确认周次选择（带冲突检测）
     */
    fun confirmWeekSelection(weeks: List<Int>) {
        val state = _showWeekSelector.value ?: return
        val (courseId, slotId) = state
        if (slotId != null) {
            // 更新时间段自定义周次
            updateSlotCustomWeeks(courseId, slotId, weeks)
            // 检测冲突（周次变更后需要重新检测）
            detectAndSetConflict(courseId, slotId)
        } else {
            // 时间段没有周次选择功能，不处理
        }
        hideWeekSelector()
    }

    /**
     * 获取当前编辑中的周次列表（用于周次选择器的初始值）
     */
    fun getCurrentEditingWeeks(): List<Int> {
        val state = _showWeekSelector.value ?: return emptyList()
        val (courseId, slotId) = state
        val course = _courseItems.value.find { it.id == courseId } ?: return emptyList()
        return if (slotId != null) {
            // 返回时间段自定义周次
            course.timeSlots.find { it.id == slotId }?.customWeeks ?: emptyList()
        } else {
            // 没有全局周次概念，返回空列表
            emptyList()
        }
    }

    // ===================== 冲突检测 =====================

    /**
     * 实时检测时间段冲突
     * 检测顺序：内部冲突 -> 批次冲突 -> 课表冲突
     *
     * @param courseId 课程ID
     * @param slotId 时间段ID（新增时传-1）
     * @param slot 要检测的时间段
     * @return 冲突信息，无冲突返回null
     */
    suspend fun checkTimeSlotConflict(courseId: Long, slotId: Long, slot: TimeSlot): ConflictInfo? {
        val course = _courseItems.value.find { it.id == courseId } ?: return null

        // 如果周次为空，不检测冲突
        if (slot.customWeeks.isEmpty()) return null

        val newSections = slot.startSection..(slot.startSection + slot.sectionCount - 1)

        // 1. 检测课程内部时间段冲突
        for (existingSlot in course.timeSlots) {
            if (existingSlot.id == slotId) continue // 跳过自身

            val internalConflict = checkSlotConflict(slot, existingSlot, course.weeks, course.weeks)
            if (internalConflict != null) {
                return internalConflict.copy(
                    conflictType = ConflictType.INTERNAL_SLOT_CONFLICT,
                    conflictingCourseName = course.courseName,
                    conflictingCourseId = course.id,
                    message = "「${course.courseName}」内部时间段冲突：${internalConflict.message}"
                )
            }
        }

        // 2. 检测批次内其他课程冲突
        for (otherCourse in _courseItems.value) {
            if (otherCourse.id == courseId) continue

            for (otherSlot in otherCourse.timeSlots) {
                val batchConflict = checkSlotConflict(slot, otherSlot, course.weeks, otherCourse.weeks)
                if (batchConflict != null) {
                    return batchConflict.copy(
                        conflictType = ConflictType.BATCH_COURSE_CONFLICT,
                        conflictingSlot = otherSlot,
                        conflictingCourseName = otherCourse.courseName,
                        conflictingCourseId = otherCourse.id,
                        message = "「${course.courseName}」与「${otherCourse.courseName}」时间冲突：${batchConflict.message}"
                    )
                }
            }
        }

        // 3. 检测与课表已有课程冲突
        val schedule = scheduleRepository.getCurrentSchedule() ?: return null
        val conflicts = courseRepository.detectConflictWithWeeks(
            scheduleId = schedule.id,
            dayOfWeek = slot.dayOfWeek,
            startSection = slot.startSection,
            sectionCount = slot.sectionCount,
            weeks = slot.customWeeks
        )

        if (conflicts.isNotEmpty()) {
            val conflict = conflicts.first()
            val conflictWeeks = conflict.weeks.filter { it in slot.customWeeks }
            val conflictSections = maxOf(slot.startSection, conflict.startSection)..
                    minOf(slot.startSection + slot.sectionCount - 1, conflict.startSection + conflict.sectionCount - 1)

            return ConflictInfo(
                conflictType = ConflictType.SCHEDULE_CONFLICT,
                conflictingCourseName = conflict.courseName,
                conflictingCourseId = conflict.id,
                conflictWeeks = conflictWeeks,
                conflictSections = conflictSections,
                dayOfWeek = slot.dayOfWeek,
                message = "「${course.courseName}」与已有课程「${conflict.courseName}」冲突：" +
                        "星期${getDayOfWeekName(slot.dayOfWeek)} 第${conflictSections.first}-${conflictSections.last}节 " +
                        "周次${WeekParser.formatWeekList(conflictWeeks)}"
            )
        }

        return null
    }

    /**
     * 检测两个时间段是否冲突
     */
    private fun checkSlotConflict(
        newSlot: TimeSlot,
        existingSlot: TimeSlot,
        newGlobalWeeks: List<Int>,
        existingGlobalWeeks: List<Int>
    ): ConflictInfo? {
        // 星期不同，不冲突
        if (newSlot.dayOfWeek != existingSlot.dayOfWeek) return null

        // 获取实际使用的周次
        val newWeeks = newSlot.customWeeks.ifEmpty { newGlobalWeeks }
        val existingWeeks = existingSlot.customWeeks.ifEmpty { existingGlobalWeeks }

        // 任一周次为空，不冲突
        if (newWeeks.isEmpty() || existingWeeks.isEmpty()) return null

        // 计算节次范围
        val newSections = newSlot.startSection..(newSlot.startSection + newSlot.sectionCount - 1)
        val existingSections = existingSlot.startSection..(existingSlot.startSection + existingSlot.sectionCount - 1)

        // 节次无交集，不冲突
        val sectionsOverlap = newSections.first <= existingSections.last && existingSections.first <= newSections.last
        if (!sectionsOverlap) return null

        // 周次无交集，不冲突
        val overlapWeeks = newWeeks.intersect(existingWeeks.toSet()).toList()
        if (overlapWeeks.isEmpty()) return null

        // 计算冲突的节次范围
        val conflictSections = maxOf(newSections.first, existingSections.first)..
                minOf(newSections.last, existingSections.last)

        return ConflictInfo(
            conflictType = ConflictType.INTERNAL_SLOT_CONFLICT,
            conflictingSlot = existingSlot,
            conflictWeeks = overlapWeeks,
            conflictSections = conflictSections,
            dayOfWeek = newSlot.dayOfWeek,
            message = "星期${getDayOfWeekName(newSlot.dayOfWeek)} 第${conflictSections.first}-${conflictSections.last}节 " +
                    "周次${WeekParser.formatWeekList(overlapWeeks)}"
        )
    }

    /**
     * 全面验证并检测冲突（保存前调用）
     * @return 错误信息，null表示验证通过
     */
    suspend fun validateAllWithConflictCheck(): String? {
        // 先进行基础验证
        val basicError = validateAll()
        if (basicError != null) return basicError

        val courses = _courseItems.value

        // 检测所有课程的冲突
        for (course in courses) {
            for (slot in course.timeSlots) {
                val conflict = checkTimeSlotConflict(course.id, slot.id, slot)
                if (conflict != null) {
                    _currentConflict.value = conflict
                    return conflict.message
                }
            }
        }

        // 清除冲突状态
        _currentConflict.value = null
        return null
    }

    /**
     * 清除当前冲突状态
     */
    fun clearConflict() {
        _currentConflict.value = null
    }

    /**
     * 获取星期几的中文名称
     */
    private fun getDayOfWeekName(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            1 -> "一"
            2 -> "二"
            3 -> "三"
            4 -> "四"
            5 -> "五"
            6 -> "六"
            7 -> "日"
            else -> dayOfWeek.toString()
        }
    }

    // ===================== 验证与保存 =====================

    /**
     * 验证所有课程数据
     * @return 错误信息，null表示验证通过
     */
    private fun validateAll(): String? {
        val courses = _courseItems.value
        for ((index, course) in courses.withIndex()) {
            val num = index + 1
            // 验证课程名称
            if (course.courseName.isBlank()) {
                return "第${num}门课程名称不能为空"
            }
            // 验证时间段
            if (course.timeSlots.isEmpty()) {
                return "「${course.courseName}」至少需要一个时间安排"
            }
            for ((slotIndex, slot) in course.timeSlots.withIndex()) {
                val slotNum = slotIndex + 1
                if (slot.startSection < 1) {
                    return "「${course.courseName}」第${slotNum}个时间段的起始节次无效"
                }
                if (slot.sectionCount < 1) {
                    return "「${course.courseName}」第${slotNum}个时间段的持续节次无效"
                }
                if (slot.dayOfWeek !in 1..7) {
                    return "「${course.courseName}」第${slotNum}个时间段的星期设置无效"
                }
                // ✅ 修复：周次是必选项，不能为空
                if (slot.customWeeks.isEmpty()) {
                    return "「${course.courseName}」第${slotNum}个时间段请选择周次"
                }
            }
        }
        return null
    }

    /**
     * 保存所有课程到数据库
     */
    fun saveAll() {
        _saveState.value = BatchSaveState.Saving

        viewModelScope.launch {
            try {
                // 使用全面验证（包含冲突检测）
                val error = validateAllWithConflictCheck()
                if (error != null) {
                    _saveState.value = BatchSaveState.Error(error)
                    return@launch
                }
                // 获取当前课表
                val currentSchedule = scheduleRepository.getCurrentSchedule()
                if (currentSchedule == null) {
                    _saveState.value = BatchSaveState.Error("未找到当前课表，请先创建课表")
                    return@launch
                }

                val scheduleId = currentSchedule.id

                val coursesToInsert = mutableListOf<Course>()
                var courseCount = 0 // 统计课程门数
                
                //  修复：用于检测同一批次内的时间段冲突
                // Key: "dayOfWeek_startSection_endSection_week" -> courseName
                val batchTimeSlots = mutableMapOf<String, String>()

                for (courseItem in _courseItems.value) {
                    courseCount++
                    // 确定颜色：用户设置 > 自动分配
                    val color = courseItem.color.ifEmpty {
                        CourseColorProvider.getColorForCourse(courseItem.courseName, existingColors)
                    }

                    // 为每个时间段创建一条Course记录
                    for (slot in courseItem.timeSlots) {
                        // 确定该时间段使用的周次（自定义周次优先，否则使用全局周次）
                        val weeks = if (slot.customWeeks.isNotEmpty()) {
                            slot.customWeeks
                        } else {
                            courseItem.weeks
                        }
                        // 确定教室：时间段教室 > 默认教室
                        val classroom = slot.classroom.ifEmpty { courseItem.defaultClassroom }
                        // 计算结束节次
                        val endSection = slot.startSection + slot.sectionCount - 1
                        
                        //  修复：如果周次为空，跳过冲突检测（因为没有实际的上课时间）
                        if (weeks.isEmpty()) {
                            Log.w(TAG, "课程「${courseItem.courseName}」的时间段周次为空，跳过冲突检测")
                        } else {
                            //  修复：先检测同一批次内的时间段冲突
                            for (week in weeks) {
                                for (section in slot.startSection..endSection) {
                                    val key = "${slot.dayOfWeek}_${section}_$week"
                                    val existingCourse = batchTimeSlots[key]
                                    if (existingCourse != null && existingCourse != courseItem.courseName) {
                                        // 同一批次内不同课程的时间段冲突
                                        _saveState.value = BatchSaveState.Error(
                                            "「${courseItem.courseName}」与「${existingCourse}」在同一时间段冲突（周$week 星期${slot.dayOfWeek} 第${section}节）"
                                        )
                                        return@launch
                                    }
                                    // 记录当前时间段（同一门课程的不同时间段不算冲突）
                                    batchTimeSlots[key] = courseItem.courseName
                                }
                            }

                            // 检测与数据库中已有课程的冲突
                            val conflicts = courseRepository.detectConflictWithWeeks(
                                scheduleId = scheduleId,
                                dayOfWeek = slot.dayOfWeek,
                                startSection = slot.startSection,
                                sectionCount = slot.sectionCount,
                                weeks = weeks
                            )
                            if (conflicts.isNotEmpty()) {
                                //  修复：过滤掉同名课程（同一门课程的不同时间段不算冲突）
                                val realConflicts = conflicts.filter { it.courseName != courseItem.courseName }
                                if (realConflicts.isNotEmpty()) {
                                    val conflictNames = realConflicts.joinToString(", ") { it.courseName }
                                    _saveState.value = BatchSaveState.Error(
                                        "「${courseItem.courseName}」与「${conflictNames}」存在时间冲突"
                                    )
                                    return@launch
                                }
                            }
                        }

                        coursesToInsert.add(
                            Course(
                                courseName = courseItem.courseName,
                                teacher = courseItem.teacher,
                                classroom = classroom,
                                dayOfWeek = slot.dayOfWeek,
                                startSection = slot.startSection,
                                sectionCount = slot.sectionCount,
                                weeks = weeks,
                                weekExpression = WeekParser.formatWeekList(weeks),
                                scheduleId = scheduleId,
                                credit = courseItem.credit,
                                color = color,
                                note = courseItem.note,
                                reminderEnabled = courseItem.reminderEnabled,
                                reminderMinutes = courseItem.reminderMinutes
                            )
                        )
                    }
                }

                // 批量插入
                val ids = courseRepository.insertCourses(coursesToInsert)
                Log.d(TAG, "批量插入成功，共 ${ids.size} 条记录")

                // 为每条记录创建提醒
                for ((index, id) in ids.withIndex()) {
                    try {
                        val savedCourse = coursesToInsert[index].copy(id = id)
                        if (savedCourse.reminderEnabled) {
                            reminderScheduler.scheduleCourseReminders(savedCourse)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "创建提醒失败: ${e.message}")
                    }
                }

                _saveState.value = BatchSaveState.Success(
                    courseCount = courseCount,
                    recordCount = ids.size
                )
            } catch (e: Exception) {
                Log.e(TAG, "批量保存失败", e)
                _saveState.value = BatchSaveState.Error("保存失败：${e.message ?: "未知错误"}")
            }
        }
    }

    /**
     * 重置保存状态
     */
    fun resetSaveState() {
        _saveState.value = BatchSaveState.Idle
    }

    /**
     * 获取预计创建的记录数（用于UI预览）
     */
    fun getEstimatedRecordCount(): Int {
        return _courseItems.value.sumOf { it.timeSlots.size }
    }
}
