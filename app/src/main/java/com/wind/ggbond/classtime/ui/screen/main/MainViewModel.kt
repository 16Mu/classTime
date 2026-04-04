package com.wind.ggbond.classtime.ui.screen.main

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.CourseAdjustment
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.data.repository.ClassTimeRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.domain.usecase.AdjustmentUseCase
import com.wind.ggbond.classtime.domain.usecase.AutoUpdateUseCase
import com.wind.ggbond.classtime.domain.usecase.ClipboardUseCase
import com.wind.ggbond.classtime.domain.usecase.CourseUseCase
import com.wind.ggbond.classtime.domain.usecase.ExamUseCase
import com.wind.ggbond.classtime.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@Immutable
data class WeekCoursesData(
    val weekNumber: Int,
    val coursesMap: Map<Int, List<Course>>
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel @Inject constructor(
    private val courseUseCase: CourseUseCase,
    private val adjustmentUseCase: AdjustmentUseCase,
    private val examUseCase: ExamUseCase,
    private val clipboardUseCase: ClipboardUseCase,
    private val autoUpdateUseCase: AutoUpdateUseCase,
    private val scheduleRepository: ScheduleRepository,
    private val classTimeRepository: ClassTimeRepository
) : ViewModel() {
    
    companion object {
        private const val TAG = "MainViewModel"
    }
    
    private val _currentSchedule = MutableStateFlow<Schedule?>(null)
    val currentSchedule: StateFlow<Schedule?> = _currentSchedule.asStateFlow()
    
    private val _currentWeekNumber = MutableStateFlow(1)
    val currentWeekNumber: StateFlow<Int> = _currentWeekNumber.asStateFlow()
    
    private val _actualWeekNumber = MutableStateFlow(1)
    val actualWeekNumber: StateFlow<Int> = _actualWeekNumber.asStateFlow()
    
    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()
    
    private val _adjustments = MutableStateFlow<List<CourseAdjustment>>(emptyList())
    val adjustments: StateFlow<List<CourseAdjustment>> = _adjustments.asStateFlow()
    
    private val _adjustmentMap = MutableStateFlow<Map<String, CourseAdjustment>>(emptyMap())
    
    val upcomingExams: StateFlow<List<com.wind.ggbond.classtime.data.local.entity.Exam>> = _currentWeekNumber
        .flatMapLatest { week ->
            examUseCase.getExamsByWeekRangeFlow(week, week + 1)
        }
        .catch { e ->
            android.util.Log.e(TAG, "Error in upcomingExams flow", e)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    val examsForCurrentWeek: StateFlow<List<com.wind.ggbond.classtime.data.local.entity.Exam>> = _currentWeekNumber
        .flatMapLatest { week ->
            flow {
                try {
                    emit(examUseCase.getExamsWithSectionByWeek(week))
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error loading exams for week $week", e)
                    emit(emptyList())
                }
            }
        }
        .catch { e ->
            android.util.Log.e(TAG, "Error in examsForCurrentWeek flow", e)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    fun isAdjustedCourse(courseId: Long, weekNumber: Int, dayOfWeek: Int, startSection: Int): CourseAdjustment? {
        val key = "${courseId}_${weekNumber}_${dayOfWeek}_${startSection}"
        return _adjustmentMap.value[key]
    }
    
    val classTimes = classTimeRepository.getClassTimesByConfig("default")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    private val _viewMode = MutableStateFlow(0)
    val viewMode: StateFlow<Int> = _viewMode.asStateFlow()
    
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()
    
    val clipboard: StateFlow<Pair<Course, Int>?> = clipboardUseCase.clipboard
    val showClipboardImport: StateFlow<Boolean> = clipboardUseCase.showClipboardImport
    val clipboardImportResult: StateFlow<String?> = clipboardUseCase.clipboardImportResult
    
    val coursesForCurrentWeek: StateFlow<Map<Int, List<Course>>> = combine(
        _courses,
        _adjustments,
        _currentWeekNumber
    ) { coursesList, adjustmentsList, weekNumber ->
        if (adjustmentsList.isNotEmpty()) {
            adjustmentUseCase.mergeCoursesWithAdjustments(coursesList, adjustmentsList, weekNumber)
        } else {
            courseUseCase.getCoursesForWeek(coursesList, weekNumber)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )
    
    private var courseLoadJob: kotlinx.coroutines.Job? = null
    private var adjustmentLoadJob: kotlinx.coroutines.Job? = null
    
    private var _lastDeletedSnapshot: Triple<Course, Int, Boolean>? = null
    
    init {
        loadCurrentSchedule()
        
        viewModelScope.launch {
            _currentSchedule.first { it != null }
            _courses.first { it.isNotEmpty() }
            
            val currentWeek = _currentWeekNumber.value
            android.util.Log.d(TAG, "ViewModel初始化：开始预加载周次 $currentWeek 及相邻周")
            
            courseUseCase.preloadAdjacentWeeks(_courses.value, currentWeek, _currentSchedule.value?.totalWeeks ?: 20)
            
            android.util.Log.d(TAG, "ViewModel初始化：预加载完成")
        }
    }
    
    private fun loadCurrentSchedule() {
        viewModelScope.launch {
            scheduleRepository.getCurrentScheduleFlow().collect { schedule ->
                android.util.Log.d(TAG, "当前课表变化: ${schedule?.name} (ID=${schedule?.id})")
                _currentSchedule.value = schedule
                schedule?.let {
                    android.util.Log.d(TAG, "=== 课表信息加载 ===")
                    android.util.Log.d(TAG, "课表名称: ${it.name}")
                    android.util.Log.d(TAG, "开始日期: ${it.startDate}")
                    android.util.Log.d(TAG, "结束日期: ${it.endDate}")
                    android.util.Log.d(TAG, "总周数: ${it.totalWeeks}")
                    
                    val calculatedWeek = DateUtils.calculateWeekNumber(it.startDate)
                    android.util.Log.d(TAG, "计算的周次: $calculatedWeek")
                    
                    val safeWeekNumber = if (calculatedWeek < 1 || calculatedWeek > 30) {
                        android.util.Log.w(TAG, "计算的周次异常: $calculatedWeek，使用默认值1")
                        1
                    } else {
                        calculatedWeek
                    }
                    _currentWeekNumber.value = safeWeekNumber
                    _actualWeekNumber.value = safeWeekNumber
                    android.util.Log.d(TAG, "最终使用周次: $safeWeekNumber")
                    android.util.Log.d(TAG, "==================")
                    
                    loadCourses(it.id)
                }
            }
        }
    }
    
    private fun loadCourses(scheduleId: Long) {
        courseLoadJob?.cancel()
        adjustmentLoadJob?.cancel()
        
        _courses.value = emptyList()
        _adjustments.value = emptyList()
        courseUseCase.clearCache()
        
        courseLoadJob = viewModelScope.launch {
            android.util.Log.d(TAG, "开始加载课表 ID=$scheduleId 的课程")
            courseUseCase.getCoursesBySchedule(scheduleId).collect { courseList ->
                android.util.Log.d(TAG, "✓ 加载到 ${courseList.size} 门课程")
                courseList.forEachIndexed { index, course ->
                    android.util.Log.d(TAG, "课程$index: ${course.courseName} (星期${course.dayOfWeek}, 第${course.startSection}节, 周次${course.weeks}, 周次类型=${course.weeks::class.simpleName})")
                    if (course.weeks.isNotEmpty()) {
                        val firstWeek = course.weeks.first()
                        android.util.Log.d(TAG, "  └─ 第一周: $firstWeek (类型: ${firstWeek::class.simpleName})")
                        if (firstWeek is String) {
                            android.util.Log.w(TAG, "  ⚠️ 周次数据类型错误：期望Int，实际String")
                        }
                    }
                }
                _courses.value = courseList
                courseUseCase.clearCache()
            }
        }
        
        adjustmentLoadJob = viewModelScope.launch {
            adjustmentUseCase.getAdjustmentsBySchedule(scheduleId).collect { adjustmentList ->
                android.util.Log.d(TAG, "========== 调课记录更新 ==========")
                android.util.Log.d(TAG, "✓ 加载到 ${adjustmentList.size} 条调课记录")
                
                adjustmentList.forEachIndexed { index, adj ->
                    android.util.Log.d(TAG, "调课记录 ${index + 1}:")
                    android.util.Log.d(TAG, "  ID=${adj.id}, 课程ID=${adj.originalCourseId}")
                    android.util.Log.d(TAG, "  原始: 第${adj.originalWeekNumber}周 ${DateUtils.getDayOfWeekName(adj.originalDayOfWeek)} 第${adj.originalStartSection}节")
                    android.util.Log.d(TAG, "  调整: 第${adj.newWeekNumber}周 ${DateUtils.getDayOfWeekName(adj.newDayOfWeek)} 第${adj.newStartSection}节")
                    android.util.Log.d(TAG, "  原因: ${adj.reason}")
                }
                
                _adjustments.value = adjustmentList
                
                val map = adjustmentUseCase.buildAdjustmentMap(adjustmentList)
                _adjustmentMap.value = map
                
                android.util.Log.d(TAG, "调课数据更新，清除所有缓存")
                courseUseCase.clearCache()
                android.util.Log.d(TAG, "====================================")
            }
        }
    }
    
    fun switchViewMode(mode: Int) {
        _viewMode.value = mode
    }
    
    fun changeWeek(weekNumber: Int) {
        val schedule = _currentSchedule.value ?: return
        if (weekNumber in 1..schedule.totalWeeks) {
            _currentWeekNumber.value = weekNumber
        }
    }
    
    fun previousWeek() {
        changeWeek(_currentWeekNumber.value - 1)
    }
    
    fun nextWeek() {
        changeWeek(_currentWeekNumber.value + 1)
    }
    
    fun getCoursesForDay(dayOfWeek: Int, weekNumber: Int): List<Course> {
        return _courses.value.filter {
            it.dayOfWeek == dayOfWeek && weekNumber in it.weeks
        }.sortedBy { it.startSection }
    }
    
    fun getCoursesForWeek(weekNumber: Int): Map<Int, List<Course>> {
        val adjustmentsList = _adjustments.value
        
        if (adjustmentsList.isNotEmpty()) {
            android.util.Log.d(TAG, "getCoursesForWeek: 检测到${adjustmentsList.size}条调课记录，使用合并逻辑")
            return adjustmentUseCase.mergeCoursesWithAdjustments(_courses.value, adjustmentsList, weekNumber)
        }
        
        return courseUseCase.getCoursesForWeek(_courses.value, weekNumber)
    }
    
    fun clearCache() {
        courseUseCase.clearCache()
    }
    
    fun forceRefreshCourses() {
        val scheduleId = _currentSchedule.value?.id ?: return
        courseUseCase.clearCache()
        loadCourses(scheduleId)
    }
    
    @Deprecated("自动更新已改为仅通过通知显示结果，不再需要UI状态")
    fun checkAndPerformAutoUpdate() {
        viewModelScope.launch {
            try {
                val result = autoUpdateUseCase.checkAndPerformAutoUpdate()
                if (result.first) {
                    android.util.Log.d(TAG, "✅ 更新成功: ${result.second}")
                } else {
                    android.util.Log.d(TAG, "❌ 更新失败: ${result.second}")
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "自动更新异常", e)
            }
        }
    }
    
    fun copyCourseToClipboard(course: Course, sourceWeekNumber: Int) {
        clipboardUseCase.copyCourse(course, sourceWeekNumber)
    }
    
    fun pasteCourseFromClipboard(targetWeekNumber: Int, dayOfWeek: Int, startSection: Int): Boolean {
        var result = false
        viewModelScope.launch {
            result = clipboardUseCase.pasteCourse(targetWeekNumber, dayOfWeek, startSection)
            if (result) {
                clearCache()
            }
        }
        return true
    }
    
    fun deleteCourseForWeek(course: Course, weekNumber: Int) {
        viewModelScope.launch {
            try {
                if (course.weeks.size == 1) {
                    _lastDeletedSnapshot = Triple(course, weekNumber, true)
                    courseUseCase.deleteCourse(course)
                } else {
                    _lastDeletedSnapshot = Triple(course, weekNumber, false)
                    val updatedWeeks = course.weeks.filter { it != weekNumber }
                    if (updatedWeeks.isNotEmpty()) {
                        courseUseCase.updateCourse(course.copy(
                            weeks = updatedWeeks,
                            updatedAt = System.currentTimeMillis()
                        ))
                    } else {
                        _lastDeletedSnapshot = Triple(course, weekNumber, true)
                        courseUseCase.deleteCourse(course)
                    }
                }
                forceRefreshCourses()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "删除课程失败", e)
                _lastDeletedSnapshot = null
            }
        }
    }

    fun undoLastDelete() {
        val snapshot = _lastDeletedSnapshot ?: return
        val (originalCourse, weekNumber, wasFullDelete) = snapshot
        _lastDeletedSnapshot = null

        viewModelScope.launch {
            try {
                if (wasFullDelete) {
                    courseUseCase.insertCourse(originalCourse.copy(id = 0))
                } else {
                    val currentCourse = courseUseCase.getCourseById(originalCourse.id)
                    if (currentCourse != null) {
                        val restoredWeeks = (currentCourse.weeks + weekNumber).sorted()
                        courseUseCase.updateCourse(currentCourse.copy(
                            weeks = restoredWeeks,
                            updatedAt = System.currentTimeMillis()
                        ))
                    } else {
                        courseUseCase.insertCourse(originalCourse.copy(id = 0))
                    }
                }
                forceRefreshCourses()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "撤销删除失败", e)
            }
        }
    }
    
    fun getLastDeletedCourseName(): String? {
        return _lastDeletedSnapshot?.first?.courseName
    }
    
    fun clearClipboard() {
        clipboardUseCase.clearClipboard()
    }
    
    fun showClipboardImport() {
        clipboardUseCase.showClipboardImport()
    }
    
    fun hideClipboardImport() {
        clipboardUseCase.hideClipboardImport()
    }
    
    suspend fun importFromClipboard(
        clipboardText: String,
        targetWeekNumber: Int,
        targetDayOfWeek: Int,
        targetStartSection: Int
    ): String {
        val schedule = _currentSchedule.value
        if (schedule == null) {
            return "未选择课表"
        }
        
        val result = clipboardUseCase.importFromClipboard(
            clipboardText,
            targetWeekNumber,
            targetDayOfWeek,
            targetStartSection,
            schedule.id
        )
        
        viewModelScope.launch {
            courseUseCase.clearCache()
        }
        
        return result
    }
    
    fun clearClipboardImportResult() {
        clipboardUseCase.clearClipboardImportResult()
    }
    
    fun updateCurrentSchedule(name: String, startDate: LocalDate, totalWeeks: Int) {
        viewModelScope.launch {
            try {
                val schedule = _currentSchedule.value ?: return@launch
                
                val endDate = startDate.plusWeeks(totalWeeks.toLong()).minusDays(1)
                
                val updatedSchedule = schedule.copy(
                    name = name,
                    startDate = startDate,
                    endDate = endDate,
                    totalWeeks = totalWeeks,
                    updatedAt = System.currentTimeMillis()
                )
                
                scheduleRepository.updateSchedule(updatedSchedule)
                
                courseUseCase.clearCache()
                
                android.util.Log.d(TAG, "✅ 课表更新成功: $name, 起始日期=$startDate, 总周数=$totalWeeks")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "更新课表失败", e)
            }
        }
    }
}