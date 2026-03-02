package com.wind.ggbond.classtime.ui.screen.main

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.data.local.entity.CourseAdjustment
import com.wind.ggbond.classtime.data.model.DisplayCourse
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.util.DateUtils
import com.wind.ggbond.classtime.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate
import javax.inject.Inject

/**
 * 优化的课程数据包装类 - 使用 @Immutable 优化 Compose 重组
 */
@Immutable
data class WeekCoursesData(
    val weekNumber: Int,
    val coursesMap: Map<Int, List<Course>>
)

/**
 * 主界面 ViewModel
 * 
 * ✅ 使用LRU缓存优化性能
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val courseRepository: CourseRepository,
    private val scheduleRepository: ScheduleRepository,
    private val classTimeRepository: com.wind.ggbond.classtime.data.repository.ClassTimeRepository,
    private val adjustmentRepository: com.wind.ggbond.classtime.data.repository.CourseAdjustmentRepository,
    private val examRepository: com.wind.ggbond.classtime.data.repository.ExamRepository,
    private val cookieAutoUpdateService: com.wind.ggbond.classtime.service.CookieAutoUpdateService,
    private val autoUpdateLogRepository: com.wind.ggbond.classtime.data.repository.AutoUpdateLogRepository,
    private val textCourseParser: com.wind.ggbond.classtime.util.TextCourseParser
) : ViewModel() {
    
    companion object {
        private const val TAG = "MainViewModel"
        private val CACHE_MAX_SIZE = Constants.UI.LRU_CACHE_MAX_SIZE  // ✅ 使用Constants
    }
    
    // DataStore 访问
    private val settingsDataStore = com.wind.ggbond.classtime.data.datastore.DataStoreManager.getSettingsDataStore(context)
    
    // 当前课表（包含学期时间信息）
    private val _currentSchedule = MutableStateFlow<Schedule?>(null)
    val currentSchedule: StateFlow<Schedule?> = _currentSchedule.asStateFlow()
    
    // 当前查看的周次（随用户滑动变化）
    private val _currentWeekNumber = MutableStateFlow(1)
    val currentWeekNumber: StateFlow<Int> = _currentWeekNumber.asStateFlow()
    
    // 真实本周（仅由学期开始日期计算，不随滑动变化）
    private val _actualWeekNumber = MutableStateFlow(1)
    val actualWeekNumber: StateFlow<Int> = _actualWeekNumber.asStateFlow()
    
    // 所有课程
    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()
    
    // 所有调课记录
    private val _adjustments = MutableStateFlow<List<CourseAdjustment>>(emptyList())
    val adjustments: StateFlow<List<CourseAdjustment>> = _adjustments.asStateFlow()
    
    // 调课状态映射：记录哪些课程在哪个周次被调整
    // Key: "courseId_weekNumber_dayOfWeek_startSection", Value: adjustment
    private val _adjustmentMap = MutableStateFlow<Map<String, CourseAdjustment>>(emptyMap())
    
    // 考试数据：用于顶部悬浮横幅（当前周+下一周）
    val upcomingExams: StateFlow<List<com.wind.ggbond.classtime.data.local.entity.Exam>> = _currentWeekNumber
        .flatMapLatest { week ->
            examRepository.getExamsByWeekRangeFlow(week, week + 1)
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
    
    // 当前周的精确考试（用于课表格子显示）
    val examsForCurrentWeek: StateFlow<List<com.wind.ggbond.classtime.data.local.entity.Exam>> = _currentWeekNumber
        .flatMapLatest { week ->
            flow {
                try {
                    emit(examRepository.getExamsWithSectionByWeek(week))
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
    
    /**
     * 检查课程是否是调课后的结果
     */
    fun isAdjustedCourse(courseId: Long, weekNumber: Int, dayOfWeek: Int, startSection: Int): CourseAdjustment? {
        val key = "${courseId}_${weekNumber}_${dayOfWeek}_${startSection}"
        return _adjustmentMap.value[key]
    }
    
    // 上课时间表
    val classTimes = classTimeRepository.getClassTimesByConfig("default")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // 视图模式：0=周视图，1=日视图
    private val _viewMode = MutableStateFlow(0)
    val viewMode: StateFlow<Int> = _viewMode.asStateFlow()
    
    // 当前选中的日期（用于日视图）
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()
    
    // ✅ 课程剪贴板：存储复制的课程及其目标周次
    private val _clipboard = MutableStateFlow<Pair<Course, Int>?>(null)
    val clipboard: StateFlow<Pair<Course, Int>?> = _clipboard.asStateFlow()
    
    // 剪贴板智能导入对话框状态
    private val _showClipboardImport = MutableStateFlow(false)
    val showClipboardImport: StateFlow<Boolean> = _showClipboardImport.asStateFlow()
    
    // 剪贴板导入结果
    private val _clipboardImportResult = MutableStateFlow<String?>(null)
    val clipboardImportResult: StateFlow<String?> = _clipboardImportResult.asStateFlow()
    
    // ✅ 性能优化：使用线程安全的 ConcurrentHashMap 缓存周课程数据
    private val weekCoursesCache = java.util.concurrent.ConcurrentHashMap<Int, Map<Int, List<Course>>>()
    
    // 缓存统计
    private var cacheHits = 0
    private var cacheMisses = 0
    
    // ✅ 优化：使用 LRU缓存减少不必要的计算，并整合调课记录
    val coursesForCurrentWeek: StateFlow<Map<Int, List<Course>>> = combine(
        _courses,
        _adjustments,
        _currentWeekNumber
    ) { coursesList, adjustmentsList, weekNumber ->
        // ⚠️ 注意：当调课记录变化时，必须清除所有缓存
        // 因为调课可能影响多个周次的显示
        
        // 如果有调课记录，强制重新计算（不使用缓存）
        if (adjustmentsList.isNotEmpty()) {
            // 使用合并方法计算（不缓存有调课的结果，确保每次都是最新的）
            mergeCoursesWithAdjustments(coursesList, adjustmentsList, weekNumber)
        } else {
            // 优先从缓存读取（ConcurrentHashMap 线程安全，无需 synchronized）
            weekCoursesCache[weekNumber] ?: run {
                cacheMisses++
                // 计算并缓存（无调课的情况）
                val coursesMap = (1..7).associateWith { dayOfWeek ->
                    coursesList.filter {
                        it.dayOfWeek == dayOfWeek && weekNumber in it.weeks
                    }.sortedBy { it.startSection }
                }
                // 缓存大小控制：超出上限时清除最早的条目
                if (weekCoursesCache.size >= CACHE_MAX_SIZE) {
                    weekCoursesCache.keys.firstOrNull()?.let { weekCoursesCache.remove(it) }
                }
                weekCoursesCache[weekNumber] = coursesMap
                // 预加载相邻周的数据
                preloadAdjacentWeeks(coursesList, weekNumber)
                coursesMap
            }.also { cacheHits++ }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )
    
    /**
     * ✅ 获取缓存命中率
     */
    private fun getCacheHitRate(): Int {
        val total = cacheHits + cacheMisses
        return if (total > 0) {
            ((cacheHits.toDouble() / total) * 100).toInt()
        } else {
            0
        }
    }
    
    /**
     * ✅ 清除缓存（在数据变化时调用）
     */
    fun clearCache() {
        synchronized(weekCoursesCache) {
            weekCoursesCache.clear()
            cacheHits = 0
            cacheMisses = 0
            // Debug logging removed for compilation compatibility
        }
    }
    
    init {
        loadCurrentSchedule()
        
        // ✅ 优化：在ViewModel创建时就开始预加载当前周的数据
        viewModelScope.launch {
            // 等待课表和课程数据加载
            _currentSchedule.first { it != null }
            _courses.first { it.isNotEmpty() }
            
            // 立即预加载当前周和相邻周的数据
            val currentWeek = _currentWeekNumber.value
            android.util.Log.d(TAG, "ViewModel初始化：开始预加载周次 $currentWeek 及相邻周")
            
            preloadAdjacentWeeks(_courses.value, currentWeek)
            
            android.util.Log.d(TAG, "ViewModel初始化：预加载完成")
        }
    }
    
    private fun loadCurrentSchedule() {
        viewModelScope.launch {
            scheduleRepository.getCurrentScheduleFlow().collect { schedule ->
                android.util.Log.d(TAG, "当前课表变化: ${schedule?.name} (ID=${schedule?.id})")
                _currentSchedule.value = schedule
                schedule?.let {
                    // 从课表中获取学期时间信息，计算当前周次
                    android.util.Log.d(TAG, "=== 课表信息加载 ===")
                    android.util.Log.d(TAG, "课表名称: ${it.name}")
                    android.util.Log.d(TAG, "开始日期: ${it.startDate}")
                    android.util.Log.d(TAG, "结束日期: ${it.endDate}")
                    android.util.Log.d(TAG, "总周数: ${it.totalWeeks}")
                    
                    val calculatedWeek = DateUtils.calculateWeekNumber(it.startDate)
                    android.util.Log.d(TAG, "计算的周次: $calculatedWeek")
                    
                    // 安全检查：确保周次在合理范围内
                    val safeWeekNumber = if (calculatedWeek < 1 || calculatedWeek > 30) {
                        android.util.Log.w(TAG, "计算的周次异常: $calculatedWeek，使用默认值1")
                        1
                    } else {
                        calculatedWeek
                    }
                    _currentWeekNumber.value = safeWeekNumber
                    _actualWeekNumber.value = safeWeekNumber  // 真实本周，不随滑动变化
                    android.util.Log.d(TAG, "最终使用周次: $safeWeekNumber")
                    android.util.Log.d(TAG, "==================")
                    
                    loadCourses(it.id)
                }
            }
        }
    }
    
    // ✅ 修复：保存课程加载协程的Job，用于取消旧的加载任务
    private var courseLoadJob: kotlinx.coroutines.Job? = null
    private var adjustmentLoadJob: kotlinx.coroutines.Job? = null
    
    private fun loadCourses(scheduleId: Long) {
        // ✅ 修复：取消之前的加载任务，避免多个协程同时收集不同scheduleId的数据
        courseLoadJob?.cancel()
        adjustmentLoadJob?.cancel()
        
        // ✅ 修复：清空旧数据，确保UI不会显示旧课表的数据
        _courses.value = emptyList()
        _adjustments.value = emptyList()
        clearCache()
        
        courseLoadJob = viewModelScope.launch {
            android.util.Log.d(TAG, "开始加载课表 ID=$scheduleId 的课程")
            courseRepository.getAllCoursesBySchedule(scheduleId).collect { courseList ->
                android.util.Log.d(TAG, "✓ 加载到 ${courseList.size} 门课程")
                courseList.forEachIndexed { index, course ->
                    android.util.Log.d(TAG, "课程$index: ${course.courseName} (星期${course.dayOfWeek}, 第${course.startSection}节, 周次${course.weeks}, 周次类型=${course.weeks::class.simpleName})")
                    // 检查课程数据格式问题
                    if (course.weeks.isNotEmpty()) {
                        val firstWeek = course.weeks.first()
                        android.util.Log.d(TAG, "  └─ 第一周: $firstWeek (类型: ${firstWeek::class.simpleName})")
                        if (firstWeek is String) {
                            android.util.Log.w(TAG, "  ⚠️ 周次数据类型错误：期望Int，实际String")
                        }
                    }
                }
                _courses.value = courseList
                // 课程数据更新时清除缓存
                clearCache()
            }
        }
        
        // 同时加载调课记录
        adjustmentLoadJob = viewModelScope.launch {
            adjustmentRepository.getAdjustmentsBySchedule(scheduleId).collect { adjustmentList ->
                android.util.Log.d(TAG, "========== 调课记录更新 ==========")
                android.util.Log.d(TAG, "✓ 加载到 ${adjustmentList.size} 条调课记录")
                
                // 详细日志：列出所有调课记录
                adjustmentList.forEachIndexed { index, adj ->
                    android.util.Log.d(TAG, "调课记录 ${index + 1}:")
                    android.util.Log.d(TAG, "  ID=${adj.id}, 课程ID=${adj.originalCourseId}")
                    android.util.Log.d(TAG, "  原始: 第${adj.originalWeekNumber}周 ${DateUtils.getDayOfWeekName(adj.originalDayOfWeek)} 第${adj.originalStartSection}节")
                    android.util.Log.d(TAG, "  调整: 第${adj.newWeekNumber}周 ${DateUtils.getDayOfWeekName(adj.newDayOfWeek)} 第${adj.newStartSection}节")
                    android.util.Log.d(TAG, "  原因: ${adj.reason}")
                }
                
                _adjustments.value = adjustmentList
                
                // 构建调课映射表
                val map = mutableMapOf<String, CourseAdjustment>()
                adjustmentList.forEach { adj ->
                    // 标记新位置的课程
                    val key = "${adj.originalCourseId}_${adj.newWeekNumber}_${adj.newDayOfWeek}_${adj.newStartSection}"
                    map[key] = adj
                }
                _adjustmentMap.value = map
                
                // 调课数据更新时清除缓存
                android.util.Log.d(TAG, "调课数据更新，清除所有缓存")
                clearCache()
                android.util.Log.d(TAG, "====================================")
            }
        }
    }
    
    /**
     * 合并课程和调课记录，生成用于显示的课程列表
     * 
     * ⚠️ 重要：这个函数决定了课表显示的内容
     * - 被调走的课程在原时间不显示
     * - 调课后的课程在新时间显示
     */
    private fun mergeCoursesWithAdjustments(
        courses: List<Course>,
        adjustments: List<CourseAdjustment>,
        weekNumber: Int
    ): Map<Int, List<Course>> {
        android.util.Log.d(TAG, "=== 开始合并课程和调课记录 ===")
        android.util.Log.d(TAG, "周次=$weekNumber, 原始课程数=${courses.size}, 调课记录数=${adjustments.size}")
        
        val displayCoursesMap = mutableMapOf<Int, MutableList<Course>>()
        
        // ✅ 修复：使用更精确的key来记录被调走的课程
        // Key格式: "courseId_weekNumber_dayOfWeek_startSection"
        val adjustedCourseKeys = mutableSetOf<String>()
        
        // 处理调课记录
        adjustments.forEach { adjustment ->
            android.util.Log.d(TAG, "处理调课记录: ID=${adjustment.id}, 课程ID=${adjustment.originalCourseId}")
            android.util.Log.d(TAG, "  原始: 第${adjustment.originalWeekNumber}周 ${DateUtils.getDayOfWeekName(adjustment.originalDayOfWeek)} 第${adjustment.originalStartSection}节")
            android.util.Log.d(TAG, "  新的: 第${adjustment.newWeekNumber}周 ${DateUtils.getDayOfWeekName(adjustment.newDayOfWeek)} 第${adjustment.newStartSection}节")
            
            if (adjustment.originalWeekNumber == weekNumber) {
                // ✅ 使用精确的key：包含原始的时间信息
                val key = "${adjustment.originalCourseId}_${weekNumber}_${adjustment.originalDayOfWeek}_${adjustment.originalStartSection}"
                adjustedCourseKeys.add(key)
                android.util.Log.d(TAG, "  → 标记原时间不显示: $key")
                
                // 在新时间显示（如果新时间也在当前周）
                if (adjustment.newWeekNumber == weekNumber) {
                    val course = courses.find { it.id == adjustment.originalCourseId }
                    if (course != null) {
                        // 创建一个临时的Course对象，用新的时间信息
                        val adjustedCourse = course.copy(
                            dayOfWeek = adjustment.newDayOfWeek,
                            startSection = adjustment.newStartSection,
                            sectionCount = adjustment.newSectionCount
                        )
                        displayCoursesMap.getOrPut(adjustment.newDayOfWeek) { mutableListOf() }
                            .add(adjustedCourse)
                        android.util.Log.d(TAG, "  ✓ 新时间显示: ${course.courseName} 在${DateUtils.getDayOfWeekName(adjustment.newDayOfWeek)}第${adjustment.newStartSection}节")
                    } else {
                        android.util.Log.w(TAG, "  ⚠️ 未找到课程ID=${adjustment.originalCourseId}")
                    }
                }
            } else if (adjustment.newWeekNumber == weekNumber) {
                // 其他周的课调到本周
                val course = courses.find { it.id == adjustment.originalCourseId }
                if (course != null) {
                    val adjustedCourse = course.copy(
                        dayOfWeek = adjustment.newDayOfWeek,
                        startSection = adjustment.newStartSection,
                        sectionCount = adjustment.newSectionCount
                    )
                    displayCoursesMap.getOrPut(adjustment.newDayOfWeek) { mutableListOf() }
                        .add(adjustedCourse)
                    android.util.Log.d(TAG, "  ✓ 其他周调入本周: ${course.courseName} (从第${adjustment.originalWeekNumber}周)")
                } else {
                    android.util.Log.w(TAG, "  ⚠️ 未找到课程ID=${adjustment.originalCourseId}")
                }
            }
        }
        
        // 添加正常课程（排除被调走的）
        android.util.Log.d(TAG, "--- 添加正常课程 ---")
        courses.forEach { course ->
            if (weekNumber in course.weeks) {
                // ✅ 修复：检查精确的key
                val key = "${course.id}_${weekNumber}_${course.dayOfWeek}_${course.startSection}"
                if (!adjustedCourseKeys.contains(key)) {
                    displayCoursesMap.getOrPut(course.dayOfWeek) { mutableListOf() }
                        .add(course)
                    android.util.Log.d(TAG, "  ✓ 添加正常课程: ${course.courseName} ${DateUtils.getDayOfWeekName(course.dayOfWeek)}第${course.startSection}节")
                } else {
                    android.util.Log.d(TAG, "  ✗ 跳过被调走课程: ${course.courseName} (key=$key)")
                }
            }
        }
        
        // 打印最终结果
        android.util.Log.d(TAG, "--- 合并结果 ---")
        displayCoursesMap.forEach { (day, courseList) ->
            android.util.Log.d(TAG, "第${weekNumber}周 ${DateUtils.getDayOfWeekName(day)}: ${courseList.size}门课")
            courseList.forEach { course ->
                android.util.Log.d(TAG, "  - ${course.courseName} 第${course.startSection}节")
            }
        }
        android.util.Log.d(TAG, "=== 合并完成 ===")
        
        // 排序并返回
        return (1..7).associateWith { dayOfWeek ->
            displayCoursesMap[dayOfWeek]?.sortedBy { it.startSection } ?: emptyList()
        }
    }
    
    /**
     * 切换视图模式
     */
    fun switchViewMode(mode: Int) {
        _viewMode.value = mode
    }
    
    /**
     * 切换周次
     */
    fun changeWeek(weekNumber: Int) {
        val schedule = _currentSchedule.value ?: return
        if (weekNumber in 1..schedule.totalWeeks) {
            _currentWeekNumber.value = weekNumber
        }
    }
    
    /**
     * 上一周
     */
    fun previousWeek() {
        changeWeek(_currentWeekNumber.value - 1)
    }
    
    /**
     * 下一周
     */
    fun nextWeek() {
        changeWeek(_currentWeekNumber.value + 1)
    }
    
    /**
     * 获取指定星期和周次的课程
     */
    fun getCoursesForDay(dayOfWeek: Int, weekNumber: Int): List<Course> {
        return _courses.value.filter {
            it.dayOfWeek == dayOfWeek && weekNumber in it.weeks
        }.sortedBy { it.startSection }
    }
    
    /**
     * 获取一周的所有课程（按星期分组）- 优化版本，使用缓存
     */
    fun getCoursesForWeek(weekNumber: Int): Map<Int, List<Course>> {
        // ✅ 修复：如果有调课记录，使用合并逻辑而不是缓存
        val adjustmentsList = _adjustments.value
        
        if (adjustmentsList.isNotEmpty()) {
            android.util.Log.d(TAG, "getCoursesForWeek: 检测到${adjustmentsList.size}条调课记录，使用合并逻辑")
            // 直接使用合并方法，不使用缓存
            return mergeCoursesWithAdjustments(_courses.value, adjustmentsList, weekNumber)
        }
        
        // 无调课时，使用原有的缓存逻辑
        return weekCoursesCache[weekNumber] ?: run {
            // 缓存未命中，计算并缓存
            val coursesMap = (1..7).associateWith { dayOfWeek ->
                getCoursesForDay(dayOfWeek, weekNumber)
            }
            weekCoursesCache[weekNumber] = coursesMap
            
            // 预加载相邻周
            preloadAdjacentWeeks(_courses.value, weekNumber)
            
            coursesMap
        }
    }
    
    /**
     * 性能优化：预加载相邻周的课程数据
     * 类似于视频APP的预加载机制，提前准备用户可能查看的内容
     * 
     * ✅ 优化：预加载范围从1周扩展到2周，配合HorizontalPager的beyondBoundsPageCount
     */
    private fun preloadAdjacentWeeks(coursesList: List<Course>, currentWeek: Int) {
        // 将较重的预加载逻辑放到 Default 线程池上执行，避免阻塞主线程动画
        viewModelScope.launch(Dispatchers.Default) {
            val schedule = _currentSchedule.value ?: return@launch

            val weeksToPreload = listOf(
                currentWeek - 2, currentWeek - 1,
                currentWeek + 1, currentWeek + 2
            )

            val preparedMaps = mutableMapOf<Int, Map<Int, List<Course>>>()

            weeksToPreload.forEach { week ->
                if (week in 1..schedule.totalWeeks && !weekCoursesCache.containsKey(week)) {
                    val coursesMap = (1..7).associateWith { dayOfWeek ->
                        coursesList.filter {
                            it.dayOfWeek == dayOfWeek && week in it.weeks
                        }.sortedBy { it.startSection }
                    }
                    preparedMaps[week] = coursesMap
                }
            }

            // ConcurrentHashMap 线程安全，无需 synchronized
            preparedMaps.forEach { (week, map) ->
                weekCoursesCache.putIfAbsent(week, map)
            }
        }
    }
    
    /**
     * ✅ 检查并执行自动更新（基于Cookie）
     * 注意：此方法已废弃，不再从UI主动调用，仅通过后台服务和WorkManager触发
     */
    @Deprecated("自动更新已改为仅通过通知显示结果，不再需要UI状态")
    fun checkAndPerformAutoUpdate() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            try {
                android.util.Log.d(TAG, "🔍 检查是否需要自动更新")
                
                // 检查是否需要更新，并获取详细原因
                val skipReason = getSkipReason()
                if (skipReason != null) {
                    android.util.Log.d(TAG, "无需更新: $skipReason")
                    
                    // 记录跳过日志
                    val durationMs = System.currentTimeMillis() - startTime
                    autoUpdateLogRepository.logUpdate(
                        triggerEvent = "进入软件",
                        result = com.wind.ggbond.classtime.data.local.entity.UpdateResult.SKIPPED,
                        successMessage = null,
                        failureReason = skipReason,
                        scheduleId = _currentSchedule.value?.id,
                        durationMs = durationMs
                    )
                    
                    return@launch
                }
                
                android.util.Log.d(TAG, "🚀 开始自动更新（Cookie模式）")
                
                // 执行更新
                val result = cookieAutoUpdateService.performUpdate()
                
                // 记录更新日志
                val durationMs = System.currentTimeMillis() - startTime
                if (result.first) {
                    android.util.Log.d(TAG, "✅ 更新成功: ${result.second}")
                    autoUpdateLogRepository.logUpdate(
                        triggerEvent = "进入软件",
                        result = com.wind.ggbond.classtime.data.local.entity.UpdateResult.SUCCESS,
                        successMessage = result.second,
                        failureReason = null,
                        scheduleId = _currentSchedule.value?.id,
                        durationMs = durationMs
                    )
                } else {
                    android.util.Log.d(TAG, "❌ 更新失败: ${result.second}")
                    autoUpdateLogRepository.logUpdate(
                        triggerEvent = "进入软件",
                        result = com.wind.ggbond.classtime.data.local.entity.UpdateResult.FAILED,
                        successMessage = null,
                        failureReason = result.second,
                        scheduleId = _currentSchedule.value?.id,
                        durationMs = durationMs
                    )
                }
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "自动更新异常", e)
                
                // 记录异常日志
                val durationMs = System.currentTimeMillis() - startTime
                autoUpdateLogRepository.logUpdate(
                    triggerEvent = "进入软件",
                    result = com.wind.ggbond.classtime.data.local.entity.UpdateResult.FAILED,
                    successMessage = null,
                    failureReason = e.message ?: "未知错误",
                    scheduleId = _currentSchedule.value?.id,
                    durationMs = durationMs
                )
            }
        }
    }
    
    /**
     * 获取跳过更新的详细原因
     * @return 跳过原因，如果应该更新则返回null
     */
    private suspend fun getSkipReason(): String? {
        // 0. ✅ 首先检查用户是否开启了自动更新
        val preferences = settingsDataStore.data.first()
        val autoUpdateEnabled = preferences[com.wind.ggbond.classtime.data.datastore.DataStoreManager.SettingsKeys.AUTO_UPDATE_ENABLED_KEY] 
            ?: com.wind.ggbond.classtime.data.datastore.DataStoreManager.SettingsKeys.DEFAULT_AUTO_UPDATE_ENABLED
        
        if (!autoUpdateEnabled) {
            return "用户未开启自动更新功能"
        }
        
        // 1. 检查是否有当前课表
        val currentSchedule = scheduleRepository.getCurrentSchedule()
        if (currentSchedule == null) {
            return "尚未导入课表，无法自动更新"
        }
        
        // 2. 检查学校配置是否存在
        val schoolId = currentSchedule.schoolName ?: ""
        if (schoolId.isEmpty()) {
            return "课表缺少学校配置信息"
        }
        
        // 3. 检查是否有Cookie（通过CookieAutoUpdateService检查）
        // 这里简化处理，直接调用shouldUpdate，如果返回false且上面条件都满足，说明是时间或Cookie问题
        if (!cookieAutoUpdateService.shouldUpdate()) {
            // 检查更新间隔
            val lastUpdateTime = currentSchedule.updatedAt
            val now = System.currentTimeMillis()
            val hoursSinceUpdate = (now - lastUpdateTime) / (1000 * 60 * 60)
            
            if (hoursSinceUpdate < 6) {
                return "距上次更新仅${hoursSinceUpdate}小时，最小间隔为6小时"
            } else {
                return "无有效登录凭证(Cookie)，请先手动登录导入课表"
            }
        }
        
        return null // 应该更新
    }
    
    /**
     * ✅ 复制课程到剪贴板
     * @param course 要复制的课程
     * @param sourceWeekNumber 源周次（用于后续粘贴时参考）
     */
    fun copyCourseToClipboard(course: Course, sourceWeekNumber: Int) {
        _clipboard.value = Pair(course, sourceWeekNumber)
        android.util.Log.d(TAG, "✅ 课程已复制到剪贴板: ${course.courseName} (周$sourceWeekNumber)")
    }
    
    /**
     * ✅ 粘贴课程到指定周次
     * @param targetWeekNumber 目标周次
     * @param dayOfWeek 目标星期（1-7）
     * @param startSection 目标开始节次
     * @return 是否粘贴成功
     */
    fun pasteCourseFromClipboard(targetWeekNumber: Int, dayOfWeek: Int, startSection: Int): Boolean {
        val (copiedCourse, _) = _clipboard.value ?: return false
        
        viewModelScope.launch {
            try {
                // ✅ 检查冲突：目标时间段是否已有课程
                val coursesForWeek = weekCoursesCache[targetWeekNumber] ?: emptyMap()
                
                val dayCourses = coursesForWeek[dayOfWeek] ?: emptyList()
                val hasConflict = dayCourses.any { existingCourse ->
                    val existingEnd = existingCourse.startSection + existingCourse.sectionCount - 1
                    val newEnd = startSection + copiedCourse.sectionCount - 1
                    
                    // 检查时间段是否重叠
                    !(newEnd < existingCourse.startSection || startSection > existingEnd)
                }
                
                if (hasConflict) {
                    android.util.Log.w(TAG, "粘贴失败：目标时间段已有课程")
                    return@launch
                }
                
                // 创建新课程，仅修改周次为目标周次
                val newCourse = copiedCourse.copy(
                    id = 0,  // 新建课程
                    weeks = listOf(targetWeekNumber),
                    dayOfWeek = dayOfWeek,
                    startSection = startSection,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                
                // 保存新课程
                courseRepository.insertCourse(newCourse)
                
                // 清除缓存以刷新UI
                clearCache()
                
                android.util.Log.d(TAG, "课程已粘贴到周$targetWeekNumber 星期$dayOfWeek 第$startSection: ${newCourse.courseName}")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "粘贴课程失败", e)
            }
        }
        
        return true
    }
    
    // 删除撤销：保存删除前的课程快照和操作类型
    private var _lastDeletedSnapshot: Triple<Course, Int, Boolean>? = null  // (原始课程, 周次, 是否整条删除)

    /**
     * ✅ 删除指定周次的课程（支持撤销）
     * @param course 要删除的课程
     * @param weekNumber 删除的周次（如果课程跨多周，仅删除该周）
     */
    fun deleteCourseForWeek(course: Course, weekNumber: Int) {
        viewModelScope.launch {
            try {
                if (course.weeks.size == 1) {
                    // 保存快照：整条删除
                    _lastDeletedSnapshot = Triple(course, weekNumber, true)
                    courseRepository.deleteCourse(course)
                } else {
                    // 保存快照：仅移除某周
                    _lastDeletedSnapshot = Triple(course, weekNumber, false)
                    val updatedWeeks = course.weeks.filter { it != weekNumber }
                    if (updatedWeeks.isNotEmpty()) {
                        courseRepository.updateCourse(course.copy(
                            weeks = updatedWeeks,
                            updatedAt = System.currentTimeMillis()
                        ))
                    } else {
                        // 实际变成整条删除
                        _lastDeletedSnapshot = Triple(course, weekNumber, true)
                        courseRepository.deleteCourse(course)
                    }
                }
                clearCache()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "删除课程失败", e)
                _lastDeletedSnapshot = null
            }
        }
    }

    /**
     * ✅ 撤销上次删除操作
     */
    fun undoLastDelete() {
        val snapshot = _lastDeletedSnapshot ?: return
        val (originalCourse, weekNumber, wasFullDelete) = snapshot
        _lastDeletedSnapshot = null

        viewModelScope.launch {
            try {
                if (wasFullDelete) {
                    // 整条删除：重新插入原始课程
                    courseRepository.insertCourse(originalCourse.copy(id = 0))
                } else {
                    // 仅移除某周：将该周加回 weeks 列表
                    val currentCourse = courseRepository.getCourseById(originalCourse.id)
                    if (currentCourse != null) {
                        val restoredWeeks = (currentCourse.weeks + weekNumber).sorted()
                        courseRepository.updateCourse(currentCourse.copy(
                            weeks = restoredWeeks,
                            updatedAt = System.currentTimeMillis()
                        ))
                    } else {
                        // 课程已不存在，重新插入
                        courseRepository.insertCourse(originalCourse.copy(id = 0))
                    }
                }
                clearCache()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "撤销删除失败", e)
            }
        }
    }

    /**
     * 获取上次删除的课程名称（用于Snackbar显示）
     */
    fun getLastDeletedCourseName(): String? {
        return _lastDeletedSnapshot?.first?.courseName
    }
    
    /**
     * ✅ 清空剪贴板
     */
    fun clearClipboard() {
        _clipboard.value = null
    }
    
    // ===================== 剪贴板智能导入 =====================
    
    /**
     * 显示剪贴板导入对话框
     */
    fun showClipboardImport() {
        _showClipboardImport.value = true
    }
    
    /**
     * 隐藏剪贴板导入对话框
     */
    fun hideClipboardImport() {
        _showClipboardImport.value = false
    }
    
    /**
     * 从剪贴板文本解析并创建课程
     * @param clipboardText 剪贴板文本
     * @param targetWeekNumber 目标周次
     * @param targetDayOfWeek 目标星期几
     * @param targetStartSection 目标开始节次
     * @return 解析结果描述
     */
    fun importFromClipboard(
        clipboardText: String,
        targetWeekNumber: Int,
        targetDayOfWeek: Int,
        targetStartSection: Int
    ): String {
        if (clipboardText.isBlank()) {
            _clipboardImportResult.value = "剪贴板内容为空"
            return "剪贴板内容为空"
        }

        val parsedCourses = textCourseParser.parse(clipboardText)
        if (parsedCourses.isEmpty()) {
            _clipboardImportResult.value = "未能识别课程信息"
            return "未能识别课程信息"
        }

        var successCount = 0
        var errorMessages = mutableListOf<String>()

        viewModelScope.launch {
            try {
                val schedule = _currentSchedule.value
                if (schedule == null) {
                    _clipboardImportResult.value = "未选择课表"
                    return@launch
                }

                parsedCourses.forEach { parsed ->
                    try {
                        // 使用解析出的周次，如果为空则使用目标周次
                        val weeks = if (parsed.weeks.isNotEmpty()) parsed.weeks else listOf(targetWeekNumber)
                        
                        // 创建新课程
                        val newCourse = com.wind.ggbond.classtime.data.local.entity.Course(
                            id = 0, // 新建课程
                            scheduleId = schedule.id,
                            courseName = parsed.courseName,
                            teacher = parsed.teacher,
                            classroom = parsed.classroom,
                            dayOfWeek = targetDayOfWeek,
                            startSection = targetStartSection,
                            sectionCount = if (parsed.endSection > 0) 
                                parsed.endSection 
                            else 1,
                            weeks = weeks,
                            color = com.wind.ggbond.classtime.util.CourseColorPalette.getColorForCourse(
                                parsed.courseName,
                                emptyList()
                            ),
                            credit = parsed.credit,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )

                        // 计算持续节次
                        val sectionCount = if (parsed.endSection > 0) 
                            parsed.endSection 
                        else 1

                        // 检查冲突
                        val conflicts = courseRepository.detectConflictWithWeeks(
                            scheduleId = schedule.id,
                            dayOfWeek = targetDayOfWeek,
                            startSection = targetStartSection,
                            sectionCount = sectionCount,
                            weeks = weeks
                        )

                        if (conflicts.isNotEmpty()) {
                            errorMessages.add("${parsed.courseName}: 时间冲突")
                            return@forEach
                        }

                        // 保存课程
                        courseRepository.insertCourse(newCourse)
                        successCount++
                        
                        android.util.Log.d(TAG, "✅ 导入课程成功: ${parsed.courseName}")
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "导入课程失败: ${parsed.courseName}", e)
                        errorMessages.add("${parsed.courseName}: ${e.message}")
                    }
                }

                // 清除缓存以刷新UI
                clearCache()

                // 构建结果消息
                val result = if (successCount > 0) {
                    "成功导入 $successCount 门课程" + 
                    if (errorMessages.isNotEmpty()) {
                        "\n失败: ${errorMessages.joinToString(", ")}"
                    } else ""
                } else {
                    "导入失败: ${errorMessages.joinToString(", ")}"
                }

                _clipboardImportResult.value = result
            } catch (e: Exception) {
                android.util.Log.e(TAG, "批量导入失败", e)
                _clipboardImportResult.value = "导入失败: ${e.message}"
            }
        }

        return "正在解析导入..."
    }
    
    /**
     * 清除剪贴板导入结果
     */
    fun clearClipboardImportResult() {
        _clipboardImportResult.value = null
    }
}
