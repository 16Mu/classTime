package com.wind.ggbond.classtime.ui.screen.scheduleimport

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.data.repository.SchoolRepository
import com.wind.ggbond.classtime.ui.components.ScheduleSelectionState
import com.wind.ggbond.classtime.ui.components.checkScheduleState
import com.wind.ggbond.classtime.util.CourseColorPalette
import com.wind.ggbond.classtime.util.CourseColorProvider
import com.wind.ggbond.classtime.util.HtmlScheduleParser
import com.wind.ggbond.classtime.util.SecureCookieManager
import com.wind.ggbond.classtime.util.WeekParser
import com.wind.ggbond.classtime.domain.usecase.ImportUseCase
import com.wind.ggbond.classtime.service.contract.CookieExpiredException
import com.wind.ggbond.classtime.service.contract.IAlarmScheduler
import com.wind.ggbond.classtime.service.contract.IScheduleFetcher
import com.wind.ggbond.classtime.service.helper.ImportConfidence
import com.wind.ggbond.classtime.service.helper.ImportRouter
import com.wind.ggbond.classtime.service.helper.ImportRouteDecision
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Inject

@HiltViewModel
class ImportScheduleViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val scheduleRepository: ScheduleRepository,
    private val schoolRepository: SchoolRepository,
    private val secureCookieManager: SecureCookieManager,
    private val htmlParser: HtmlScheduleParser,
    private val unifiedScheduleFetchService: IScheduleFetcher,
    private val reminderScheduler: IAlarmScheduler,
    private val importRouter: ImportRouter,
    private val importUseCase: ImportUseCase,
    private val application: Application
) : ViewModel() {
    
    // 使用统一的 DataStore 管理器
    private val classTimeDataStore = DataStoreManager.getClassTimeDataStore(application)
    private val MORNING_SECTIONS_KEY = DataStoreManager.ClassTimeKeys.MORNING_SECTIONS_KEY
    private val AFTERNOON_SECTIONS_KEY = DataStoreManager.ClassTimeKeys.AFTERNOON_SECTIONS_KEY
    
    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()
    
    private val _parsedCourses = MutableStateFlow<List<ParsedCourse>>(emptyList())
    val parsedCourses: StateFlow<List<ParsedCourse>> = _parsedCourses.asStateFlow()
    
    private val _scheduleState = MutableStateFlow<ScheduleSelectionState>(ScheduleSelectionState.Loading)
    val scheduleState: StateFlow<ScheduleSelectionState> = _scheduleState.asStateFlow()
    
    private val _importProgress = MutableStateFlow(ImportProgress())
    val importProgress: StateFlow<ImportProgress> = _importProgress.asStateFlow()
    
    private val _cookieExpiredEvent = MutableStateFlow<String?>(null)
    val cookieExpiredEvent: StateFlow<String?> = _cookieExpiredEvent.asStateFlow()
    
    private var lastFailedAction: (suspend () -> Unit)? = null
    private var currentSchoolId: String = ""
    
    init {
        // 初始化时检查课表状态
        checkCurrentScheduleState()
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
            } catch (e: Exception) {
                AppLogger.w("ImportSchedule", "检查课表状态失败: ${e.message}")
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
                AppLogger.d("ImportSchedule", "创建课表成功: $name, ID: $scheduleId")
                // 更新状态为就绪
                _scheduleState.value = ScheduleSelectionState.Ready
            } catch (e: Exception) {
                AppLogger.e("ImportSchedule", "创建课表失败", e)
                _importState.value = ImportState.Error("创建课表失败：${e.message}")
            }
        }
    }
    
    /**
     * 确认继续使用过期课表
     */
    fun confirmUseExpiredSchedule() {
        _scheduleState.value = ScheduleSelectionState.Ready
        AppLogger.d("ImportSchedule", "用户选择继续使用过期课表")
    }
    
    /**
     * 用户选择创建新课表（从过期提示）
     * 将状态切换为需要创建
     */
    fun switchToCreateNewSchedule() {
        _scheduleState.value = ScheduleSelectionState.NeedCreate
        AppLogger.d("ImportSchedule", "用户选择创建新课表")
    }
    
    fun previewCourses(courses: List<ParsedCourse>, schoolId: String = "") {
        _parsedCourses.value = courses
        currentSchoolId = schoolId
        _importState.value = ImportState.Preview
    }

    fun confirmImport(overrideMorningSections: Int? = null, overrideAfternoonSections: Int? = null) {
        val capturedMorningSections = overrideMorningSections
        val capturedAfternoonSections = overrideAfternoonSections
        lastFailedAction = { performImport(capturedMorningSections, capturedAfternoonSections) }

        val incompleteCourses = checkIncompleteCourses(_parsedCourses.value)
        if (incompleteCourses.isNotEmpty()) {
            AppLogger.d("ImportSchedule", "检测到${incompleteCourses.size}门课程信息不完整，需要手动填写")
            _importState.value = ImportState.NeedManualFill(incompleteCourses)
            return
        }

        viewModelScope.launch {
            try {
                performImport(capturedMorningSections, capturedAfternoonSections)
            } catch (e: Exception) {
                AppLogger.e("ImportSchedule", "✗ 导入失败", e)
                AppLogger.e("ImportSchedule", "错误详情: ${e.message}")
                AppLogger.e("ImportSchedule", "错误堆栈: ${e.stackTraceToString()}")
                handleError(e)
            }
        }
    }
    
    private suspend fun performImport(overrideMorningSections: Int? = null, overrideAfternoonSections: Int? = null) {
        _importState.value = ImportState.Loading
        _importProgress.value = ImportProgress(step = ImportStep.CONNECTING)
        
        AppLogger.d("ImportSchedule", "=== 开始导入流程 ===")
        AppLogger.d("ImportSchedule", "待导入课程数量: ${_parsedCourses.value.size}")
        
        if (_parsedCourses.value.isEmpty()) {
            AppLogger.e("ImportSchedule", "✗ 没有课程数据可导入")
            _importState.value = ImportState.Error("没有课程数据可导入")
            return
        }
        
        _importProgress.value = ImportProgress(step = ImportStep.FETCHING_DATA)
        
        val currentSchedule = scheduleRepository.getCurrentSchedule()
        
        if (currentSchedule == null) {
            AppLogger.e("ImportSchedule", "✗ 没有课表，需要设置")
            _importState.value = ImportState.NeedScheduleSetup
            return
        }
        
        AppLogger.d("ImportSchedule", "✓ 找到当前课表: ${currentSchedule.name} (ID: ${currentSchedule.id})")
        
        _importProgress.value = ImportProgress(step = ImportStep.PARSING)
        
        val maxSection = _parsedCourses.value.maxOfOrNull { it.startSection + it.sectionCount - 1 } ?: 8
        val morningSections = overrideMorningSections
            ?: classTimeDataStore.data.first()[MORNING_SECTIONS_KEY]
            ?: DataStoreManager.ClassTimeKeys.DEFAULT_MORNING_SECTIONS
        val afternoonSections = overrideAfternoonSections
            ?: classTimeDataStore.data.first()[AFTERNOON_SECTIONS_KEY]
            ?: DataStoreManager.ClassTimeKeys.DEFAULT_AFTERNOON_SECTIONS
        val currentTotalSections = morningSections + afternoonSections
        
        AppLogger.d("ImportSchedule", "========== 节次数检查 ==========")
        AppLogger.d("ImportSchedule", "课程总数: ${_parsedCourses.value.size}")
        
        _parsedCourses.value.take(5).forEach { course ->
            val endSection = course.startSection + course.sectionCount - 1
            AppLogger.d("ImportSchedule", "  ${course.courseName}: 第${course.startSection}-${endSection}节 (周${course.dayOfWeek})")
        }
        if (_parsedCourses.value.size > 5) {
            AppLogger.d("ImportSchedule", "  ... 还有 ${_parsedCourses.value.size - 5} 门课程")
        }
        
        AppLogger.d("ImportSchedule", "检测到最大节次: $maxSection")
        AppLogger.d("ImportSchedule", "当前设置总节次: $currentTotalSections (上午$morningSections + 下午$afternoonSections)")
        
        if (maxSection != currentTotalSections) {
            AppLogger.w("ImportSchedule", "✗ 节次数不匹配！需要用户重新设置")
            AppLogger.w("ImportSchedule", "  系统将弹出对话框，让用户设置为 $maxSection 节课")
            _importState.value = ImportState.NeedSectionSetup(maxSection)
            return
        }
        
        AppLogger.d("ImportSchedule", "✓ 节次数匹配，继续导入流程")
        AppLogger.d("ImportSchedule", "===============================")
        
        _importProgress.value = ImportProgress(step = ImportStep.IMPORTING)
        
        val existingSchedules = scheduleRepository.getAllSchedulesList()
        val scheduleCount = existingSchedules.size
        
        val scheduleName = if (scheduleCount == 0) {
            "我的课表"
        } else {
            "我的课表(${scheduleCount + 1})"
        }
        
        AppLogger.d("ImportSchedule", "生成课表名称: $scheduleName")
        
        val newSchedule = Schedule(
            name = scheduleName,
            schoolName = currentSchoolId,
            startDate = currentSchedule.startDate,
            endDate = currentSchedule.endDate,
            totalWeeks = currentSchedule.totalWeeks,
            isCurrent = false
        )
        
        AppLogger.d("ImportSchedule", "课表关联学校: $currentSchoolId")
        
        val scheduleId = scheduleRepository.insertSchedule(newSchedule)
        AppLogger.d("ImportSchedule", "✓ 创建课表成功，ID: $scheduleId")
        
        if (currentSchoolId.isNotEmpty()) {
            try {
                AppLogger.d("ImportSchedule", "========== 开始保存Cookie ==========")
                val cookieManager = CookieManager.getInstance()
                val school = schoolRepository.getSchoolById(currentSchoolId)
                
                if (school != null) {
                    AppLogger.d("ImportSchedule", "学校信息:")
                    AppLogger.d("ImportSchedule", "  学校ID: $currentSchoolId")
                    AppLogger.d("ImportSchedule", "  学校名称: ${school.name}")
                    AppLogger.d("ImportSchedule", "  登录URL: ${school.loginUrl}")
                    AppLogger.d("ImportSchedule", "  课表URL: ${school.scheduleUrl}")
                    
                    var cookies = cookieManager.getCookie(school.loginUrl)
                    AppLogger.d("ImportSchedule", "从登录URL获取Cookie: ${if (cookies.isNullOrEmpty()) "无" else "有(${cookies.length}字符)"}")
                    
                    if (cookies.isNullOrEmpty()) {
                        cookies = cookieManager.getCookie(school.scheduleUrl)
                        AppLogger.d("ImportSchedule", "从课表URL获取Cookie: ${if (cookies.isNullOrEmpty()) "无" else "有(${cookies.length}字符)"}")
                    }
                    
                    if (!cookies.isNullOrEmpty()) {
                        secureCookieManager.saveCookies(currentSchoolId, cookies)
                        AppLogger.d("ImportSchedule", "✓ 已保存Cookie到加密存储")
                        AppLogger.d("ImportSchedule", "  保存的Key: $currentSchoolId")
                        AppLogger.d("ImportSchedule", "  Cookie长度: ${cookies.length}")
                        
                        val savedCookies = secureCookieManager.getCookies(currentSchoolId)
                        if (savedCookies != null) {
                            AppLogger.d("ImportSchedule", "✓ 验证成功：Cookie已正确保存")
                        } else {
                            AppLogger.e("ImportSchedule", "✗ 验证失败：无法读取刚保存的Cookie")
                        }
                    } else {
                        AppLogger.w("ImportSchedule", "⚠️ 未找到Cookie（可能未登录或已过期）")
                        AppLogger.w("ImportSchedule", "  登录URL: ${school.loginUrl}")
                        AppLogger.w("ImportSchedule", "  课表URL: ${school.scheduleUrl}")
                    }
                } else {
                    AppLogger.e("ImportSchedule", "✗ 未找到学校信息: $currentSchoolId")
                }
                AppLogger.d("ImportSchedule", "========================================")
            } catch (e: Exception) {
                AppLogger.e("ImportSchedule", "✗ 保存Cookie失败", e)
            }
        }
        
        scheduleRepository.setCurrentSchedule(scheduleId)
        AppLogger.d("ImportSchedule", "✓ 已设置为当前课表")
        
        val conversionResult = importUseCase.convertParsedCoursesToCourses(
            _parsedCourses.value, scheduleId
        )
        val courses = conversionResult.courses
        
        AppLogger.d("ImportSchedule", "准备插入 ${courses.size} 门课程")
        courses.forEachIndexed { index, course ->
            AppLogger.d("ImportSchedule", "  课程 ${index + 1}: ${course.courseName}")
            AppLogger.d("ImportSchedule", "    - 教师: '${course.teacher}', 教室: '${course.classroom}'")
            AppLogger.d("ImportSchedule", "    - 周${course.dayOfWeek} 第${course.startSection}节(共${course.sectionCount}节) - 学分${course.credit}")
            AppLogger.d("ImportSchedule", "    - 周次: ${course.weekExpression} -> ${course.weeks.take(5).joinToString(",")}${if(course.weeks.size > 5) "..." else ""}")
        }
        
        val courseIds = courseRepository.insertCourses(courses)
        AppLogger.d("ImportSchedule", "✓ 插入课程成功，数量: ${courseIds.size}")
        
        try {
            val settingsDataStore = com.wind.ggbond.classtime.data.datastore.DataStoreManager.getSettingsDataStore(application)
            val reminderEnabled = settingsDataStore.data.first()[com.wind.ggbond.classtime.data.datastore.DataStoreManager.SettingsKeys.REMINDER_ENABLED_KEY] ?: false
            
            if (reminderEnabled) {
                val defaultMinutes = settingsDataStore.data.first()[com.wind.ggbond.classtime.data.datastore.DataStoreManager.SettingsKeys.DEFAULT_REMINDER_MINUTES_KEY] ?: 10
                
                val updatedCount = courseRepository.enableAllCoursesReminder(scheduleId, defaultMinutes)
                
                AppLogger.d("ImportSchedule", "✅ 已为 $updatedCount 门课程开启提醒")
                
                reminderScheduler.scheduleAllCourseReminders(scheduleId)
                
                AppLogger.d("ImportSchedule", "✅ 已创建所有课程的通知任务")
            } else {
                AppLogger.d("ImportSchedule", "全局提醒未开启，跳过提醒设置")
            }
        } catch (e: Exception) {
            AppLogger.e("ImportSchedule", "开启新导入课程的提醒失败", e)
        }
        
        _importProgress.value = ImportProgress(step = ImportStep.COMPLETED)
        AppLogger.d("ImportSchedule", "=== 导入完成 ===")
        
        _importState.value = ImportState.Success
    }
    
    /**
     * 更新节次数设置并重新尝试导入
     */
    fun updateSectionCountsAndRetry(morningSections: Int, afternoonSections: Int) {
        viewModelScope.launch {
            try {
                AppLogger.d("ImportSchedule", "更新节次数: 上午${morningSections}节, 下午${afternoonSections}节")
                
                classTimeDataStore.edit { preferences ->
                    preferences[MORNING_SECTIONS_KEY] = morningSections
                    preferences[AFTERNOON_SECTIONS_KEY] = afternoonSections
                }
                
                confirmImport(overrideMorningSections = morningSections, overrideAfternoonSections = afternoonSections)
            } catch (e: Exception) {
                AppLogger.e("ImportSchedule", "更新节次数失败", e)
                _importState.value = ImportState.Error("更新节次数失败：${e.message}")
            }
        }
    }
    
    /**
     * 创建学期并导入
     */
    fun createSemesterAndImport(semesterName: String, startDate: java.time.LocalDate, totalWeeks: Int) {
        viewModelScope.launch {
            try {
                _importState.value = ImportState.Loading
                
                AppLogger.d("ImportSchedule", "准备创建课表: $semesterName, 开始: $startDate, 周数: $totalWeeks")
                AppLogger.d("ImportSchedule", "当前待导入课程数量: ${_parsedCourses.value.size}")
                
                // 创建新课表（包含学期时间信息）并设置为当前
                val endDate = startDate.plusWeeks(totalWeeks.toLong())
                val schedule = Schedule(
                    name = semesterName,
                    startDate = startDate,
                    endDate = endDate,
                    totalWeeks = totalWeeks,
                    isCurrent = true
                )
                val scheduleId = scheduleRepository.insertSchedule(schedule)
                scheduleRepository.setCurrentSchedule(scheduleId)
                AppLogger.d("ImportSchedule", "✓ 创建课表成功，ID: $scheduleId, 已设置为当前课表")
                
                // 重新调用导入逻辑
                confirmImport()
            } catch (e: Exception) {
                AppLogger.e("ImportSchedule", "✗ 创建课表失败", e)
                _importState.value = ImportState.Error("创建课表失败: ${e.message}")
            }
        }
    }
    
    fun cancelImport() {
        _parsedCourses.value = emptyList()
        _importState.value = ImportState.Idle
    }
    
    fun resetState() {
        _parsedCourses.value = emptyList()
        _importState.value = ImportState.Idle
        _importProgress.value = ImportProgress()
        lastFailedAction = null
    }

    fun retryLastAction() {
        val action = lastFailedAction
        if (action != null) {
            viewModelScope.launch {
                _importState.value = ImportState.Loading
                try {
                    action()
                } catch (e: Exception) {
                    handleError(e)
                }
            }
        } else {
            resetState()
        }
    }

    fun onCookieExpiredHandled() {
        _cookieExpiredEvent.value = null
    }

    private fun classifyError(exception: Exception): Pair<ImportErrorType, ImportErrorRetryAction> {
        if (exception is CookieExpiredException) {
            return ImportErrorType.COOKIE_EXPIRED to ImportErrorRetryAction.RELOGIN
        }

        val message = exception.message ?: ""
        return when {
            message.contains("timeout", ignoreCase = true) ||
            message.contains("connection", ignoreCase = true) ||
            message.contains("network", ignoreCase = true) ||
            message.contains("SocketException", ignoreCase = true) ||
            message.contains("UnknownHostException", ignoreCase = true) ||
            message.contains("ConnectException", ignoreCase = true) ->
                ImportErrorType.NETWORK to ImportErrorRetryAction.RETRY

            message.contains("parse", ignoreCase = true) ||
            message.contains("json", ignoreCase = true) ||
            message.contains("format", ignoreCase = true) ||
            message.contains("解析", ignoreCase = true) ->
                ImportErrorType.PARSE to ImportErrorRetryAction.SWITCH_METHOD

            message.contains("cookie", ignoreCase = true) ||
            message.contains("expired", ignoreCase = true) ||
            message.contains("登录", ignoreCase = true) ||
            message.contains("unauthorized", ignoreCase = true) ||
            message.contains("401", ignoreCase = true) ->
                ImportErrorType.COOKIE_EXPIRED to ImportErrorRetryAction.RELOGIN

            else -> ImportErrorType.UNKNOWN to ImportErrorRetryAction.RESET
        }
    }

    private fun handleError(exception: Exception) {
        val (errorType, retryAction) = classifyError(exception)
        val message = exception.message ?: "导入失败"
        
        if (errorType == ImportErrorType.COOKIE_EXPIRED && currentSchoolId.isNotEmpty()) {
            _cookieExpiredEvent.value = currentSchoolId
        }
        
        _importState.value = ImportState.Error(
            message = message,
            errorType = errorType,
            retryAction = retryAction
        )
    }
    
    fun selectFile() {
        _filePickerTrigger.value = true
    }

    private val _filePickerTrigger = MutableStateFlow(false)
    val filePickerTrigger: StateFlow<Boolean> = _filePickerTrigger.asStateFlow()

    fun resetFilePickerTrigger() {
        _filePickerTrigger.value = false
    }

    fun handleFile(uri: Uri) {
        val fileName = getFileName(uri)
        val extension = fileName?.substringAfterLast('.', "")?.lowercase() ?: ""

        when (extension) {
            "xlsx", "xls" -> handleExcelFile(uri)
            "csv" -> handleCsvFile(uri)
            "html", "htm" -> handleHtmlFile(uri)
            else -> handleJsonFile(uri)
        }
    }

    private fun getFileName(uri: Uri): String? {
        var fileName: String? = null
        try {
            application.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) { AppLogger.e("Safety", "操作异常", e) }
        return fileName ?: uri.lastPathSegment
    }
    
    /**
     * 处理 JSON 文件
     */
    fun handleJsonFile(uri: Uri) {
        viewModelScope.launch {
            try {
                _importState.value = ImportState.Loading
                
                // 读取文件内容
                val jsonContent = withContext(Dispatchers.IO) {
                    application.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().use { it.readText() }
                    } ?: ""
                }
                
                if (jsonContent.isEmpty()) {
                    _importState.value = ImportState.Error("文件为空或无法读取")
                    return@launch
                }
                
                // 解析 JSON
                val courses = parseJsonCourses(jsonContent)
                
                if (courses.isEmpty()) {
                    _importState.value = ImportState.Error("未能从 JSON 中解析出课程信息\n\n可能原因：\n1. JSON 格式不正确\n2. 缺少必要字段\n3. 课程数据为空")
                } else {
                    _parsedCourses.value = courses
                    _importState.value = ImportState.Preview
                    AppLogger.d("ImportSchedule", "✅ 成功解析 ${courses.size} 门课程")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _importState.value = ImportState.Error("解析 JSON 失败：${e.message ?: "未知错误"}")
            }
        }
    }
    
    /**
     * 解析 JSON 课表文件
     * 
     * 支持的 JSON 格式：
     * 1. 简化格式（ParsedCourse数组）：
     * ```json
     * [
     *   {
     *     "courseName": "高等数学",
     *     "teacher": "张老师",
     *     "classroom": "教学楼A101",
     *     "dayOfWeek": 1,
     *     "startSection": 1,
     *     "sectionCount": 2,
     *     "weekExpression": "1-16周",
     *     "weeks": [1,2,3...16]
     *   }
     * ]
     * ```
     * 
     * 2. 完整格式（导出格式）：
     * ```json
     * {
     *   "exportTime": "2026-02-28T03:30:00.000",
     *   "version": "1.0",
     *   "semester": {...},
     *   "totalCourses": 3,
     *   "courses": [...]
     * }
     * ```
     */
    private fun parseJsonCourses(jsonContent: String): List<ParsedCourse> {
        val result = importUseCase.parseJsonToParsedCourses(jsonContent)
        if (result.errors.isNotEmpty()) {
            AppLogger.e("ImportSchedule", "JSON解析错误: ${result.errors.joinToString()}")
        }
        return result.courses
    }
    
    private val _htmlFilePickerTrigger = MutableStateFlow<Boolean>(false)
    val htmlFilePickerTrigger: StateFlow<Boolean> = _htmlFilePickerTrigger.asStateFlow()
    
    /**
     * 选择HTML文件
     */
    fun selectHtmlFile() {
        _htmlFilePickerTrigger.value = true
    }
    
    /**
     * 重置文件选择器触发状态
     */
    fun resetHtmlFilePickerTrigger() {
        _htmlFilePickerTrigger.value = false
    }
    
    /**
     * 处理选择的HTML文件
     */
    fun handleHtmlFile(uri: Uri) {
        viewModelScope.launch {
            try {
                _importState.value = ImportState.Loading
                
                // 读取文件内容
                val htmlContent = withContext(Dispatchers.IO) {
                    application.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().use { it.readText() }
                    } ?: ""
                }
                
                if (htmlContent.isEmpty()) {
                    _importState.value = ImportState.Error("文件为空或无法读取")
                    return@launch
                }
                
                // 自动检测HTML类型并解析
                val courses = htmlParser.parseHtmlAuto(htmlContent)
                
                if (courses.isEmpty()) {
                    _importState.value = ImportState.Error("未能从HTML中解析出课程信息\n\n可能原因：\n1. 文件格式不正确\n2. 不支持该教务系统\n3. HTML结构已变更")
                } else {
                    _parsedCourses.value = courses
                    _importState.value = ImportState.Preview
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _importState.value = ImportState.Error("解析失败：${e.message ?: "未知错误"}")
            }
        }
    }
    
    /**
     * 解析HTML内容（备用方法，用于粘贴）
     */
    fun parseHtmlContent(htmlContent: String) {
        viewModelScope.launch {
            try {
                _importState.value = ImportState.Loading
                
                // 自动检测HTML类型并解析
                val courses = htmlParser.parseHtmlAuto(htmlContent)
                
                if (courses.isEmpty()) {
                    _importState.value = ImportState.Error("未能从HTML中解析出课程信息，请检查HTML格式是否正确")
                } else {
                    _parsedCourses.value = courses
                    _importState.value = ImportState.Preview
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _importState.value = ImportState.Error("解析失败：${e.message ?: "未知错误"}")
            }
        }
    }
    
    /**
     * ✨ 使用统一服务导入课表（新方法）
     * 
     * @param schoolId 学校ID
     * @param username 用户名
     * @param password 密码
     * @param shouldSave 是否保存账号密码
     */

    // ===================== Excel / CSV 导入支持 =====================

    private val _excelRecognitionState = MutableStateFlow<ExcelRecognitionState>(ExcelRecognitionState.Idle)
    val excelRecognitionState: StateFlow<ExcelRecognitionState> = _excelRecognitionState.asStateFlow()

    private var currentExcelUri: Uri? = null
    private val _isCsvFile = MutableStateFlow(false)
    val isCsvFile: StateFlow<Boolean> = _isCsvFile.asStateFlow()

    fun handleCsvFile(uri: Uri) {
        currentExcelUri = uri
        _isCsvFile.value = true
        viewModelScope.launch {
            try {
                _importState.value = ImportState.Loading
                _excelRecognitionState.value = ExcelRecognitionState.Analyzing

                val result = withContext(Dispatchers.IO) {
                    importRouter.routeCsvImport(application, uri)
                }

                when (result.decision.confidence) {
                    ImportConfidence.HIGH -> {
                        if (result.courses.isNotEmpty()) {
                            _parsedCourses.value = result.courses
                            _excelRecognitionState.value = ExcelRecognitionState.Recognized(
                                templateName = result.decision.templateName,
                                fieldMapping = result.decision.fieldMapping,
                                confidence = result.decision.confidence,
                                courseCount = result.courses.size
                            )
                            _importState.value = ImportState.Preview
                            AppLogger.d("ImportSchedule", "✅ CSV智能识别成功: ${result.courses.size}门课程, 模板: ${result.decision.templateName}")
                        } else {
                            _excelRecognitionState.value = ExcelRecognitionState.NeedManualSetup(
                                preview = result.preview,
                                sheetNames = result.sheetNames,
                                decision = result.decision
                            )
                            _importState.value = ImportState.Idle
                        }
                    }
                    ImportConfidence.MEDIUM -> {
                        _excelRecognitionState.value = ExcelRecognitionState.NeedConfirmation(
                            preview = result.preview,
                            sheetNames = result.sheetNames,
                            decision = result.decision,
                            courses = result.courses
                        )
                        _importState.value = ImportState.Idle
                    }
                    ImportConfidence.LOW -> {
                        _excelRecognitionState.value = ExcelRecognitionState.NeedManualSetup(
                            preview = result.preview,
                            sheetNames = result.sheetNames,
                            decision = result.decision
                        )
                        _importState.value = ImportState.Idle
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("ImportSchedule", "CSV导入失败", e)
                _excelRecognitionState.value = ExcelRecognitionState.Idle
                _importState.value = ImportState.Error("CSV导入失败：${e.message}")
            }
        }
    }

    fun handleExcelFile(uri: Uri) {
        currentExcelUri = uri
        _isCsvFile.value = false
        viewModelScope.launch {
            try {
                _importState.value = ImportState.Loading
                _excelRecognitionState.value = ExcelRecognitionState.Analyzing

                val result = withContext(Dispatchers.IO) {
                    importRouter.routeExcelImport(application, uri)
                }

                when (result.decision.confidence) {
                    ImportConfidence.HIGH -> {
                        if (result.courses.isNotEmpty()) {
                            _parsedCourses.value = result.courses
                            _excelRecognitionState.value = ExcelRecognitionState.Recognized(
                                templateName = result.decision.templateName,
                                fieldMapping = result.decision.fieldMapping,
                                confidence = result.decision.confidence,
                                courseCount = result.courses.size
                            )
                            _importState.value = ImportState.Preview
                            AppLogger.d("ImportSchedule", "✅ Excel智能识别成功: ${result.courses.size}门课程, 模板: ${result.decision.templateName}")
                        } else {
                            _excelRecognitionState.value = ExcelRecognitionState.NeedManualSetup(
                                preview = result.preview,
                                sheetNames = result.sheetNames,
                                decision = result.decision
                            )
                            _importState.value = ImportState.Idle
                        }
                    }
                    ImportConfidence.MEDIUM -> {
                        _excelRecognitionState.value = ExcelRecognitionState.NeedConfirmation(
                            preview = result.preview,
                            sheetNames = result.sheetNames,
                            decision = result.decision,
                            courses = result.courses
                        )
                        _importState.value = ImportState.Idle
                    }
                    ImportConfidence.LOW -> {
                        _excelRecognitionState.value = ExcelRecognitionState.NeedManualSetup(
                            preview = result.preview,
                            sheetNames = result.sheetNames,
                            decision = result.decision
                        )
                        _importState.value = ImportState.Idle
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("ImportSchedule", "Excel导入失败", e)
                _excelRecognitionState.value = ExcelRecognitionState.Idle
                _importState.value = ImportState.Error("Excel导入失败：${e.message}")
            }
        }
    }

    fun confirmExcelRecognition() {
        val state = _excelRecognitionState.value
        if (state is ExcelRecognitionState.NeedConfirmation) {
            if (state.courses.isNotEmpty()) {
                _parsedCourses.value = state.courses
                _excelRecognitionState.value = ExcelRecognitionState.Idle
                _importState.value = ImportState.Preview
            }
        }
    }

    fun applyExcelTemplate(templateName: String) {
        val uri = currentExcelUri ?: return
        viewModelScope.launch {
            try {
                _importState.value = ImportState.Loading
                val result = withContext(Dispatchers.IO) {
                    val extension = getFileName(uri)?.substringAfterLast('.', "")?.lowercase() ?: ""
                    if (extension == "csv") {
                        importRouter.routeCsvImport(application, uri, forceTemplate = templateName)
                    } else {
                        importRouter.routeExcelImport(application, uri, forceTemplate = templateName)
                    }
                }
                if (result.courses.isNotEmpty()) {
                    _parsedCourses.value = result.courses
                    _excelRecognitionState.value = ExcelRecognitionState.Recognized(
                        templateName = templateName,
                        fieldMapping = result.decision.fieldMapping,
                        confidence = result.decision.confidence,
                        courseCount = result.courses.size
                    )
                    _importState.value = ImportState.Preview
                } else {
                    _importState.value = ImportState.Error("使用模板解析失败，请尝试其他模板")
                }
            } catch (e: Exception) {
                _importState.value = ImportState.Error("模板解析失败：${e.message}")
            }
        }
    }

    fun applyExcelManualMapping(fieldMapping: Map<String, Int>, headerRow: Int) {
        val uri = currentExcelUri ?: return
        viewModelScope.launch {
            try {
                _importState.value = ImportState.Loading
                val result = withContext(Dispatchers.IO) {
                    val extension = getFileName(uri)?.substringAfterLast('.', "")?.lowercase() ?: ""
                    if (extension == "csv") {
                        importRouter.routeCsvImport(
                            application, uri,
                            forceFieldMapping = fieldMapping,
                            forceHeaderRow = headerRow
                        )
                    } else {
                        importRouter.routeExcelImport(
                            application, uri,
                            forceFieldMapping = fieldMapping,
                            forceHeaderRow = headerRow
                        )
                    }
                }
                if (result.courses.isNotEmpty()) {
                    _parsedCourses.value = result.courses
                    _excelRecognitionState.value = ExcelRecognitionState.Idle
                    _importState.value = ImportState.Preview
                } else {
                    _importState.value = ImportState.Error("手动映射解析失败，请检查字段对应关系")
                }
            } catch (e: Exception) {
                _importState.value = ImportState.Error("手动映射解析失败：${e.message}")
            }
        }
    }

    fun resetExcelRecognition() {
        _excelRecognitionState.value = ExcelRecognitionState.Idle
        currentExcelUri = null
        _isCsvFile.value = false
        _importState.value = ImportState.Idle
    }

    private fun checkIncompleteCourses(courses: List<ParsedCourse>): List<IncompleteCourseInfo> {
        val result = mutableListOf<IncompleteCourseInfo>()
        courses.forEachIndexed { index, course ->
            val missingFields = mutableListOf<String>()
            if (course.weeks.isEmpty() && course.weekExpression.isBlank()) {
                missingFields.add("weeks")
            }
            if (course.dayOfWeek !in 1..7) {
                missingFields.add("dayOfWeek")
            }
            if (course.startSection <= 0) {
                missingFields.add("startSection")
            }
            if (course.sectionCount <= 0) {
                missingFields.add("sectionCount")
            }
            if (missingFields.isNotEmpty()) {
                result.add(IncompleteCourseInfo(index, course, missingFields))
            }
        }
        return result
    }

    fun applyManualFilledCourses(updatedIncompleteCourses: List<IncompleteCourseInfo>) {
        viewModelScope.launch {
            try {
                val originalCourses = _parsedCourses.value.toMutableList()
                updatedIncompleteCourses.forEach { updatedInfo ->
                    if (updatedInfo.index in originalCourses.indices) {
                        val course = updatedInfo.course
                        val weeks = if (course.weeks.isNotEmpty()) {
                            course.weeks
                        } else if (course.weekExpression.isNotBlank()) {
                            WeekParser.parseWeekExpression(course.weekExpression)
                        } else {
                            emptyList()
                        }
                        originalCourses[updatedInfo.index] = course.copy(weeks = weeks)
                    }
                }
                _parsedCourses.value = originalCourses.toList()
                val stillIncomplete = checkIncompleteCourses(originalCourses)
                if (stillIncomplete.isNotEmpty()) {
                    _importState.value = ImportState.NeedManualFill(stillIncomplete)
                } else {
                    _importState.value = ImportState.Preview
                }
            } catch (e: Exception) {
                AppLogger.e("ImportSchedule", "应用手动填写数据失败", e)
                _importState.value = ImportState.Error("应用填写数据失败：${e.message}")
            }
        }
    }
}

sealed class ExcelRecognitionState {
    object Idle : ExcelRecognitionState()
    object Analyzing : ExcelRecognitionState()
    data class Recognized(
        val templateName: String?,
        val fieldMapping: Map<String, Int>,
        val confidence: ImportConfidence,
        val courseCount: Int
    ) : ExcelRecognitionState()
    data class NeedConfirmation(
        val preview: List<List<String>>,
        val sheetNames: List<String>,
        val decision: ImportRouteDecision,
        val courses: List<ParsedCourse>
    ) : ExcelRecognitionState()
    data class NeedManualSetup(
        val preview: List<List<String>>,
        val sheetNames: List<String>,
        val decision: ImportRouteDecision
    ) : ExcelRecognitionState()
}

enum class ImportStep(val label: String) {
    CONNECTING("连接中"),
    FETCHING_DATA("获取数据"),
    PARSING("解析中"),
    IMPORTING("导入中"),
    COMPLETED("完成")
}

data class ImportProgress(
    val step: ImportStep = ImportStep.CONNECTING,
    val isFailed: Boolean = false
)

