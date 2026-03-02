package com.wind.ggbond.classtime.ui.screen.scheduleimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.data.local.entity.SchoolEntity
import com.wind.ggbond.classtime.data.model.ImportedSemesterInfo
import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.SchoolRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.service.AlarmReminderScheduler
import com.wind.ggbond.classtime.util.Constants
import com.wind.ggbond.classtime.util.InputValidator
import com.wind.ggbond.classtime.util.extractor.SchoolExtractorFactory
import com.wind.ggbond.classtime.util.extractor.ElementInspector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 解析状态
 */
sealed class ParseState {
    object Idle : ParseState()
    object Parsing : ParseState()
    data class Success(val debugInfo: List<String> = emptyList()) : ParseState()
    data class Error(val message: String) : ParseState()
}

/**
 * JSON课程数据类（用于解析）
 */
data class JsonCourse(
    val courseName: String,
    val teacher: String,
    val classroom: String,
    val dayOfWeek: Int,
    val sectionRow: Int,
    val weekInfo: String
)

/**
 * 智能导入ViewModel（使用新的提取器架构）
 */
@HiltViewModel
class SmartImportViewModel @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val courseRepository: CourseRepository,
    private val scheduleRepository: ScheduleRepository,
    private val reminderScheduler: AlarmReminderScheduler,
    private val extractorFactory: SchoolExtractorFactory
) : ViewModel() {
    
    private val _parseState = MutableStateFlow<ParseState>(ParseState.Idle)
    val parseState: StateFlow<ParseState> = _parseState.asStateFlow()
    
    private val _parsedCourses = MutableStateFlow<List<ParsedCourse>>(emptyList())
    val parsedCourses: StateFlow<List<ParsedCourse>> = _parsedCourses.asStateFlow()
    
    private val _debugInfo = MutableStateFlow<List<String>>(emptyList())
    val debugInfo: StateFlow<List<String>> = _debugInfo.asStateFlow()
    
    // 导入时提取到的学期信息（开始日期、结束日期、总周数）
    private val _importedSemesterInfo = MutableStateFlow<ImportedSemesterInfo?>(null)
    val importedSemesterInfo: StateFlow<ImportedSemesterInfo?> = _importedSemesterInfo.asStateFlow()
    
    /**
     * 获取学校信息
     * ✅ 修复：添加异常处理，避免闪退
     */
    fun getSchool(schoolId: String): Flow<SchoolEntity?> {
        return flow {
            try {
                val school = schoolRepository.getSchoolById(schoolId)
                android.util.Log.d("SmartImportVM", "获取学校信息: schoolId=$schoolId, result=${school?.name}")
                emit(school)
            } catch (e: Exception) {
                android.util.Log.e("SmartImportVM", "获取学校信息失败: schoolId=$schoolId", e)
                emit(null)
            }
        }
    }
    
    /**
     * 获取页面元素检查脚本（用于开发和调试）
     */
    fun getInspectionScript(): String {
        return ElementInspector.generateInspectionScript()
    }
    
    /**
     * 根据学校ID获取课表提取脚本
     */
    fun getExtractionScript(schoolId: String): String? {
        val extractor = extractorFactory.getExtractor(schoolId)
        return extractor?.generateExtractionScript()
    }
    
    /**
     * 根据URL自动检测学校并获取提取脚本
     */
    fun getExtractionScriptByUrl(url: String): String? {
        val extractor = extractorFactory.detectExtractorByUrl(url)
        return extractor?.generateExtractionScript()
    }
    
    /**
     * 使用学校专用提取器解析课程（推荐使用）
     */
    fun parseScheduleWithExtractor(schoolId: String, jsonData: String) {
        viewModelScope.launch {
            _parseState.value = ParseState.Parsing
            
            try {
                val extractor = extractorFactory.getExtractor(schoolId)
                if (extractor == null) {
                    _parseState.value = ParseState.Error("不支持的学校: $schoolId")
                    return@launch
                }
                
                val courses = extractor.parseCourses(jsonData)
                
                if (courses.isEmpty()) {
                    _parseState.value = ParseState.Error("未找到课程信息\n请确保已登录并进入课表页面")
                } else {
                    _parsedCourses.value = courses
                    
                    // 尝试解析学期信息（如果提取器支持）
                    try {
                        val semesterInfo = extractor.parseSemesterInfo(jsonData)
                        if (semesterInfo != null) {
                            _importedSemesterInfo.value = semesterInfo
                            android.util.Log.d("SmartImportVM", "成功解析学期信息: 开始=${semesterInfo.startDate}, 总周数=${semesterInfo.totalWeeks}")
                        } else {
                            _importedSemesterInfo.value = null
                            android.util.Log.d("SmartImportVM", "未能解析学期信息，将由用户手动设置")
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("SmartImportVM", "解析学期信息失败: ${e.message}")
                        _importedSemesterInfo.value = null
                    }
                    
                    _parseState.value = ParseState.Success()
                }
            } catch (e: Exception) {
                android.util.Log.e("SmartImportVM", "使用提取器解析失败", e)
                _parseState.value = ParseState.Error("解析失败：${e.message}")
            }
        }
    }
    
    /**
     * 从JSON解析课表（JavaScript提取的数据）⭐
     * 
     * 这个方法保留用于兼容旧的通用格式，
     * 新的学校适配器应使用 parseScheduleWithExtractor
     */
    fun parseScheduleFromJson(jsonString: String) {
        viewModelScope.launch {
            _parseState.value = ParseState.Parsing
            
            try {
                // ✅ 第一步：验证JSON结构
                val validationResult = InputValidator.validateScheduleJson(jsonString)
                if (!validationResult.isValid) {
                    // 在验证失败时，也收集调试信息（原始JSON和错误信息）
                    val errorDebugInfo = listOf(
                        "【验证失败】",
                        "错误信息: ${validationResult.message}",
                        "",
                        "【原始JSON数据】",
                        jsonString.take(2000) + if (jsonString.length > 2000) "\n...(已截断，共${jsonString.length}字符)" else ""
                    )
                    _debugInfo.value = errorDebugInfo
                    _parseState.value = ParseState.Error("数据验证失败：${validationResult.message}")
                    return@launch
                }
                
                // 清理JSON字符串（移除外层引号和转义字符）
                val cleanJson = jsonString
                    .removeSurrounding("\"")
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                    .replace("\\\\", "\\")
                
                android.util.Log.d("SmartImportVM", "清理后的JSON: $cleanJson")
                
                // 使用Gson解析JSON
                val gson = com.google.gson.Gson()
                
                // 解析小爱课程表格式的数据
                try {
                    val debugType = object : com.google.gson.reflect.TypeToken<DebugResponse>() {}.type
                    val debugResponse: DebugResponse = gson.fromJson(cleanJson, debugType)
                    
                    // 保存调试信息 ⭐
                    _debugInfo.value = debugResponse.debug ?: emptyList()
                    
                    // 输出调试信息
                    debugResponse.debug?.forEach { debugMsg ->
                        android.util.Log.d("SmartImportVM", "JS调试: $debugMsg")
                    }
                    
                    // 检查是否有错误信息
                    if (debugResponse.error != null) {
                        val errorMsg = "提取失败：${debugResponse.error}\n\n调试信息:\n${debugResponse.debug?.joinToString("\n") ?: "无"}"
                        _parseState.value = ParseState.Error(errorMsg)
                        return@launch
                    }
                    
                    val aiCourses = debugResponse.courses ?: emptyList()
                    android.util.Log.d("SmartImportVM", "解析到 ${aiCourses.size} 门课程")
                    
                    if (aiCourses.isEmpty()) {
                        // 显示**所有**调试信息帮助用户 ⭐
                        val errorMsg = "未找到课程信息\n\n调试信息:\n${debugResponse.debug?.joinToString("\n") ?: "无调试信息"}"
                        _parseState.value = ParseState.Error(errorMsg)
                        return@launch
                    }
                    
                    // ✅ 第二步：转换前过滤和验证数据
                    android.util.Log.d("SmartImportVM", "===== 开始转换课程 =====")
                    val parsedCourses = aiCourses.mapIndexedNotNull { index, aiCourse ->
                        // 解析课程名称（兼容两种格式）
                        var courseName = aiCourse.resolveCourseName()
                        val originalCourseName = courseName  // 保存原始名称用于日志
                        
                        android.util.Log.d("SmartImportVM", "课程${index + 1} 原始名称: '$courseName'")
                        
                        // 如果课程名称包含过多信息，尝试解析
                        var teacher = aiCourse.teacher
                        var classroom = aiCourse.resolveClassroom()
                        var weekInfo = aiCourse.weekInfo
                        
                        android.util.Log.d("SmartImportVM", "  原始字段 - 教师: '$teacher', 教室: '$classroom', 周次: '$weekInfo'")
                        
                        // 🔧 强化清理：移除课程名称中的多余信息
                        courseName = courseName
                            .replace(Regex(",+$"), "")  // 移除尾部逗号
                            .replace(Regex("\\s+,"), "")  // 移除空格+逗号
                            .replace(Regex("\\(\\d+-\\d+节\\).*"), "")  // 移除(1-2节)及之后的所有内容
                            .replace(Regex("\\d+-\\d+周.*"), "")  // 移除周次信息
                            .replace(Regex("[★☆〇■◆].*"), "")  // 移除特殊符号及之后内容
                            .replace(Regex("\\d+$"), "")  // 移除尾部数字（如"体育3"中的3可选保留）
                            .trim()
                        
                        // 如果课程名称太长（超过20字符），可能包含了其他信息，尝试进一步提取
                        if (courseName.length > 20) {
                            // 尝试提取第一个有意义的词
                            val words = courseName.split(Regex("[\\s,;]+"))
                            if (words.isNotEmpty()) {
                                courseName = words[0]
                            }
                        }
                        
                        android.util.Log.d("SmartImportVM", "  -> 清理后: '$courseName', 教师: '$teacher', 教室: '$classroom'")
                        android.util.Log.d("SmartImportVM", "  -> 最终字段 - 教师: '$teacher', 教室: '$classroom'")
                        
                        // 验证课程名称（基本验证，不要太严格）
                        if (courseName.isEmpty() || courseName.length < 2) {
                            android.util.Log.w("SmartImportVM", "课程名称过短，跳过: '$originalCourseName'")
                            return@mapIndexedNotNull null
                        }
                        
                        // 移除特殊标记（保留课程名称）
                        courseName = courseName
                            .replace("【调】", "")
                            .replace("★", "")
                            .trim()
                        
                        // 验证教师姓名（只检查长度，不清空）
                        if (teacher.isNotEmpty() && teacher.length > Constants.Course.MAX_TEACHER_NAME_LENGTH) {
                            android.util.Log.w("SmartImportVM", "教师姓名过长，截断: $teacher")
                            teacher = teacher.take(Constants.Course.MAX_TEACHER_NAME_LENGTH)
                        }
                        
                        // 验证教室（只检查长度，不清空）
                        if (classroom.isNotEmpty() && classroom.length > Constants.Course.MAX_CLASSROOM_NAME_LENGTH) {
                            android.util.Log.w("SmartImportVM", "教室名称过长，截断: $classroom")
                            classroom = classroom.take(Constants.Course.MAX_CLASSROOM_NAME_LENGTH)
                        }
                        
                        // 解析星期和节次（兼容多种格式）
                        var dayOfWeek = aiCourse.resolveDayOfWeek()
                        val sectionRow = aiCourse.resolveSectionRow()
                        val sectionCount = aiCourse.resolveSectionCount()
                        
                        // 确保星期值在1-7范围内（如果超出，取模）
                        if (dayOfWeek < 1) {
                            dayOfWeek = 1
                        } else if (dayOfWeek > 7) {
                            dayOfWeek = ((dayOfWeek - 1) % 7) + 1
                        }
                        
                        // 计算开始节次（优先使用新格式的startSection）
                        val startSection = if (aiCourse.startSection > 0) {
                            aiCourse.startSection  // 新格式直接提供
                        } else {
                            (sectionRow - 1) * 2 + 1  // 旧格式需要转换
                        }
                        
                        // 解析周次（优先使用JavaScript已解析的数据）
                        val weekExpression = aiCourse.resolveWeekExpression()
                        val weekList = if (aiCourse.weeks.isNotEmpty()) {
                            android.util.Log.d("SmartImportVM", "转换课程${index + 1}: $courseName, 使用JS解析的周次: ${aiCourse.weeks.joinToString(",")}")
                            aiCourse.weeks  // JavaScript已经解析好了
                        } else {
                            android.util.Log.d("SmartImportVM", "转换课程${index + 1}: $courseName, 周次表达式: $weekExpression")
                            val parsed = parseWeekInfo(weekExpression)
                            android.util.Log.d("SmartImportVM", "  -> 解析得到周次: ${parsed.joinToString(",")}")
                            parsed
                        }
                        
                        // ✅ 使用sanitizeXSS过滤字段
                        ParsedCourse(
                            courseName = InputValidator.sanitizeXSS(courseName),
                            teacher = InputValidator.sanitizeXSS(teacher),
                            classroom = InputValidator.sanitizeXSS(classroom),
                            dayOfWeek = dayOfWeek,
                            startSection = startSection,
                            sectionCount = sectionCount,
                            weekExpression = weekExpression,
                            weeks = weekList,
                            credit = aiCourse.credit  // 学分
                        )
                    }
                    android.util.Log.d("SmartImportVM", "===== 转换完成，共${parsedCourses.size}门课程 =====")
                    
                    _parsedCourses.value = parsedCourses
                    _parseState.value = ParseState.Success(debugInfo = _debugInfo.value)
                    
                } catch (e: Exception) {
                    // 如果解析调试格式失败，尝试直接解析课程列表
                    android.util.Log.w("SmartImportVM", "解析调试格式失败，尝试直接解析", e)
                    
                    val type = object : com.google.gson.reflect.TypeToken<List<com.wind.ggbond.classtime.ui.screen.scheduleimport.JsonCourse>>() {}.type
                    val jsonCourses: List<com.wind.ggbond.classtime.ui.screen.scheduleimport.JsonCourse> = gson.fromJson(cleanJson, type)
                    
                    if (jsonCourses.isEmpty()) {
                        _parseState.value = ParseState.Error("未找到课程信息\n请确保已登录并进入课表页面")
                        return@launch
                    }
                    
                    val parsedCourses = jsonCourses.map { jsonCourse ->
                        val weekList = parseWeekInfo(jsonCourse.weekInfo)
                        ParsedCourse(
                            courseName = jsonCourse.courseName,
                            teacher = jsonCourse.teacher,
                            classroom = jsonCourse.classroom,
                            dayOfWeek = jsonCourse.dayOfWeek,
                            startSection = (jsonCourse.sectionRow - 1) * 2 + 1,
                            sectionCount = 2,
                            weekExpression = jsonCourse.weekInfo,
                            weeks = weekList
                        )
                    }
                    
                    _parsedCourses.value = parsedCourses
                    _parseState.value = ParseState.Success(debugInfo = _debugInfo.value)
                }
                
            } catch (e: Exception) {
                android.util.Log.e("SmartImportVM", "解析失败", e)
                _parseState.value = ParseState.Error("解析失败：${e.message}")
            }
        }
    }
    
    /**
     * 调试响应数据类（小爱课程表格式）
     */
    data class DebugResponse(
        val debug: List<String>? = null,
        val courses: List<AIScheduleCourse>? = null,
        val error: String? = null
    )
    
    /**
     * 小爱课程表的课程格式
     * 支持两种格式：
     * 1. JavaScript提取的格式：courseName, teacher, classroom, weekInfo, dayOfWeek, sectionRow
     * 2. 小爱课程表格式：name, teacher, position, weekInfo, day, section
     */
    data class AIScheduleCourse(
        val name: String = "",
        val courseName: String = "",  // JavaScript提取格式
        val teacher: String = "",
        val position: String = "",
        val classroom: String = "",  // JavaScript提取格式
        val weekInfo: String = "",  // 旧格式
        val weekExpression: String = "",  // 新格式：周次表达式
        val weeks: List<Int> = emptyList(),  // 新格式：解析后的周次数组
        val day: Int = 0,
        val dayOfWeek: Int = 0,  // JavaScript提取格式
        val section: Int = 0,
        val sectionRow: Int = 0,  // JavaScript提取格式
        val startSection: Int = 0,  // 新格式：开始节次
        val sectionCount: Int = 2,  // 新格式：持续节数
        val credit: Float = 0f  // 学分
    ) {
        // 解析课程名称（兼容两种格式）
        fun resolveCourseName(): String = courseName.ifEmpty { name }
        
        // 解析教室（兼容两种格式）
        fun resolveClassroom(): String = classroom.ifEmpty { position }
        
        // 解析星期（兼容两种格式）
        fun resolveDayOfWeek(): Int = if (dayOfWeek > 0) dayOfWeek else day
        
        // 解析节次（兼容多种格式）
        fun resolveSectionRow(): Int = when {
            startSection > 0 -> startSection  // 优先使用新格式
            sectionRow > 0 -> sectionRow
            else -> section
        }
        
        // 解析持续节数
        fun resolveSectionCount(): Int = if (sectionCount > 0) sectionCount else 2
        
        // 解析周次表达式（兼容多种格式）
        fun resolveWeekExpression(): String = weekExpression.ifEmpty { weekInfo }
        
        // 解析周次列表（如果JavaScript已经解析好了，就直接使用）
        fun resolveWeeks(): List<Int> = weeks
    }
    
    /**
     * 清理课程名称，移除学校名称前缀
     */
    private fun cleanCourseName(rawName: String): String {
        var cleaned = rawName.trim()
        
        // 常见的学校名称后缀模式（按长度从长到短排序，优先匹配更长的模式）
        val schoolNamePatterns = listOf(
            Regex("""^[^\s]*高等专科学校\s*"""),
            Regex("""^[^\s]*职业技术大学\s*"""),
            Regex("""^[^\s]*职业技术学院\s*"""),
            Regex("""^[^\s]*专科学校\s*"""),
            Regex("""^[^\s]*职业大学\s*"""),
            Regex("""^[^\s]*大学\s*"""),
            Regex("""^[^\s]*学院\s*"""),
        )
        
        // 尝试移除学校名称前缀
        for (pattern in schoolNamePatterns) {
            val beforeReplace = cleaned
            cleaned = cleaned.replace(pattern, "").trim()
            if (cleaned != beforeReplace && cleaned.isNotEmpty()) {
                break
            }
        }
        
        return if (cleaned.isEmpty()) rawName else cleaned
    }
    
    /**
     * 从包含完整信息的文本中解析课程信息
     * 例如："【调】职业发展与就业指导★(1-2节)1-4周,8-17周,19周潼南校区32413贾雯超职业发展与就业指导-0016信息24501;信息25201考查理论:322362.0"
     */
    private fun parseCourseNameFromText(text: String): ParsedCourseInfo {
        var courseName = text
        var teacher = ""
        var classroom = ""
        var weekInfo = ""
        
        // 1. 提取周次信息（如 "1-4周,6-15周,19周" 或 "6-14周(双)"）
        val weekPattern = Regex("""(\d+-\d+周(?:\([单双]\))?|\d+周(?:\([单双]\))?)""")
        val weekMatches = weekPattern.findAll(text)
        weekInfo = weekMatches.map { it.value }.joinToString(",")
        
        // 2. 提取教室信息（通常在"校区"之后，是数字，如 "潼南校区31509"）
        // 模式1：校区+数字教室号（5-6位数字）
        val classroomPattern1 = Regex("""[\u4e00-\u9fa5]+校区\s*(\d{4,6})""")
        val classroomMatch1 = classroomPattern1.find(text)
        if (classroomMatch1 != null) {
            classroom = classroomMatch1.groupValues[1]
        } else {
            // 模式2：未排地点
            if (text.contains("未排地点")) {
                classroom = "未排地点"
            } else {
                // 模式3：直接查找校区后的数字
                val classroomPattern2 = Regex("""校区\s*(\d{4,6})""")
                val classroomMatch2 = classroomPattern2.find(text)
                if (classroomMatch2 != null) {
                    classroom = classroomMatch2.groupValues[1]
                }
            }
        }
        
        // 3. 提取教师姓名（通常在教室之后，是2-4个中文字符）
        // 常见模式：校区教室号+教师姓名，或者"未排地点"+教师姓名
        val teacherPatterns = listOf(
            Regex("""校区\s*\d{4,6}\s*([\u4e00-\u9fa5]{2,4})"""),  // 校区31509徐昌
            Regex("""未排地点\s*([\u4e00-\u9fa5]{2,4})"""),      // 未排地点陈泓林
            Regex("""校区\s*([\u4e00-\u9fa5]{2,4})"""),         // 校区徐昌（备用）
        )
        
        for (pattern in teacherPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val possibleTeacher = match.groupValues[1]
                // 验证是否是教师姓名（排除常见非教师词汇）
                val excludedWords = listOf("校区", "地点", "考查", "考试", "理论", "信息", "调", "职业", "发展", "就业", "指导", "英语", "思想", "特色", "社会", "主义", "概论", "代数", "电路", "创新", "创业", "教育", "体育", "毛泽东", "中国特色", "社会主", "理论概")
                if (!excludedWords.any { possibleTeacher.contains(it) }) {
                    teacher = possibleTeacher
                    break
                }
            }
        }
        
        // 如果还没找到教师，尝试从文本后半部分查找（排除非教师词汇）
        if (teacher.isEmpty()) {
            val textLength = text.length
            val teacherPattern = Regex("""([\u4e00-\u9fa5]{2,4})""")
            val allMatches = teacherPattern.findAll(text)
            val excludedWords = listOf("校区", "地点", "考查", "考试", "理论", "信息", "调", "职业", "发展", "就业", "指导", "英语", "思想", "特色", "社会", "主义", "概论", "代数", "电路", "创新", "创业", "教育", "体育", "未排", "潼南", "校区", "地点")
            
            allMatches.forEach { match ->
                val position = match.range.first
                val word = match.value
                // 在文本后50%部分查找，且不在排除列表中，且长度合适（2-4个字符）
                if (position > textLength * 0.5 && 
                    word.length in 2..4 && 
                    !excludedWords.contains(word) &&
                    !excludedWords.any { word.contains(it) }) {
                    teacher = word
                    return@forEach
                }
            }
        }
        
        // 4. 清理课程名称：提取真正的课程名称部分
        // 课程名称通常在开头，到节次信息或校区信息之前
        // 先尝试提取【调】+课程名，或者直接课程名
        val courseNamePattern1 = Regex("""^(\[?调\]?[\u4e00-\u9fa5\w\s]+?)(?:\★|\(|\d+-\d+节|校区|周|未排)""")
        val courseNameMatch1 = courseNamePattern1.find(text)
        if (courseNameMatch1 != null) {
            courseName = courseNameMatch1.groupValues[1]
                .replace(Regex("""★|☆|〇|■|◆"""), "") // 移除课程类型标记
                .trim()
        } else {
            // 如果正则匹配失败，手动清理
            courseName = text
                .replace(weekPattern, "") // 移除周次
                .replace(Regex("""[\u4e00-\u9fa5]+校区\s*\d+"""), "") // 移除校区+教室号
                .replace(Regex("""未排地点"""), "") // 移除未排地点
                .replace(teacher, "") // 移除教师姓名
                .replace(Regex("""★|☆|〇|■|◆"""), "") // 移除课程类型标记
                .replace(Regex("""\(.*?节\)"""), "") // 移除节次信息如"(1-2节)"
                .replace(Regex("""[\u4e00-\u9fa5\w]+-\d+"""), "") // 移除课程代码（如"体育3-0044"）
                .replace(Regex("""信息\d+[;；]?"""), "") // 移除信息代码
                .replace(Regex("""考查|考试"""), "") // 移除考查/考试
                .replace(Regex("""理论:\d+\.?\d*"""), "") // 移除理论分数
                .trim()
        }
        
        // 进一步清理：移除重复的课程名称
        // 例如："毛泽东思想和中国特色社会主义理论概论毛泽东思想和中国特色社会主义理论概论-0001"
        // 如果课程名称超过30个字符，尝试检测重复
        if (courseName.length > 30) {
            val halfLength = courseName.length / 2
            val firstHalf = courseName.substring(0, halfLength)
            val secondHalf = courseName.substring(halfLength)
            // 如果后半部分与前半部分相似，只保留前半部分
            if (secondHalf.startsWith(firstHalf.substring(0, minOf(10, firstHalf.length)))) {
                courseName = firstHalf
            }
        }
        
        // 移除末尾的课程代码和数字
        courseName = courseName
            .replace(Regex("""-?\d+$"""), "") // 移除末尾数字
            .replace(Regex("""-\d+$"""), "") // 移除末尾的"-数字"
            .trim()
        
        // 如果课程名称仍然很长，尝试只保留前30个字符（通常是课程名的主体部分）
        if (courseName.length > 50) {
            // 尝试找到第一个合理的断点（在标点符号或空格处）
            val breakPoints = listOf("(", "（", "周", "校区", "★", " ", "【", "】")
            var breakIndex = 50
            for (point in breakPoints) {
                val index = courseName.indexOf(point, 20)
                if (index in 20..50) {
                    breakIndex = index
                    break
                }
            }
            courseName = courseName.substring(0, breakIndex).trim()
        }
        
        return ParsedCourseInfo(courseName, teacher, classroom, weekInfo)
    }
    
    /**
     * 解析后的课程信息
     */
    private data class ParsedCourseInfo(
        val courseName: String,
        val teacher: String,
        val classroom: String,
        val weekInfo: String
    )
    
    /**
     * 解析周次信息（支持复杂格式）
     * 
     * 支持格式：
     * - 简单范围：       "1-16周" -> [1,2,3,...,16]
     * - 多段周次：       "(1-2节)1-4周,6-15周,19周" -> [1,2,3,4,6,7,8,...,15,19]
     * - 单周：           "1-8周(单)" -> [1,3,5,7]
     * - 双周：           "1-8周(双)" -> [2,4,6,8]
     * - 单个周次：       "5周" -> [5]
     * - 混合格式：       "(3-4节)1-10周(双),15周" -> [2,4,6,8,10,15]
     */
    private fun parseWeekInfo(weekInfo: String): List<Int> {
        if (weekInfo.isEmpty()) {
            return (1..16).toList()
        }
        
        try {
            var text = weekInfo
            
            // 调试日志：输入
            android.util.Log.d("SmartImportVM", "【周次解析】原始输入: $weekInfo")
            
            // 去除节次信息：(1-2节)1-4周 -> 1-4周
            text = text.replace(Regex("\\(\\d+-\\d+节\\)"), "").trim()
            android.util.Log.d("SmartImportVM", "【周次解析】去除节次后: $text")
            
            // 分割多个周次段：1-3周(单),4-6周(双),7-15周 -> [1-3周(单), 4-6周(双), 7-15周]
            val segments = text.split(Regex("[,，;；]"))
            android.util.Log.d("SmartImportVM", "【周次解析】分段: ${segments.joinToString(" | ")}")
            
            val allWeeks = mutableSetOf<Int>()
            
            for (segment in segments) {
                // ⭐ 关键修复：每个段落单独检测单双周标记
                val isSingle = segment.contains("单")
                val isDouble = segment.contains("双")
                
                // 去除单双周标记及其括号：1-8周(单) -> 1-8周
                val cleaned = segment
                    .replace(Regex("\\([单双]\\)"), "")
                    .replace(Regex("[单双]"), "")
                    .replace("周", "")
                    .replace("星期", "")
                    .trim()
                
                if (cleaned.isEmpty()) continue
                
                when {
                    // 范围：1-16
                    cleaned.contains("-") -> {
                        val parts = cleaned.split("-")
                        val start = parts[0].trim().toIntOrNull() ?: continue
                        val end = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: continue
                        
                        android.util.Log.d("SmartImportVM", "【周次解析】处理段落: $segment -> 范围$start-$end, 单=$isSingle, 双=$isDouble")
                        
                        for (week in start..end) {
                            // 应用该段落的单双周过滤
                            if (isSingle && week % 2 == 0) {
                                android.util.Log.d("SmartImportVM", "【周次解析】  跳过第${week}周(双周，该段要求单周)")
                                continue
                            }
                            if (isDouble && week % 2 == 1) {
                                android.util.Log.d("SmartImportVM", "【周次解析】  跳过第${week}周(单周，该段要求双周)")
                                continue
                            }
                            android.util.Log.d("SmartImportVM", "【周次解析】  ✓ 添加第${week}周")
                            allWeeks.add(week)
                        }
                    }
                    // 单个周次：5
                    cleaned.matches(Regex("\\d+")) -> {
                        val week = cleaned.toInt()
                        android.util.Log.d("SmartImportVM", "【周次解析】处理段落: $segment -> 单个周次$week, 单=$isSingle, 双=$isDouble")
                        if (isSingle && week % 2 == 0) {
                            android.util.Log.d("SmartImportVM", "【周次解析】  跳过第${week}周(双周，该段要求单周)")
                            continue
                        }
                        if (isDouble && week % 2 == 1) {
                            android.util.Log.d("SmartImportVM", "【周次解析】  跳过第${week}周(单周，该段要求双周)")
                            continue
                        }
                        android.util.Log.d("SmartImportVM", "【周次解析】  ✓ 添加第${week}周")
                        allWeeks.add(week)
                    }
                }
            }
            
            val result = if (allWeeks.isEmpty()) {
                (1..16).toList()
            } else {
                allWeeks.sorted()
            }
            
            android.util.Log.d("SmartImportVM", "【周次解析】最终结果: ${result.joinToString(", ")}")
            return result
            
        } catch (e: Exception) {
            android.util.Log.e("SmartImportVM", "解析周次失败: $weekInfo", e)
            return (1..16).toList()
        }
    }
    
    /**
     * 确认导入
     */
    fun confirmImport() {
        viewModelScope.launch {
            try {
                val courses = _parsedCourses.value
                if (courses.isEmpty()) return@launch
                
                // 获取当前课表（包含学期时间信息）
                val currentSchedule = scheduleRepository.getCurrentSchedule()
                
                if (currentSchedule == null) {
                    _parseState.value = ParseState.Error("请先设置课表")
                    return@launch
                }
                
                // 查询已存在的课表数量，用于生成新课表名称
                val existingSchedules = scheduleRepository.getAllSchedulesList()
                val scheduleCount = existingSchedules.size
                
                // 生成新课表名称：我的课表、我的课表(2)、我的课表(3)...
                val scheduleName = if (scheduleCount == 0) {
                    "我的课表"
                } else {
                    "我的课表(${scheduleCount + 1})"
                }
                
                // 创建新课表
                val newSchedule = Schedule(
                    name = scheduleName,
                    schoolName = "",
                    startDate = currentSchedule.startDate,
                    endDate = currentSchedule.endDate,
                    totalWeeks = currentSchedule.totalWeeks,
                    isCurrent = false // 不自动设置为当前课表
                )
                
                val scheduleId = scheduleRepository.insertSchedule(newSchedule)
                android.util.Log.d("SmartImport", "创建新课表: $scheduleName, ID: $scheduleId")
                
                // !!!! 这段代码在 SmartImportViewModel 中，但现在已经不用了
                // 现在使用 ImportScheduleViewModel 的 confirmImport() 方法
                android.util.Log.e("SmartImport", "错误：这段代码不应该被执行！")
            } catch (e: Exception) {
                android.util.Log.e("SmartImport", "导入失败", e)
                _parseState.value = ParseState.Error("导入失败：${e.message}")
            }
        }
    }
    
    /**
     * 重置状态
     */
    fun resetState() {
        _parseState.value = ParseState.Idle
        _parsedCourses.value = emptyList()
        _debugInfo.value = emptyList()
        _importedSemesterInfo.value = null
    }
    
    /**
     * 重置解析状态（保留课程数据）
     * 用于避免返回时重复导航
     */
    fun resetParseState() {
        _parseState.value = ParseState.Idle
        // 保留 parsedCourses 和 debugInfo
    }
    
    /**
     * 获取随机颜色
     */
    private fun getRandomColor(): String {
        val colors = listOf(
            "#FF6B6B", "#4ECDC4", "#45B7D1", "#FFA07A",
            "#98D8C8", "#F7DC6F", "#BB8FCE", "#85C1E2",
            "#F8B739", "#52B788"
        )
        return colors.random()
    }
}
