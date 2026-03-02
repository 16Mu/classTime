package com.wind.ggbond.classtime.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.repository.CourseRepository
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 导入服务
 * 支持导入JSON、ICS、CSV等格式的课程表数据
 */
@Singleton
class ImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val courseRepository: CourseRepository
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
                    val newCourse = course.copy(
                        id = 0, // 重置ID，让数据库自动生成
                        scheduleId = scheduleId,
                        color = CourseColorPalette.getColorForCourse(course.courseName),
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
     * 从ICS导入（基础实现）
     */
    private suspend fun importFromIcs(content: String, scheduleId: Long): ImportResult {
        return try {
            val courses = parseIcsContent(content, scheduleId)
            
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
            
            // 跳过表头
            val dataLines = lines.drop(1)
            val courses = mutableListOf<Course>()
            
            dataLines.forEach { line ->
                val fields = parseCsvLine(line)
                if (fields.size >= 6) {
                    try {
                        val courseName = fields[0].trim()
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
                            color = CourseColorPalette.getColorForCourse(courseName),
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
     * 解析ICS内容（简化版）
     */
    private fun parseIcsContent(content: String, scheduleId: Long): List<Course> {
        val courses = mutableListOf<Course>()
        val events = content.split("BEGIN:VEVENT")
        
        events.drop(1).forEach { event ->
            try {
                val summary = extractIcsField(event, "SUMMARY:")
                val location = extractIcsField(event, "LOCATION:")
                val description = extractIcsField(event, "DESCRIPTION:")
                
                // 从描述中提取教师信息
                val teacher = description.split("\\n")
                    .find { it.contains("教师：") }
                    ?.substringAfter("教师：") ?: ""
                
                if (summary.isNotEmpty()) {
                    // 简化处理：创建基础课程，用户需要手动调整时间
                    val course = Course(
                        courseName = summary,
                        teacher = teacher,
                        classroom = location,
                        dayOfWeek = 1, // 默认周一
                        startSection = 1, // 默认第1节
                        sectionCount = 2,
                        color = CourseColorPalette.getColorForCourse(summary),
                        scheduleId = scheduleId
                    )
                    courses.add(course)
                }
            } catch (e: Exception) {
                // 跳过解析失败的事件
            }
        }
        
        return courses.distinctBy { "${it.courseName}-${it.teacher}-${it.classroom}" }
    }
    
    /**
     * 从ICS事件中提取字段
     */
    private fun extractIcsField(event: String, fieldName: String): String {
        val lines = event.lines()
        val line = lines.find { it.trim().startsWith(fieldName) }
        return line?.substringAfter(fieldName)?.trim() ?: ""
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


