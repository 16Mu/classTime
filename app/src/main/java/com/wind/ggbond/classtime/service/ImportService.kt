package com.wind.ggbond.classtime.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.repository.ClassTimeRepository
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.util.CourseColorPalette
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 导入服务
 * 支持导入JSON、ICS、CSV等格式的课程表数据
 */
private const val TAG = "ImportService"

@Singleton
class ImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val courseRepository: CourseRepository,
    private val classTimeRepository: ClassTimeRepository,
    private val scheduleRepository: ScheduleRepository
) {
    
    private val gson = Gson()
    
    /**
     * 导入结果
     */
    data class ImportResult(
        val success: Boolean,
        val importedCount: Int = 0,
        val errorMessage: String? = null,
        val duplicateCount: Int = 0,
        val skippedCount: Int = 0
    )
    
    /**
     * 从URI导入数据
     */
    suspend fun importFromUri(uri: Uri, scheduleId: Long): ImportResult {
        return withContext(Dispatchers.IO) {
            try {
                val content = readFileContent(uri)
                val fileExtension = getFileExtension(uri)
                
                when (fileExtension.lowercase()) {
                    "json" -> importFromJson(content, scheduleId)
                    "ics" -> importFromIcs(content, scheduleId)
                    "csv" -> importFromCsv(content, scheduleId)
                    else -> ImportResult(
                        success = false,
                        errorMessage = "不支持的文件格式: $fileExtension"
                    )
                }
            } catch (e: Exception) {
                ImportResult(
                    success = false,
                    errorMessage = "导入失败: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 从JSON导入
     */
    private suspend fun importFromJson(content: String, scheduleId: Long): ImportResult {
        return try {
            Log.d("ImportService", "开始导入JSON，scheduleId: $scheduleId")
            Log.d("ImportService", "JSON内容长度: ${content.length}")
            
            // 尝试解析为导出的格式（包含元数据）
            val jsonObject = gson.fromJson(content, JsonObject::class.java)
            
            val courses: List<Course> = if (jsonObject.has("courses")) {
                // 新格式：包含元数据
                Log.d("ImportService", "检测到新格式JSON，包含元数据")
                val coursesArray = jsonObject.getAsJsonArray("courses")
                Log.d("ImportService", "课程数组大小: ${coursesArray.size()}")
                gson.fromJson(coursesArray, object : TypeToken<List<Course>>() {}.type)
            } else {
                // 旧格式：直接是课程数组
                Log.d("ImportService", "检测到旧格式JSON，直接解析课程数组")
                gson.fromJson(content, object : TypeToken<List<Course>>() {}.type)
            }
            
            Log.d("ImportService", "解析出 ${courses.size} 门课程")
            
            // 导入课程
            var importedCount = 0
            var duplicateCount = 0
            
            // 先获取所有现有课程，避免在循环中重复查询
            val existingCourses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            Log.d("ImportService", "当前课表中有 ${existingCourses.size} 门课程")
            
            // 获取已有课程的颜色列表，用于智能分配新颜色
            val existingColors = existingCourses.map { it.color }.toMutableList()
            
            courses.forEachIndexed { index, course ->
                Log.d("ImportService", "处理第 ${index + 1} 门课程: ${course.courseName}")
                
                // 检查是否已存在相同课程
                val isDuplicate = existingCourses.any { 
                    it.courseName == course.courseName && 
                    it.dayOfWeek == course.dayOfWeek && 
                    it.startSection == course.startSection 
                }
                
                if (!isDuplicate) {
                    // 更新课程的scheduleId和其他必要字段
                    // 智能分配颜色：传入已有课程颜色列表，避免颜色重复
                    val assignedColor = CourseColorPalette.getColorForCourse(course.courseName, existingColors)
                    existingColors.add(assignedColor) // 将新分配的颜色加入列表
                    
                    val newCourse = course.copy(
                        id = 0, // 重置ID，让数据库自动生成
                        scheduleId = scheduleId,
                        color = assignedColor,
                        // 确保weeks列表不为空，如果为空则使用默认值
                        weeks = if (course.weeks.isEmpty()) {
                            // 如果weekExpression不为空，尝试解析
                            if (course.weekExpression.isNotEmpty()) {
                                parseWeeks(course.weekExpression)
                            } else {
                                // 默认全学期 1-16 周
                                (1..16).toList()
                            }
                        } else {
                            course.weeks
                        },
                        // 确保weekExpression与weeks列表一致
                        weekExpression = if (course.weekExpression.isEmpty() && course.weeks.isNotEmpty()) {
                            "${course.weeks.first()}-${course.weeks.last()}周"
                        } else {
                            course.weekExpression
                        },
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    
                    Log.d("ImportService", "插入课程: ${newCourse.courseName}, 周次: ${newCourse.weeks}")
                    courseRepository.insertCourse(newCourse)
                    importedCount++
                } else {
                    Log.d("ImportService", "跳过重复课程: ${course.courseName}")
                    duplicateCount++
                }
            }
            
            Log.d("ImportService", "导入完成: 成功 $importedCount 门，重复 $duplicateCount 门")
            
            ImportResult(
                success = true,
                importedCount = importedCount,
                duplicateCount = duplicateCount
            )
        } catch (e: Exception) {
            Log.e("ImportService", "JSON导入失败", e)
            ImportResult(
                success = false,
                errorMessage = "JSON解析失败: ${e.message}"
            )
        }
    }
    
    /**
     * 从ICS导入（完整实现）
     * 支持解析DTSTART/DTEND时间，自动推断星期和节次
     */
    private suspend fun importFromIcs(content: String, scheduleId: Long): ImportResult {
        return try {
            Log.d(TAG, "开始导入ICS文件，scheduleId: $scheduleId")
            
            // 获取课表信息（用于计算周次）
            val schedule = scheduleRepository.getScheduleById(scheduleId)
            if (schedule == null) {
                return ImportResult(
                    success = false,
                    errorMessage = "未找到课表信息，请先创建课表"
                )
            }
            
            // 获取课程时间配置（用于匹配节次）
            val classTimes = classTimeRepository.getClassTimesByConfigSync()
            Log.d(TAG, "获取到 ${classTimes.size} 个课程时间配置")
            
            // 获取已有课程的颜色列表，用于智能分配新颜色
            val existingCourses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            val existingColors = existingCourses.map { it.color }.toMutableList()
            
            // 解析ICS内容
            val courses = parseIcsContentFull(content, scheduleId, schedule.startDate, classTimes, existingColors)
            Log.d(TAG, "解析出 ${courses.size} 门课程")
            
            if (courses.isEmpty()) {
                return ImportResult(
                    success = false,
                    errorMessage = "未能从ICS文件中解析出课程信息"
                )
            }
            
            // 检查重复并导入
            var importedCount = 0
            var duplicateCount = 0
            
            courses.forEach { course ->
                // 检查是否已存在相同课程（课程名+星期+节次+周次）
                val isDuplicate = existingCourses.any {
                    it.courseName == course.courseName &&
                    it.dayOfWeek == course.dayOfWeek &&
                    it.startSection == course.startSection &&
                    it.weeks.intersect(course.weeks.toSet()).isNotEmpty()
                }
                
                if (!isDuplicate) {
                    courseRepository.insertCourse(course)
                    importedCount++
                    Log.d(TAG, "导入课程: ${course.courseName}, 周${course.dayOfWeek} 第${course.startSection}节")
                } else {
                    duplicateCount++
                    Log.d(TAG, "跳过重复课程: ${course.courseName}")
                }
            }
            
            Log.d(TAG, "ICS导入完成: 成功 $importedCount 门，重复 $duplicateCount 门")
            
            ImportResult(
                success = true,
                importedCount = importedCount,
                duplicateCount = duplicateCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "ICS导入失败", e)
            ImportResult(
                success = false,
                errorMessage = "ICS解析失败: ${e.message}"
            )
        }
    }
    
    /**
     * 从CSV导入
     */
    private suspend fun importFromCsv(content: String, scheduleId: Long): ImportResult {
        return try {
            val lines = content.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) {
                return ImportResult(success = false, errorMessage = "文件为空")
            }
            
            // 获取已有课程的颜色列表，用于智能分配新颜色
            val existingCourses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            val existingColors = existingCourses.map { it.color }.toMutableList()
            
            // 跳过表头
            val dataLines = lines.drop(1)
            val courses = mutableListOf<Course>()
            
            dataLines.forEach { line ->
                val fields = parseCsvLine(line)
                if (fields.size >= 6) {
                    try {
                        val courseName = fields[0].trim()
                        // 智能分配颜色：传入已有课程颜色列表，避免颜色重复
                        val assignedColor = CourseColorPalette.getColorForCourse(courseName, existingColors)
                        existingColors.add(assignedColor) // 将新分配的颜色加入列表
                        
                        val course = Course(
                            courseName = courseName,
                            teacher = fields[1].trim(),
                            classroom = fields[2].trim(),
                            dayOfWeek = parseDayOfWeek(fields[3].trim()),
                            startSection = parseSection(fields[4].trim()),
                            sectionCount = 1,
                            weekExpression = fields[5].trim(),
                            weeks = parseWeeks(fields[5].trim()),
                            credit = if (fields.size > 6) fields[6].toFloatOrNull() ?: 0f else 0f,
                            note = if (fields.size > 7) fields[7].trim() else "",
                            color = assignedColor,
                            scheduleId = scheduleId
                        )
                        courses.add(course)
                    } catch (e: Exception) {
                        // 跳过解析失败的行
                    }
                }
            }
            
            var importedCount = 0
            courses.forEach { course ->
                courseRepository.insertCourse(course)
                importedCount++
            }
            
            ImportResult(
                success = true,
                importedCount = importedCount
            )
        } catch (e: Exception) {
            ImportResult(
                success = false,
                errorMessage = "CSV解析失败: ${e.message}"
            )
        }
    }
    
    /**
     * 读取文件内容
     */
    private fun readFileContent(uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("无法打开文件")
        
        return BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
            reader.readText()
        }
    }
    
    /**
     * 获取文件扩展名
     */
    private fun getFileExtension(uri: Uri): String {
        val path = uri.path ?: return ""
        val lastDot = path.lastIndexOf('.')
        return if (lastDot >= 0) path.substring(lastDot + 1) else ""
    }
    
    /**
     * 解析ICS内容（完整版）
     * 支持DTSTART/DTEND时间解析、星期推断、节次匹配、周次计算
     * 
     * @param content ICS文件内容
     * @param scheduleId 课表ID
     * @param semesterStartDate 学期开始日期（用于计算周次）
     * @param classTimes 课程时间配置（用于匹配节次）
     */
    private fun parseIcsContentFull(
        content: String,
        scheduleId: Long,
        semesterStartDate: LocalDate,
        classTimes: List<ClassTime>,
        existingColors: MutableList<String>
    ): List<Course> {
        val courseMap = mutableMapOf<String, MutableList<IcsEventData>>()
        val events = content.split("BEGIN:VEVENT")
        
        Log.d(TAG, "解析ICS文件，共 ${events.size - 1} 个事件")
        
        events.drop(1).forEach { event ->
            try {
                // 提取基本信息
                val summary = extractIcsField(event, "SUMMARY")
                val location = extractIcsField(event, "LOCATION")
                val description = extractIcsField(event, "DESCRIPTION")
                val dtStart = extractIcsField(event, "DTSTART")
                val dtEnd = extractIcsField(event, "DTEND")
                val rrule = extractIcsField(event, "RRULE")
                
                if (summary.isEmpty() || dtStart.isEmpty()) {
                    Log.w(TAG, "跳过无效事件: summary=$summary, dtStart=$dtStart")
                    return@forEach
                }
                
                // 解析开始时间
                val startDateTime = parseIcsDateTime(dtStart)
                val endDateTime = if (dtEnd.isNotEmpty()) parseIcsDateTime(dtEnd) else null
                
                if (startDateTime == null) {
                    Log.w(TAG, "无法解析开始时间: $dtStart")
                    return@forEach
                }
                
                // 从描述中提取教师信息
                val teacher = extractTeacherFromDescription(description)
                
                // 从描述中提取节次信息（如果有）
                val sectionInfo = extractSectionFromDescription(description)
                
                // 从描述中提取周次信息（如果有）
                val weekInfo = extractWeekFromDescription(description)
                
                // 创建事件数据
                val eventData = IcsEventData(
                    courseName = summary,
                    teacher = teacher,
                    classroom = location,
                    startDateTime = startDateTime,
                    endDateTime = endDateTime,
                    rrule = rrule,
                    sectionInfo = sectionInfo,
                    weekInfo = weekInfo
                )
                
                // 按课程名称分组（同一门课可能有多个事件）
                val key = "$summary|$teacher|$location"
                courseMap.getOrPut(key) { mutableListOf() }.add(eventData)
                
            } catch (e: Exception) {
                Log.w(TAG, "解析事件失败: ${e.message}")
            }
        }
        
        Log.d(TAG, "分组后共 ${courseMap.size} 门不同课程")
        
        // 将事件数据转换为课程
        val courses = mutableListOf<Course>()
        
        courseMap.forEach { (_, eventList) ->
            // 按星期和时间分组，合并同一时间段的事件
            val timeSlotMap = mutableMapOf<String, MutableList<IcsEventData>>()
            
            eventList.forEach { event ->
                val dayOfWeek = event.startDateTime.dayOfWeek.value
                val startTime = event.startDateTime.toLocalTime()
                val key = "$dayOfWeek|$startTime"
                timeSlotMap.getOrPut(key) { mutableListOf() }.add(event)
            }
            
            // 为每个时间段创建课程记录
            timeSlotMap.forEach { (_, events) ->
                val firstEvent = events.first()
                
                // 计算星期几（1=周一，7=周日）
                val dayOfWeek = firstEvent.startDateTime.dayOfWeek.value
                
                // 计算节次
                val startTime = firstEvent.startDateTime.toLocalTime()
                val endTime = firstEvent.endDateTime?.toLocalTime()
                val (startSection, sectionCount) = if (firstEvent.sectionInfo != null) {
                    // 优先使用描述中的节次信息
                    firstEvent.sectionInfo
                } else {
                    // 根据时间匹配节次
                    matchSectionByTime(startTime, endTime, classTimes)
                }
                
                // 计算周次列表
                val weeks = if (firstEvent.weekInfo != null) {
                    // 优先使用描述中的周次信息
                    firstEvent.weekInfo
                } else {
                    // 根据事件日期计算周次
                    calculateWeeksFromEvents(events, semesterStartDate)
                }
                
                if (weeks.isEmpty()) {
                    Log.w(TAG, "课程 ${firstEvent.courseName} 周次为空，跳过")
                    return@forEach
                }
                
                // 生成周次表达式
                val weekExpression = formatWeekExpression(weeks)
                
                // 智能分配颜色：传入已有课程颜色列表，避免颜色重复
                val assignedColor = CourseColorPalette.getColorForCourse(firstEvent.courseName, existingColors)
                existingColors.add(assignedColor) // 将新分配的颜色加入列表
                
                val course = Course(
                    courseName = firstEvent.courseName,
                    teacher = firstEvent.teacher,
                    classroom = firstEvent.classroom,
                    dayOfWeek = dayOfWeek,
                    startSection = startSection,
                    sectionCount = sectionCount,
                    weeks = weeks,
                    weekExpression = weekExpression,
                    color = assignedColor,
                    scheduleId = scheduleId,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                
                courses.add(course)
                Log.d(TAG, "创建课程: ${course.courseName}, 周${course.dayOfWeek} 第${course.startSection}-${course.startSection + course.sectionCount - 1}节, ${course.weekExpression}")
            }
        }
        
        return courses
    }
    
    /**
     * ICS事件数据类
     */
    private data class IcsEventData(
        val courseName: String,
        val teacher: String,
        val classroom: String,
        val startDateTime: LocalDateTime,
        val endDateTime: LocalDateTime?,
        val rrule: String,
        val sectionInfo: Pair<Int, Int>?,  // (startSection, sectionCount)
        val weekInfo: List<Int>?           // 周次列表
    )
    
    /**
     * 从ICS事件中提取字段（支持多行折叠）
     */
    private fun extractIcsField(event: String, fieldName: String): String {
        val lines = event.lines()
        var result = StringBuilder()
        var isCapturing = false
        
        for (line in lines) {
            val trimmedLine = line.trim()
            
            // 检查是否是目标字段的开始
            if (trimmedLine.startsWith("$fieldName:") || trimmedLine.startsWith("$fieldName;")) {
                isCapturing = true
                // 提取冒号后的内容
                val colonIndex = trimmedLine.indexOf(':')
                if (colonIndex >= 0) {
                    result.append(trimmedLine.substring(colonIndex + 1))
                }
            } else if (isCapturing) {
                // ICS规范：续行以空格或制表符开头
                if (line.startsWith(" ") || line.startsWith("\t")) {
                    result.append(line.substring(1))
                } else {
                    // 遇到新字段，停止捕获
                    break
                }
            }
        }
        
        return result.toString()
            .replace("\\n", "\n")  // 处理转义换行
            .replace("\\,", ",")   // 处理转义逗号
            .replace("\\\\", "\\") // 处理转义反斜杠
            .trim()
    }
    
    /**
     * 解析ICS日期时间格式
     * 支持格式：
     * - 20260302T080000Z (UTC时间)
     * - 20260302T080000 (本地时间)
     * - TZID=Asia/Shanghai:20260302T080000 (带时区)
     */
    private fun parseIcsDateTime(dtString: String): LocalDateTime? {
        return try {
            var dateTimeStr = dtString
            
            // 处理带时区的格式
            if (dateTimeStr.contains(":")) {
                dateTimeStr = dateTimeStr.substringAfter(":")
            }
            
            // 移除Z后缀（UTC标记）
            val isUtc = dateTimeStr.endsWith("Z")
            dateTimeStr = dateTimeStr.removeSuffix("Z")
            
            // 解析日期时间
            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
            var dateTime = LocalDateTime.parse(dateTimeStr, formatter)
            
            // 如果是UTC时间，转换为本地时间
            if (isUtc) {
                val utcZone = ZoneId.of("UTC")
                val localZone = ZoneId.systemDefault()
                dateTime = dateTime.atZone(utcZone).withZoneSameInstant(localZone).toLocalDateTime()
            }
            
            dateTime
        } catch (e: Exception) {
            Log.w(TAG, "解析日期时间失败: $dtString, ${e.message}")
            null
        }
    }
    
    /**
     * 从描述中提取教师信息
     */
    private fun extractTeacherFromDescription(description: String): String {
        // 尝试多种格式
        val patterns = listOf(
            Regex("教师[：:](\\S+)"),
            Regex("Teacher[：:](\\S+)"),
            Regex("任课教师[：:](\\S+)"),
            Regex("授课教师[：:](\\S+)")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(description)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        
        return ""
    }
    
    /**
     * 从描述中提取节次信息
     * @return Pair(startSection, sectionCount) 或 null
     */
    private fun extractSectionFromDescription(description: String): Pair<Int, Int>? {
        // 尝试多种格式：第1-2节、1-2节、节次：1-2
        val patterns = listOf(
            Regex("第(\\d+)-(\\d+)节"),
            Regex("(\\d+)-(\\d+)节"),
            Regex("节次[：:](\\d+)-(\\d+)"),
            Regex("Section[：:](\\d+)-(\\d+)")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(description)
            if (match != null) {
                val start = match.groupValues[1].toIntOrNull() ?: continue
                val end = match.groupValues[2].toIntOrNull() ?: continue
                return Pair(start, end - start + 1)
            }
        }
        
        return null
    }
    
    /**
     * 从描述中提取周次信息
     * @return 周次列表 或 null
     */
    private fun extractWeekFromDescription(description: String): List<Int>? {
        // 尝试多种格式：1-16周、周次：1-16、第1-8周(单)
        val patterns = listOf(
            Regex("(\\d+)-(\\d+)周(?:\\(([单双])\\))?"),
            Regex("周次[：:](\\d+)-(\\d+)"),
            Regex("Week[：:](\\d+)-(\\d+)")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(description)
            if (match != null) {
                val start = match.groupValues[1].toIntOrNull() ?: continue
                val end = match.groupValues[2].toIntOrNull() ?: continue
                val oddEven = match.groupValues.getOrNull(3)
                
                val weeks = mutableListOf<Int>()
                for (week in start..end) {
                    when (oddEven) {
                        "单" -> if (week % 2 == 1) weeks.add(week)
                        "双" -> if (week % 2 == 0) weeks.add(week)
                        else -> weeks.add(week)
                    }
                }
                return weeks
            }
        }
        
        return null
    }
    
    /**
     * 根据时间匹配节次
     * @return Pair(startSection, sectionCount)
     */
    private fun matchSectionByTime(
        startTime: LocalTime,
        endTime: LocalTime?,
        classTimes: List<ClassTime>
    ): Pair<Int, Int> {
        if (classTimes.isEmpty()) {
            // 没有时间配置，使用默认值
            return Pair(1, 2)
        }
        
        // 查找最接近的开始节次
        var startSection = 1
        var minDiff = Long.MAX_VALUE
        
        for (classTime in classTimes) {
            val diff = kotlin.math.abs(ChronoUnit.MINUTES.between(startTime, classTime.startTime))
            if (diff < minDiff) {
                minDiff = diff
                startSection = classTime.sectionNumber
            }
        }
        
        // 计算持续节数
        var sectionCount = 2 // 默认2节
        
        if (endTime != null) {
            // 查找最接近的结束节次
            var endSection = startSection
            minDiff = Long.MAX_VALUE
            
            for (classTime in classTimes) {
                val diff = kotlin.math.abs(ChronoUnit.MINUTES.between(endTime, classTime.endTime))
                if (diff < minDiff) {
                    minDiff = diff
                    endSection = classTime.sectionNumber
                }
            }
            
            sectionCount = maxOf(1, endSection - startSection + 1)
        }
        
        return Pair(startSection, sectionCount)
    }
    
    /**
     * 根据事件日期计算周次列表
     */
    private fun calculateWeeksFromEvents(
        events: List<IcsEventData>,
        semesterStartDate: LocalDate
    ): List<Int> {
        val weeks = mutableSetOf<Int>()
        
        // 计算学期开始日期所在周的周一
        val semesterStartMonday = semesterStartDate.with(DayOfWeek.MONDAY)
        
        events.forEach { event ->
            val eventDate = event.startDateTime.toLocalDate()
            val eventMonday = eventDate.with(DayOfWeek.MONDAY)
            
            // 计算周次（从1开始）
            val weeksBetween = ChronoUnit.WEEKS.between(semesterStartMonday, eventMonday).toInt() + 1
            
            if (weeksBetween in 1..25) { // 合理的周次范围
                weeks.add(weeksBetween)
            }
        }
        
        return weeks.sorted()
    }
    
    /**
     * 格式化周次表达式
     * 例如：[1,2,3,4,6,7,8] -> "1-4,6-8周"
     */
    private fun formatWeekExpression(weeks: List<Int>): String {
        if (weeks.isEmpty()) return ""
        
        val sortedWeeks = weeks.sorted()
        val ranges = mutableListOf<String>()
        var rangeStart = sortedWeeks[0]
        var rangeEnd = sortedWeeks[0]
        
        for (i in 1 until sortedWeeks.size) {
            if (sortedWeeks[i] == rangeEnd + 1) {
                // 连续，扩展范围
                rangeEnd = sortedWeeks[i]
            } else {
                // 不连续，保存当前范围
                ranges.add(if (rangeStart == rangeEnd) "$rangeStart" else "$rangeStart-$rangeEnd")
                rangeStart = sortedWeeks[i]
                rangeEnd = sortedWeeks[i]
            }
        }
        
        // 保存最后一个范围
        ranges.add(if (rangeStart == rangeEnd) "$rangeStart" else "$rangeStart-$rangeEnd")
        
        return ranges.joinToString(",") + "周"
    }
    
    /**
     * 解析CSV行（处理引号）
     */
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        var currentField = StringBuilder()
        var inQuotes = false
        
        line.forEach { char ->
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    fields.add(currentField.toString())
                    currentField = StringBuilder()
                }
                else -> currentField.append(char)
            }
        }
        fields.add(currentField.toString())
        
        return fields
    }
    
    /**
     * 解析星期
     */
    private fun parseDayOfWeek(dayStr: String): Int {
        return when (dayStr) {
            "周一", "星期一", "Monday" -> 1
            "周二", "星期二", "Tuesday" -> 2
            "周三", "星期三", "Wednesday" -> 3
            "周四", "星期四", "Thursday" -> 4
            "周五", "星期五", "Friday" -> 5
            "周六", "星期六", "Saturday" -> 6
            "周日", "星期日", "Sunday" -> 7
            else -> dayStr.toIntOrNull() ?: 1
        }
    }
    
    /**
     * 解析节次
     */
    private fun parseSection(sectionStr: String): Int {
        // 提取数字，如 "1-2节" -> 1
        val match = Regex("\\d+").find(sectionStr)
        return match?.value?.toIntOrNull() ?: 1
    }
    
    /**
     * 解析周次表达式
     */
    private fun parseWeeks(weekExpression: String): List<Int> {
        val weeks = mutableListOf<Int>()
        
        try {
            // 简单实现：1-16周 -> [1,2,3...16]
            if (weekExpression.contains("-") && weekExpression.contains("周")) {
                val parts = weekExpression.replace("周", "").split("-")
                if (parts.size == 2) {
                    val start = parts[0].toIntOrNull() ?: 1
                    val end = parts[1].toIntOrNull() ?: 16
                    for (i in start..end) {
                        weeks.add(i)
                    }
                }
            } else if (weekExpression.matches(Regex("\\d+"))) {
                // 单个周次
                weekExpression.toIntOrNull()?.let { weeks.add(it) }
            } else {
                // 默认全学期
                for (i in 1..16) {
                    weeks.add(i)
                }
            }
        } catch (e: Exception) {
            // 解析失败，默认全学期
            for (i in 1..16) {
                weeks.add(i)
            }
        }
        
        return weeks
    }
    
    /**
     * 验证导入文件
     */
    suspend fun validateImportFile(uri: Uri): ValidationResult {
        return try {
            val content = readFileContent(uri)
            val extension = getFileExtension(uri)
            
            when (extension.lowercase()) {
                "json" -> {
                    // 尝试解析JSON并验证内容
                    val jsonObject = gson.fromJson(content, JsonObject::class.java)
                    
                    // 检查是否包含课程数据
                    val courses: List<Course> = if (jsonObject.has("courses")) {
                        val coursesArray = jsonObject.getAsJsonArray("courses")
                        gson.fromJson(coursesArray, object : TypeToken<List<Course>>() {}.type)
                    } else {
                        // 尝试直接解析为课程列表
                        gson.fromJson(content, object : TypeToken<List<Course>>() {}.type)
                    }
                    
                    if (courses.isEmpty()) {
                        ValidationResult(false, "JSON文件中没有找到课程数据")
                    } else {
                        // 验证每个课程的必要字段
                        val invalidCourses = courses.filter { course ->
                            course.courseName.isEmpty() || 
                            course.dayOfWeek !in 1..7 || 
                            course.startSection < 1
                        }
                        
                        if (invalidCourses.isNotEmpty()) {
                            ValidationResult(false, "发现 ${invalidCourses.size} 门无效课程，请检查课程名称、星期和节次")
                        } else {
                            ValidationResult(true, "JSON文件格式正确，包含 ${courses.size} 门课程")
                        }
                    }
                }
                "ics" -> {
                    if (content.contains("BEGIN:VCALENDAR")) {
                        ValidationResult(true, "ICS文件格式正确")
                    } else {
                        ValidationResult(false, "不是有效的ICS文件")
                    }
                }
                "csv" -> {
                    val lines = content.lines().filter { it.isNotBlank() }
                    if (lines.isNotEmpty()) {
                        ValidationResult(true, "CSV文件格式正确，共${lines.size - 1}行数据")
                    } else {
                        ValidationResult(false, "CSV文件为空")
                    }
                }
                else -> ValidationResult(false, "不支持的文件格式: $extension")
            }
        } catch (e: Exception) {
            ValidationResult(false, "文件验证失败: ${e.message}")
        }
    }
    
    /**
     * 验证结果
     */
    data class ValidationResult(
        val isValid: Boolean,
        val message: String
    )
}


