package com.wind.ggbond.classtime.service

import android.content.Context
import android.net.Uri
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.repository.ClassTimeRepository
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.service.helper.ImportParser
import com.wind.ggbond.classtime.util.AppLogger
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

    data class ImportResult(val success: Boolean, val importedCount: Int = 0, val errorMessage: String? = null, val duplicateCount: Int = 0, val skippedCount: Int = 0)
    data class ValidationResult(val isValid: Boolean, val message: String)

    suspend fun importFromUri(uri: Uri, scheduleId: Long): ImportResult = withContext(Dispatchers.IO) {
        try {
            val content = readFileContent(uri)
            when (getFileExtension(uri).lowercase()) {
                "json" -> importFromJson(content, scheduleId)
                "ics" -> importFromIcs(content, scheduleId)
                "csv" -> importFromCsv(content, scheduleId)
                else -> ImportResult(success = false, errorMessage = "不支持的文件格式")
            }
        } catch (e: Exception) {
            ImportResult(success = false, errorMessage = "导入失败: ${e.message}")
        }
    }

    private fun parseJsonCourses(content: String): List<Course> {
        val jsonObject = gson.fromJson(content, JsonObject::class.java)
        return if (jsonObject.has("courses")) {
            val arr = jsonObject.getAsJsonArray("courses")
            gson.fromJson(arr, object : TypeToken<List<Course>>() {}.type)
        } else {
            gson.fromJson(content, object : TypeToken<List<Course>>() {}.type)
        }
    }

    private suspend fun importFromJson(content: String, scheduleId: Long): ImportResult {
        return try {
            AppLogger.d(TAG, "开始导入JSON，scheduleId: $scheduleId")
            val courses = parseJsonCourses(content)
            if (courses.isEmpty()) return ImportResult(success = false, errorMessage = "JSON文件中没有课程数据")

        var importedCount = 0; var duplicateCount = 0
        val existingCourses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
        val existingColors = existingCourses.map { it.color }.toMutableList()

        val toInsert = mutableListOf<Course>()
        courses.forEach { course ->
            if (existingCourses.any { it.courseName == course.courseName && it.dayOfWeek == course.dayOfWeek && it.startSection == course.startSection }) {
                duplicateCount++; return@forEach
            }
            val color = CourseColorPalette.getColorForCourse(course.courseName, existingColors).also { existingColors.add(it) }
            toInsert.add(course.copy(id = 0, scheduleId = scheduleId, color = color,
                weeks = course.weeks.takeIf { it.isNotEmpty() } ?: run {
                    if (course.weekExpression.isNotEmpty()) importParser.parseWeeks(course.weekExpression) else (1..16).toList()
                },
                weekExpression = course.weekExpression.takeIf { it.isNotEmpty() } ?: run {
                    if (course.weeks.isNotEmpty()) "${course.weeks.first()}-${course.weeks.last()}周" else ""
                },
                createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
            ))
            importedCount++
        }
        if (toInsert.isNotEmpty()) courseRepository.insertCourses(toInsert)
        ImportResult(success = true, importedCount = importedCount, duplicateCount = duplicateCount)
        } catch (e: Exception) {
            AppLogger.e(TAG, "JSON导入失败", e); ImportResult(success = false, errorMessage = "JSON解析失败: ${e.message}")
        }
    }

    private suspend fun importFromIcs(content: String, scheduleId: Long): ImportResult {
        return try {
            AppLogger.d(TAG, "开始导入ICS文件，scheduleId: $scheduleId")
            val schedule = scheduleRepository.getScheduleById(scheduleId) ?: return ImportResult(success = false, errorMessage = "未找到课表信息，请先创建课表")

        val classTimes = classTimeRepository.getClassTimesByConfigSync()
        val existingCourses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
        val existingColors = existingCourses.map { it.color }.toMutableList()
        val courses = importParser.parseIcsContentFull(content, scheduleId, schedule.startDate, classTimes, existingColors)
        if (courses.isEmpty()) return ImportResult(success = false, errorMessage = "未能从ICS文件中解析出课程信息")

        var importedCount = 0; var duplicateCount = 0
        val toInsert = mutableListOf<Course>()
        courses.forEach { course ->
            if (existingCourses.any { it.courseName == course.courseName && it.dayOfWeek == course.dayOfWeek && it.startSection == course.startSection && it.weeks.intersect(course.weeks.toSet()).isNotEmpty() }) {
                duplicateCount++
            } else { toInsert.add(course); importedCount++ }
        }
        if (toInsert.isNotEmpty()) courseRepository.insertCourses(toInsert)
        ImportResult(success = true, importedCount = importedCount, duplicateCount = duplicateCount)
        } catch (e: Exception) {
            AppLogger.e(TAG, "ICS导入失败", e); ImportResult(success = false, errorMessage = "ICS解析失败: ${e.message}")
        }
    }

    private suspend fun importFromCsv(content: String, scheduleId: Long): ImportResult {
        return try {
            val dataLines = content.lines().filter { it.isNotBlank() }.drop(1)
            if (dataLines.isEmpty()) return ImportResult(success = false, errorMessage = "文件为空")

        val existingCourses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
        val existingColors = existingCourses.map { it.color }.toMutableList()
        val courses = dataLines.mapNotNull { line ->
            val fields = importParser.parseCsvLine(line)
            if (fields.size < 6) null else try {
                val name = fields[0].trim()
                val color = CourseColorPalette.getColorForCourse(name, existingColors).also { existingColors.add(it) }
                Course(courseName = name, teacher = fields[1].trim(), classroom = fields[2].trim(),
                    dayOfWeek = importParser.parseDayOfWeek(fields[3].trim()), startSection = importParser.parseSection(fields[4].trim()),
                    sectionCount = 1, weekExpression = fields[5].trim(), weeks = importParser.parseWeeks(fields[5].trim()),
                    credit = fields.getOrNull(6)?.toFloatOrNull() ?: 0f, note = fields.getOrNull(7)?.trim() ?: "",
                    color = color, scheduleId = scheduleId)
            } catch (_: Exception) { null }
        }
        var importedCount = 0; var duplicateCount = 0
        val toInsert = mutableListOf<Course>()
        courses.forEach { course ->
            if (existingCourses.any { it.courseName == course.courseName && it.dayOfWeek == course.dayOfWeek && it.startSection == course.startSection }) {
                duplicateCount++
            } else {
                toInsert.add(course)
                importedCount++
            }
        }
        if (toInsert.isNotEmpty()) courseRepository.insertCourses(toInsert)
        ImportResult(success = true, importedCount = importedCount, duplicateCount = duplicateCount)
    } catch (e: Exception) {
        ImportResult(success = false, errorMessage = "CSV解析失败: ${e.message}")
    }
}

    private fun readFileContent(uri: Uri): String = context.contentResolver.openInputStream(uri)?.use { inputStream ->
        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { it.readText() }
    } ?: throw Exception("无法打开文件")

    private fun getFileExtension(uri: Uri): String = uri.path?.let { path ->
        path.lastIndexOf('.').takeIf { it >= 0 }?.let { path.substring(it + 1) } ?: ""
    } ?: ""

    suspend fun validateImportFile(uri: Uri): ValidationResult = try {
        val content = readFileContent(uri); val ext = getFileExtension(uri).lowercase()
        when (ext) {
            "json" -> {
                val courses = parseJsonCourses(content)
                when {
                    courses.isEmpty() -> ValidationResult(false, "JSON文件中没有找到课程数据")
                    courses.any { it.courseName.isEmpty() || it.dayOfWeek !in 1..7 || it.startSection < 1 } ->
                        ValidationResult(false, "发现无效课程，请检查课程名称、星期和节次")
                    else -> ValidationResult(true, "JSON文件格式正确，包含${courses.size}门课程")
                }
            }
            "ics" -> if (content.contains("BEGIN:VCALENDAR")) ValidationResult(true, "ICS文件格式正确") else ValidationResult(false, "不是有效的ICS文件")
            "csv" -> {
                val count = content.lines().filter { it.isNotBlank() }.size - 1
                if (count > 0) ValidationResult(true, "CSV文件格式正确，共${count}行数据") else ValidationResult(false, "CSV文件为空")
            }
            else -> ValidationResult(false, "不支持的文件格式: $ext")
        }
    } catch (e: Exception) { ValidationResult(false, "文件验证失败: ${e.message}") }
}
