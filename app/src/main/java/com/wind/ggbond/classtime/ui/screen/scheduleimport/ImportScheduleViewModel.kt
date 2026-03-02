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
import com.wind.ggbond.classtime.util.CourseColorPalette
import com.wind.ggbond.classtime.util.HtmlScheduleParser
import com.wind.ggbond.classtime.util.SecureCookieManager
import com.wind.ggbond.classtime.util.WeekParser
import com.wind.ggbond.classtime.service.AlarmReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 导入课表 ViewModel
 */
@HiltViewModel
class ImportScheduleViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val scheduleRepository: ScheduleRepository,
    private val schoolRepository: SchoolRepository,
    private val secureCookieManager: SecureCookieManager,
    private val htmlParser: HtmlScheduleParser,
    private val unifiedScheduleFetchService: com.wind.ggbond.classtime.service.UnifiedScheduleFetchService,
    private val reminderScheduler: AlarmReminderScheduler,
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
    
    // 保存学校ID，用于创建课表时关联学校
    private var currentSchoolId: String = ""
    
    fun previewCourses(courses: List<ParsedCourse>, schoolId: String = "") {
        _parsedCourses.value = courses
        currentSchoolId = schoolId
        _importState.value = ImportState.Preview
    }
    
    fun confirmImport() {
        viewModelScope.launch {
            try {
                _importState.value = ImportState.Loading
                
                android.util.Log.d("ImportSchedule", "=== 开始导入流程 ===")
                android.util.Log.d("ImportSchedule", "待导入课程数量: ${_parsedCourses.value.size}")
                
                // 检查是否有课程数据
                if (_parsedCourses.value.isEmpty()) {
                    android.util.Log.e("ImportSchedule", "✗ 没有课程数据可导入")
                    _importState.value = ImportState.Error("没有课程数据可导入")
                    return@launch
                }
                
                // 检查是否有当前课表（包含学期时间信息）
                val currentSchedule = scheduleRepository.getCurrentSchedule()
                
                if (currentSchedule == null) {
                    android.util.Log.e("ImportSchedule", "✗ 没有课表，需要设置")
                    _importState.value = ImportState.NeedScheduleSetup
                    return@launch
                }
                
                android.util.Log.d("ImportSchedule", "✓ 找到当前课表: ${currentSchedule.name} (ID: ${currentSchedule.id})")
                
                // ⭐ 检查课程节次数是否与当前设置匹配
                val maxSection = _parsedCourses.value.maxOfOrNull { it.startSection + it.sectionCount - 1 } ?: 8
                val preferences = classTimeDataStore.data.first()
                val morningSections = preferences[MORNING_SECTIONS_KEY] ?: DataStoreManager.ClassTimeKeys.DEFAULT_MORNING_SECTIONS
                val afternoonSections = preferences[AFTERNOON_SECTIONS_KEY] ?: DataStoreManager.ClassTimeKeys.DEFAULT_AFTERNOON_SECTIONS
                val currentTotalSections = morningSections + afternoonSections
                
                android.util.Log.d("ImportSchedule", "========== 节次数检查 ==========")
                android.util.Log.d("ImportSchedule", "课程总数: ${_parsedCourses.value.size}")
                
                // 输出每门课程的节次信息
                _parsedCourses.value.take(5).forEach { course ->
                    val endSection = course.startSection + course.sectionCount - 1
                    android.util.Log.d("ImportSchedule", "  ${course.courseName}: 第${course.startSection}-${endSection}节 (周${course.dayOfWeek})")
                }
                if (_parsedCourses.value.size > 5) {
                    android.util.Log.d("ImportSchedule", "  ... 还有 ${_parsedCourses.value.size - 5} 门课程")
                }
                
                android.util.Log.d("ImportSchedule", "检测到最大节次: $maxSection")
                android.util.Log.d("ImportSchedule", "当前设置总节次: $currentTotalSections (上午$morningSections + 下午$afternoonSections)")
                
                // 如果节次数不匹配，提示用户设置
                if (maxSection != currentTotalSections) {
                    android.util.Log.w("ImportSchedule", "✗ 节次数不匹配！需要用户重新设置")
                    android.util.Log.w("ImportSchedule", "  系统将弹出对话框，让用户设置为 $maxSection 节课")
                    _importState.value = ImportState.NeedSectionSetup(maxSection)
                    return@launch
                }
                
                android.util.Log.d("ImportSchedule", "✓ 节次数匹配，继续导入流程")
                android.util.Log.d("ImportSchedule", "===============================")
                
                // 查询已存在的课表数量，用于生成新课表名称
                val existingSchedules = scheduleRepository.getAllSchedulesList()
                val scheduleCount = existingSchedules.size
                
                // 生成新课表名称：我的课表、我的课表(2)、我的课表(3)...
                val scheduleName = if (scheduleCount == 0) {
                    "我的课表"
                } else {
                    "我的课表(${scheduleCount + 1})"
                }
                
                android.util.Log.d("ImportSchedule", "生成课表名称: $scheduleName")
                
                // 创建新课表
                val newSchedule = Schedule(
                    name = scheduleName,
                    schoolName = currentSchoolId,  // 保存学校ID
                    startDate = currentSchedule.startDate,
                    endDate = currentSchedule.endDate,
                    totalWeeks = currentSchedule.totalWeeks,
                    isCurrent = false // 先设置为非当前，插入后再统一设置
                )
                
                android.util.Log.d("ImportSchedule", "课表关联学校: $currentSchoolId")
                
                val scheduleId = scheduleRepository.insertSchedule(newSchedule)
                android.util.Log.d("ImportSchedule", "✓ 创建课表成功，ID: $scheduleId")
                
                // ✅ 保存Cookie到SecureCookieManager（如果有的话）
                if (currentSchoolId.isNotEmpty()) {
                    try {
                        android.util.Log.d("ImportSchedule", "========== 开始保存Cookie ==========")
                        val cookieManager = CookieManager.getInstance()
                        val school = schoolRepository.getSchoolById(currentSchoolId)
                        
                        if (school != null) {
                            android.util.Log.d("ImportSchedule", "学校信息:")
                            android.util.Log.d("ImportSchedule", "  学校ID: $currentSchoolId")
                            android.util.Log.d("ImportSchedule", "  学校名称: ${school.name}")
                            android.util.Log.d("ImportSchedule", "  登录URL: ${school.loginUrl}")
                            android.util.Log.d("ImportSchedule", "  课表URL: ${school.scheduleUrl}")
                            
                            // 尝试从多个URL获取cookie
                            var cookies = cookieManager.getCookie(school.loginUrl)
                            android.util.Log.d("ImportSchedule", "从登录URL获取Cookie: ${if (cookies.isNullOrEmpty()) "无" else "有(${cookies.length}字符)"}")
                            
                            if (cookies.isNullOrEmpty()) {
                                cookies = cookieManager.getCookie(school.scheduleUrl)
                                android.util.Log.d("ImportSchedule", "从课表URL获取Cookie: ${if (cookies.isNullOrEmpty()) "无" else "有(${cookies.length}字符)"}")
                            }
                            
                            if (!cookies.isNullOrEmpty()) {
                                // 保存cookie时使用学校ID作为key
                                secureCookieManager.saveCookies(currentSchoolId, cookies)
                                android.util.Log.d("ImportSchedule", "✓ 已保存Cookie到加密存储")
                                android.util.Log.d("ImportSchedule", "  保存的Key: $currentSchoolId")
                                android.util.Log.d("ImportSchedule", "  Cookie长度: ${cookies.length}")
                                android.util.Log.d("ImportSchedule", "  Cookie前50字符: ${cookies.take(50)}...")
                                
                                // 验证保存是否成功
                                val savedCookies = secureCookieManager.getCookies(currentSchoolId)
                                if (savedCookies != null) {
                                    android.util.Log.d("ImportSchedule", "✓ 验证成功：Cookie已正确保存")
                                } else {
                                    android.util.Log.e("ImportSchedule", "✗ 验证失败：无法读取刚保存的Cookie")
                                }
                            } else {
                                android.util.Log.w("ImportSchedule", "⚠️ 未找到Cookie（可能未登录或已过期）")
                                android.util.Log.w("ImportSchedule", "  登录URL: ${school.loginUrl}")
                                android.util.Log.w("ImportSchedule", "  课表URL: ${school.scheduleUrl}")
                            }
                        } else {
                            android.util.Log.e("ImportSchedule", "✗ 未找到学校信息: $currentSchoolId")
                        }
                        android.util.Log.d("ImportSchedule", "========================================")
                    } catch (e: Exception) {
                        android.util.Log.e("ImportSchedule", "✗ 保存Cookie失败", e)
                        // 不影响导入流程，继续执行
                    }
                }
                
                // 🔧 修复：确保只有这个课表是当前课表（先清除其他课表的当前状态）
                scheduleRepository.setCurrentSchedule(scheduleId)
                android.util.Log.d("ImportSchedule", "✓ 已设置为当前课表")
                
                // 转换为Course实体并插入
                // 🎨 智能分配颜色：为不同课程分配不同颜色
                val courseNames = _parsedCourses.value.map { it.courseName }.distinct()
                val colorMapping = CourseColorPalette.assignColorsForCourses(courseNames)
                
                val courses = _parsedCourses.value.map { parsed ->
                    val weeks = if (parsed.weeks.isNotEmpty()) {
                        parsed.weeks
                    } else {
                        WeekParser.parseWeekExpression(parsed.weekExpression)
                    }
                    
                    Course(
                        courseName = parsed.courseName,
                        teacher = parsed.teacher,
                        classroom = parsed.classroom,
                        dayOfWeek = parsed.dayOfWeek,
                        startSection = parsed.startSection,
                        sectionCount = parsed.sectionCount,
                        weeks = weeks,
                        weekExpression = if (parsed.weekExpression.isNotEmpty()) {
                            parsed.weekExpression
                        } else {
                            WeekParser.formatWeekList(weeks)
                        },
                        color = colorMapping[parsed.courseName] ?: CourseColorPalette.getColorByIndex(0),
                        scheduleId = scheduleId,
                        credit = parsed.credit,  // 学分
                        reminderEnabled = false  // ✅ 导入时默认关闭提醒，等待全局设置控制
                    )
                }
                
                android.util.Log.d("ImportSchedule", "准备插入 ${courses.size} 门课程")
                courses.forEachIndexed { index, course ->
                    android.util.Log.d("ImportSchedule", "  课程 ${index + 1}: ${course.courseName}")
                    android.util.Log.d("ImportSchedule", "    - 教师: '${course.teacher}', 教室: '${course.classroom}'")
                    android.util.Log.d("ImportSchedule", "    - 周${course.dayOfWeek} 第${course.startSection}节(共${course.sectionCount}节) - 学分${course.credit}")
                    android.util.Log.d("ImportSchedule", "    - 周次: ${course.weekExpression} -> ${course.weeks.take(5).joinToString(",")}${if(course.weeks.size > 5) "..." else ""}")
                }
                
                val courseIds = courseRepository.insertCourses(courses)
                android.util.Log.d("ImportSchedule", "✓ 插入课程成功，数量: ${courseIds.size}")
                
                // ✅ 检查全局提醒设置，如果开启了则批量为所有课程开启提醒并创建通知任务
                try {
                    val settingsDataStore = com.wind.ggbond.classtime.data.datastore.DataStoreManager.getSettingsDataStore(application)
                    val reminderEnabled = settingsDataStore.data.first()[com.wind.ggbond.classtime.data.datastore.DataStoreManager.SettingsKeys.REMINDER_ENABLED_KEY] ?: false
                    
                    if (reminderEnabled) {
                        val defaultMinutes = settingsDataStore.data.first()[com.wind.ggbond.classtime.data.datastore.DataStoreManager.SettingsKeys.DEFAULT_REMINDER_MINUTES_KEY] ?: 10
                        
                        // ✅ 使用统一的批量开启方法（确保与设置页面逻辑一致）
                        val updatedCount = courseRepository.enableAllCoursesReminder(scheduleId, defaultMinutes)
                        
                        android.util.Log.d("ImportSchedule", "✅ 已为 $updatedCount 门课程开启提醒")
                        
                        // ✅ 批量创建提醒任务
                        reminderScheduler.scheduleAllCourseReminders(scheduleId)
                        
                        android.util.Log.d("ImportSchedule", "✅ 已创建所有课程的通知任务")
                    } else {
                        android.util.Log.d("ImportSchedule", "全局提醒未开启，跳过提醒设置")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ImportSchedule", "开启新导入课程的提醒失败", e)
                }
                
                android.util.Log.d("ImportSchedule", "=== 导入完成 ===")
                
                _importState.value = ImportState.Success
            } catch (e: Exception) {
                android.util.Log.e("ImportSchedule", "✗ 导入失败", e)
                android.util.Log.e("ImportSchedule", "错误详情: ${e.message}")
                android.util.Log.e("ImportSchedule", "错误堆栈: ${e.stackTraceToString()}")
                _importState.value = ImportState.Error(e.message ?: "导入失败")
            }
        }
    }
    
    /**
     * 更新节次数设置并重新尝试导入
     */
    fun updateSectionCountsAndRetry(morningSections: Int, afternoonSections: Int) {
        viewModelScope.launch {
            try {
                android.util.Log.d("ImportSchedule", "更新节次数: 上午${morningSections}节, 下午${afternoonSections}节")
                
                // 更新DataStore中的设置
                classTimeDataStore.edit { preferences ->
                    preferences[MORNING_SECTIONS_KEY] = morningSections
                    preferences[AFTERNOON_SECTIONS_KEY] = afternoonSections
                }
                
                // 重新生成课程时间表
                // 这个逻辑应该由ClassTimeConfigViewModel处理，这里只需更新设置即可
                // confirmImport会读取最新的设置
                
                // 重新尝试导入
                confirmImport()
            } catch (e: Exception) {
                android.util.Log.e("ImportSchedule", "更新节次数失败", e)
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
                
                android.util.Log.d("ImportSchedule", "准备创建课表: $semesterName, 开始: $startDate, 周数: $totalWeeks")
                android.util.Log.d("ImportSchedule", "当前待导入课程数量: ${_parsedCourses.value.size}")
                
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
                android.util.Log.d("ImportSchedule", "✓ 创建课表成功，ID: $scheduleId, 已设置为当前课表")
                
                // 重新调用导入逻辑
                confirmImport()
            } catch (e: Exception) {
                android.util.Log.e("ImportSchedule", "✗ 创建课表失败", e)
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
    }
    
    fun selectFile() {
        // 触发 JSON 文件选择器
        _jsonFilePickerTrigger.value = true
    }
    
    private val _jsonFilePickerTrigger = MutableStateFlow<Boolean>(false)
    val jsonFilePickerTrigger: StateFlow<Boolean> = _jsonFilePickerTrigger.asStateFlow()
    
    /**
     * 重置 JSON 文件选择器触发状态
     */
    fun resetJsonFilePickerTrigger() {
        _jsonFilePickerTrigger.value = false
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
                    android.util.Log.d("ImportSchedule", "✅ 成功解析 ${courses.size} 门课程")
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
        return try {
            val gson = com.google.gson.Gson()
            
            // 先尝试解析为完整导出格式
            val jsonObject = gson.fromJson(jsonContent, com.google.gson.JsonObject::class.java)
            
            val courses: List<ParsedCourse> = if (jsonObject.has("courses")) {
                // 完整格式：提取courses数组
                val coursesArray = jsonObject.getAsJsonArray("courses")
                val fullCourses: List<com.wind.ggbond.classtime.data.local.entity.Course> = 
                    gson.fromJson(coursesArray, object : com.google.gson.reflect.TypeToken<List<com.wind.ggbond.classtime.data.local.entity.Course>>() {}.type)
                
                // 转换为ParsedCourse
                fullCourses.map { course ->
                    ParsedCourse(
                        courseName = course.courseName,
                        teacher = course.teacher,
                        classroom = course.classroom,
                        dayOfWeek = course.dayOfWeek,
                        startSection = course.startSection,
                        sectionCount = course.sectionCount,
                        weekExpression = course.weekExpression,
                        weeks = course.weeks,
                        credit = course.credit
                    )
                }
            } else {
                // 尝试解析为简化格式
                try {
                    val type = object : com.google.gson.reflect.TypeToken<List<ParsedCourse>>() {}.type
                    gson.fromJson<List<ParsedCourse>>(jsonContent, type) ?: emptyList()
                } catch (e: Exception) {
                    // 如果都失败了，返回空列表
                    android.util.Log.e("ImportSchedule", "无法解析JSON为任何已知格式", e)
                    emptyList()
                }
            }
            
            // 验证并过滤无效课程
            courses.filter { course ->
                course.courseName.isNotBlank() &&
                course.dayOfWeek in 1..7 &&
                course.startSection > 0 &&
                course.sectionCount > 0 &&
                course.weeks.isNotEmpty()
            }.also { validCourses ->
                android.util.Log.d("ImportSchedule", "JSON 解析结果：总数 ${courses.size}，有效 ${validCourses.size}")
            }
        } catch (e: Exception) {
            android.util.Log.e("ImportSchedule", "JSON 解析失败", e)
            emptyList()
        }
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
}

