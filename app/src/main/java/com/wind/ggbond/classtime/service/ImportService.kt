package com.wind.ggbond.classtime.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.repository.ClassTimeRepository
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.service.helper.ImportParser
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

private const val TAG = "ImportService"

@Singleton
class ImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val courseRepository: CourseRepository,
    private val classTimeRepository: ClassTimeRepository,
    private val scheduleRepository: ScheduleRepository,
    private val importParser: ImportParser
) {
    
    private val gson = Gson()
    
    data class ImportResult(
        val success: Boolean,
        val importedCount: Int = 0,
        val errorMessage: String? = null,
        val duplicateCount: Int = 0,
        val skippedCount: Int = 0
    )
    
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
    
    private suspend fun importFromJson(content: String, scheduleId: Long): ImportResult {
        return try {
            Log.d(TAG, "开始导入JSON，scheduleId: $scheduleId")
            
            val jsonObject = gson.fromJson(content, JsonObject::class.java)
            
            val courses: List<Course> = if (jsonObject.has("courses")) {
                val coursesArray = jsonObject.getAsJsonArray("courses")
                gson.fromJson(coursesArray, object : TypeToken<List<Course>>() {}.type)
            } else {
                gson.fromJson(content, object : TypeToken<List<Course>>() {}.type)
            }
            
            var importedCount = 0
            var duplicateCount = 0
            
            val existingCourses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            val existingColors = existingCourses.map { it.color }.toMutableList()
            
            courses.forEachIndexed { index, course ->
                val isDuplicate = existingCourses.any { 
                    it.courseName == course.courseName && 
                    it.dayOfWeek == course.dayOfWeek && 
                    it.startSection == course.startSection 
                }
                
                if (!isDuplicate) {
                    val assignedColor = CourseColorPalette.getColorForCourse(course.courseName, existingColors)
                    existingColors.add(assignedColor)
                    
                    val newCourse = course.copy(
                        id = 0,
                        scheduleId = scheduleId,
                        color = assignedColor,
                        weeks = if (course.weeks.isEmpty()) {
                            if (course.weekExpression.isNotEmpty()) {
                                importParser.parseWeeks(course.weekExpression)
                            } else {
                                (1..16).toList()
                            }
                        } else {
                            course.weeks
                        },
                        weekExpression = if (course.weekExpression.isEmpty() && course.weeks.isNotEmpty()) {
                            "${course.weeks.first()}-${course.weeks.last()}周"
                        } else {
                            course.weekExpression
                        },
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    
                    courseRepository.insertCourse(newCourse)
                    importedCount++
                } else {
                    duplicateCount++
                }
            }
            
            ImportResult(
                success = true,
                importedCount = importedCount,
                duplicateCount = duplicateCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "JSON导入失败", e)
            ImportResult(
                success = false,
                errorMessage = "JSON解析失败: ${e.message}"
            )
        }
    }
    
    private suspend fun importFromIcs(content: String, scheduleId: Long): ImportResult {
        return try {
            Log.d(TAG, "开始导入ICS文件，scheduleId: $scheduleId")
            
            val schedule = scheduleRepository.getScheduleById(scheduleId)
            if (schedule == null) {
                return ImportResult(
                    success = false,
                    errorMessage = "未找到课表信息，请先创建课表"
                )
            }
            
            val classTimes = classTimeRepository.getClassTimesByConfigSync()
            
            val existingCourses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            val existingColors = existingCourses.map { it.color }.toMutableList()
            
            val courses = importParser.parseIcsContentFull(content, scheduleId, schedule.startDate, classTimes, existingColors)
            
            if (courses.isEmpty()) {
                return ImportResult(
                    success = false,
                    errorMessage = "未能从ICS文件中解析出课程信息"
                )
            }
            
            var importedCount = 0
            var duplicateCount = 0
            
            courses.forEach { course ->
                val isDuplicate = existingCourses.any {
                    it.courseName == course.courseName &&
                    it.dayOfWeek == course.dayOfWeek &&
                    it.startSection == course.startSection &&
                    it.weeks.intersect(course.weeks.toSet()).isNotEmpty()
                }
                
                if (!isDuplicate) {
                    courseRepository.insertCourse(course)
                    importedCount++
                } else {
                    duplicateCount++
                }
            }
            
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
    
    private suspend fun importFromCsv(content: String, scheduleId: Long): ImportResult {
        return try {
            val lines = content.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) {
                return ImportResult(success = false, errorMessage = "文件为空")
            }
            
            val existingCourses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            val existingColors = existingCourses.map { it.color }.toMutableList()
            
            val dataLines = lines.drop(1)
            val courses = mutableListOf<Course>()
            
            dataLines.forEach { line ->
                val fields = importParser.parseCsvLine(line)
                if (fields.size >= 6) {
                    try {
                        val courseName = fields[0].trim()
                        val assignedColor = CourseColorPalette.getColorForCourse(courseName, existingColors)
                        existingColors.add(assignedColor)
                        
                        val course = Course(
                            courseName = courseName,
                            teacher = fields[1].trim(),
                            classroom = fields[2].trim(),
                            dayOfWeek = importParser.parseDayOfWeek(fields[3].trim()),
                            startSection = importParser.parseSection(fields[4].trim()),
                            sectionCount = 1,
                            weekExpression = fields[5].trim(),
                            weeks = importParser.parseWeeks(fields[5].trim()),
                            credit = if (fields.size > 6) fields[6].toFloatOrNull() ?: 0f else 0f,
                            note = if (fields.size > 7) fields[7].trim() else "",
                            color = assignedColor,
                            scheduleId = scheduleId
                        )
                        courses.add(course)
                    } catch (e: Exception) {
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
    
    private fun readFileContent(uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("无法打开文件")
        
        return BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
            reader.readText()
        }
    }
    
    private fun getFileExtension(uri: Uri): String {
        val path = uri.path ?: return ""
        val lastDot = path.lastIndexOf('.')
        return if (lastDot >= 0) path.substring(lastDot + 1) else ""
    }
    
    suspend fun validateImportFile(uri: Uri): ValidationResult {
        return try {
            val content = readFileContent(uri)
            val extension = getFileExtension(uri)
            
            when (extension.lowercase()) {
                "json" -> {
                    val jsonObject = gson.fromJson(content, JsonObject::class.java)
                    
                    val courses: List<Course> = if (jsonObject.has("courses")) {
                        val coursesArray = jsonObject.getAsJsonArray("courses")
                        gson.fromJson(coursesArray, object : TypeToken<List<Course>>() {}.type)
                    } else {
                        gson.fromJson(content, object : TypeToken<List<Course>>() {}.type)
                    }
                    
                    if (courses.isEmpty()) {
                        ValidationResult(false, "JSON文件中没有找到课程数据")
                    } else {
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
    
    data class ValidationResult(
        val isValid: Boolean,
        val message: String
    )
}
