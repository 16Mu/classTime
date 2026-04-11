package com.wind.ggbond.classtime.service

import android.content.Context
import android.net.Uri
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.repository.ClassTimeRepository
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.service.helper.FormatDetector
import com.wind.ggbond.classtime.service.helper.FormatDetector.DetectedFormat
import com.wind.ggbond.classtime.service.helper.ImportParser
import com.wind.ggbond.classtime.service.helper.ImportValidator
import com.wind.ggbond.classtime.service.helper.VersionMigrator
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
    private val importParser: ImportParser,
    private val formatDetector: FormatDetector,
    private val versionMigrator: VersionMigrator,
    private val importValidator: ImportValidator
) {
    private val gson = Gson()

    data class ImportResult(
        val success: Boolean,
        val importedCount: Int = 0,
        val errorMessage: String? = null,
        val duplicateCount: Int = 0,
        val skippedCount: Int = 0,
        val warnings: List<String> = emptyList(),
        val detectedFormat: String? = null,
        val detectedVersion: String? = null,
        val isExternalSource: Boolean = false
    )

    data class ValidationResult(val isValid: Boolean, val message: String)

    suspend fun importFromUri(uri: Uri, scheduleId: Long): ImportResult = withContext(Dispatchers.IO) {
        try {
            val content = readFileContent(uri)
            val fileName = getFileName(uri)
            val detection = formatDetector.detectFormat(content, fileName)

            AppLogger.d(TAG, "格式检测: ${detection.format}, 版本: ${detection.version}, 外部来源: ${detection.isExternalSource}")

            when (detection.format) {
                DetectedFormat.JSON -> importFromJson(content, scheduleId, detection.version, detection.isExternalSource)
                DetectedFormat.ICS -> importFromIcs(content, scheduleId, detection.isExternalSource)
                DetectedFormat.CSV -> importFromCsv(content, scheduleId, detection.isExternalSource)
                DetectedFormat.HTML -> ImportResult(success = false, errorMessage = "HTML格式仅支持导出，不支持导入")
                DetectedFormat.UNKNOWN -> ImportResult(success = false, errorMessage = "无法识别的文件格式，请选择 JSON/ICS/CSV 文件")
            }.copy(detectedFormat = detection.format.name, detectedVersion = detection.version, isExternalSource = detection.isExternalSource)
        } catch (e: Exception) {
            AppLogger.e(TAG, "导入失败", e)
            ImportResult(success = false, errorMessage = "导入失败: ${e.message}")
        }
    }

    private suspend fun importFromJson(content: String, scheduleId: Long, detectedVersion: String?, isExternal: Boolean): ImportResult {
        return try {
            AppLogger.d(TAG, "开始导入JSON，scheduleId: $scheduleId, 版本: $detectedVersion, 外部: $isExternal")

            val migrationResult = versionMigrator.migrateJson(content, detectedVersion)
            if (!migrationResult.success) {
                return ImportResult(
                    success = false,
                    errorMessage = "版本迁移失败: ${migrationResult.warnings.joinToString("; ")}",
                    warnings = migrationResult.warnings
                )
            }

            val migratedContent = migrationResult.migratedData ?: content
            val courses = parseJsonCourses(migratedContent)
            if (courses.isEmpty()) {
                return ImportResult(success = false, errorMessage = "JSON文件中没有课程数据")
            }

            val validation = importValidator.validateCourses(courses)
            if (!validation.isValid) {
                return ImportResult(
                    success = false,
                    errorMessage = "数据验证失败: ${validation.errors.joinToString("; ")}",
                    warnings = validation.warnings
                )
            }

            var importedCount = 0
            var duplicateCount = 0
            val existingCourses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            val existingColors = existingCourses.map { it.color }.toMutableList()

            val toInsert = mutableListOf<Course>()
            validation.validCourses.forEach { course ->
                if (existingCourses.any {
                    it.courseName == course.courseName &&
                    it.dayOfWeek == course.dayOfWeek &&
                    it.startSection == course.startSection
                }) {
                    duplicateCount++
                } else {
                    val color = CourseColorPalette.getColorForCourse(course.courseName, existingColors)
                        .also { existingColors.add(it) }
                    toInsert.add(course.copy(
                        id = 0,
                        scheduleId = scheduleId,
                        color = color,
                        weeks = course.weeks.takeIf { it.isNotEmpty() } ?: run {
                            if (course.weekExpression.isNotEmpty()) importParser.parseWeeks(course.weekExpression) else (1..16).toList()
                        },
                        weekExpression = course.weekExpression.takeIf { it.isNotEmpty() } ?: run {
                            if (course.weeks.isNotEmpty()) "${course.weeks.first()}-${course.weeks.last()}周" else ""
                        },
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    ))
                    importedCount++
                }
            }

            if (toInsert.isNotEmpty()) courseRepository.insertCourses(toInsert)

            ImportResult(
                success = true,
                importedCount = importedCount,
                duplicateCount = duplicateCount,
                skippedCount = validation.skippedCourses.size,
                warnings = validation.warnings + migrationResult.warnings +
                    migrationResult.incompatibleFields.map { "不兼容字段: $it" }
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "JSON导入失败", e)
            ImportResult(success = false, errorMessage = "JSON解析失败: ${e.message}")
        }
    }

    private suspend fun importFromIcs(content: String, scheduleId: Long, isExternal: Boolean): ImportResult {
        return try {
            AppLogger.d(TAG, "开始导入ICS文件，scheduleId: $scheduleId, 外部: $isExternal")
            val schedule = scheduleRepository.getScheduleById(scheduleId)
                ?: return ImportResult(success = false, errorMessage = "未找到课表信息，请先创建课表")

            val classTimes = classTimeRepository.getClassTimesByConfigSync()
            val existingCourses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            val existingColors = existingCourses.map { it.color }.toMutableList()
            val courses = importParser.parseIcsContentFull(content, scheduleId, schedule.startDate, classTimes, existingColors)
            if (courses.isEmpty()) {
                return ImportResult(success = false, errorMessage = "未能从ICS文件中解析出课程信息")
            }

            val validation = importValidator.validateCourses(courses)

            var importedCount = 0
            var duplicateCount = 0
            val toInsert = mutableListOf<Course>()
            validation.validCourses.forEach { course ->
                if (existingCourses.any {
                    it.courseName == course.courseName &&
                    it.dayOfWeek == course.dayOfWeek &&
                    it.startSection == course.startSection &&
                    it.weeks.intersect(course.weeks.toSet()).isNotEmpty()
                }) {
                    duplicateCount++
                } else {
                    toInsert.add(course)
                    importedCount++
                }
            }

            if (toInsert.isNotEmpty()) courseRepository.insertCourses(toInsert)

            ImportResult(
                success = true,
                importedCount = importedCount,
                duplicateCount = duplicateCount,
                skippedCount = validation.skippedCourses.size,
                warnings = validation.warnings
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "ICS导入失败", e)
            ImportResult(success = false, errorMessage = "ICS解析失败: ${e.message}")
        }
    }

    private suspend fun importFromCsv(content: String, scheduleId: Long, isExternal: Boolean): ImportResult {
        return try {
            val dataLines = content.lines()
                .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }

            if (dataLines.isEmpty()) {
                return ImportResult(success = false, errorMessage = "文件为空")
            }

            val headerLine = dataLines.first()
            val headerFields = importParser.parseCsvLine(headerLine).map { it.trim().removeSurrounding("\"") }
            val isAppExport = headerFields.contains("序号") || headerFields.contains("课程名称")

            val courseDataLines = if (isAppExport || headerFields.any { it.matches(Regex("课程|名称|教师|教室")) }) {
                dataLines.drop(1)
            } else {
                dataLines
            }

            if (courseDataLines.isEmpty()) {
                return ImportResult(success = false, errorMessage = "CSV文件中没有课程数据")
            }

            val existingCourses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            val existingColors = existingCourses.map { it.color }.toMutableList()

            val courses = if (isAppExport) {
                parseAppCsvCourses(courseDataLines, scheduleId, existingColors)
            } else {
                parseExternalCsvCourses(courseDataLines, scheduleId, existingColors, headerFields)
            }

            if (courses.isEmpty()) {
                return ImportResult(success = false, errorMessage = "未能从CSV文件中解析出课程信息")
            }

            val validation = importValidator.validateCourses(courses)

            var importedCount = 0
            var duplicateCount = 0
            val toInsert = mutableListOf<Course>()
            validation.validCourses.forEach { course ->
                if (existingCourses.any {
                    it.courseName == course.courseName &&
                    it.dayOfWeek == course.dayOfWeek &&
                    it.startSection == course.startSection
                }) {
                    duplicateCount++
                } else {
                    toInsert.add(course)
                    importedCount++
                }
            }

            if (toInsert.isNotEmpty()) courseRepository.insertCourses(toInsert)

            ImportResult(
                success = true,
                importedCount = importedCount,
                duplicateCount = duplicateCount,
                skippedCount = validation.skippedCourses.size,
                warnings = validation.warnings,
                isExternalSource = isExternal
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "CSV导入失败", e)
            ImportResult(success = false, errorMessage = "CSV解析失败: ${e.message}")
        }
    }

    private fun parseAppCsvCourses(
        dataLines: List<String>,
        scheduleId: Long,
        existingColors: MutableList<String>
    ): List<Course> {
        return dataLines.mapNotNull { line ->
            val fields = importParser.parseCsvLine(line)
            if (fields.size < 6) null else try {
                val name = fields[1].trim().removeSurrounding("\"")
                val color = CourseColorPalette.getColorForCourse(name, existingColors)
                    .also { existingColors.add(it) }
                Course(
                    courseName = name,
                    teacher = fields[2].trim().removeSurrounding("\""),
                    classroom = fields[3].trim().removeSurrounding("\""),
                    dayOfWeek = importParser.parseDayOfWeek(fields[4].trim().removeSurrounding("\"")),
                    startSection = importParser.parseSection(fields[5].trim().removeSurrounding("\"")),
                    sectionCount = 1,
                    weekExpression = fields.getOrNull(7)?.trim()?.removeSurrounding("\"") ?: "",
                    weeks = importParser.parseWeeks(fields.getOrNull(7)?.trim()?.removeSurrounding("\"") ?: ""),
                    credit = fields.getOrNull(8)?.trim()?.removeSurrounding("\"")?.toFloatOrNull() ?: 0f,
                    note = fields.getOrNull(11)?.trim()?.removeSurrounding("\"") ?: "",
                    color = color,
                    scheduleId = scheduleId
                )
            } catch (_: Exception) { null }
        }
    }

    private fun parseExternalCsvCourses(
        dataLines: List<String>,
        scheduleId: Long,
        existingColors: MutableList<String>,
        headerFields: List<String>
    ): List<Course> {
        val nameIdx = headerFields.indexOfFirst { it.contains("课程") || it.contains("名称") || it.equals("name", true) || it.equals("subject", true) }
        val teacherIdx = headerFields.indexOfFirst { it.contains("教师") || it.contains("老师") || it.equals("teacher", true) || it.equals("instructor", true) }
        val roomIdx = headerFields.indexOfFirst { it.contains("教室") || it.contains("地点") || it.equals("room", true) || it.equals("location", true) }
        val dayIdx = headerFields.indexOfFirst { it.contains("星期") || it.contains("周") || it.equals("day", true) || it.equals("weekday", true) }
        val sectionIdx = headerFields.indexOfFirst { it.contains("节次") || it.contains("节") || it.equals("section", true) || it.equals("period", true) }
        val weekIdx = headerFields.indexOfFirst { it.contains("周次") || it.contains("周") && !it.contains("星期") || it.equals("week", true) || it.equals("weeks", true) }
        val creditIdx = headerFields.indexOfFirst { it.contains("学分") || it.equals("credit", true) || it.equals("credits", true) }

        if (nameIdx < 0) return emptyList()

        return dataLines.mapNotNull { line ->
            val fields = importParser.parseCsvLine(line).map { it.trim().removeSurrounding("\"") }
            if (fields.size <= nameIdx) return@mapNotNull null

            try {
                val name = fields[nameIdx]
                val color = CourseColorPalette.getColorForCourse(name, existingColors)
                    .also { existingColors.add(it) }

                val dayOfWeek = if (dayIdx >= 0 && dayIdx < fields.size) {
                    importParser.parseDayOfWeek(fields[dayIdx])
                } else 1

                val startSection = if (sectionIdx >= 0 && sectionIdx < fields.size) {
                    importParser.parseSection(fields[sectionIdx])
                } else 1

                val weekExpr = if (weekIdx >= 0 && weekIdx < fields.size) fields[weekIdx] else ""

                Course(
                    courseName = name,
                    teacher = if (teacherIdx >= 0 && teacherIdx < fields.size) fields[teacherIdx] else "",
                    classroom = if (roomIdx >= 0 && roomIdx < fields.size) fields[roomIdx] else "",
                    dayOfWeek = dayOfWeek,
                    startSection = startSection,
                    sectionCount = 1,
                    weekExpression = weekExpr,
                    weeks = if (weekExpr.isNotEmpty()) importParser.parseWeeks(weekExpr) else (1..16).toList(),
                    credit = if (creditIdx >= 0 && creditIdx < fields.size) fields[creditIdx].toFloatOrNull() ?: 0f else 0f,
                    color = color,
                    scheduleId = scheduleId
                )
            } catch (_: Exception) { null }
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

    private fun readFileContent(uri: Uri): String = context.contentResolver.openInputStream(uri)?.use { inputStream ->
        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { it.readText() }
    } ?: throw Exception("无法打开文件")

    private fun getFileExtension(uri: Uri): String = uri.path?.let { path ->
        path.lastIndexOf('.').takeIf { it >= 0 }?.let { path.substring(it + 1) } ?: ""
    } ?: ""

    private fun getFileName(uri: Uri): String? {
        var fileName: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        return fileName ?: uri.lastPathSegment
    }

    suspend fun validateImportFile(uri: Uri): ValidationResult = try {
        val content = readFileContent(uri)
        val fileName = getFileName(uri)
        val detection = formatDetector.detectFormat(content, fileName)

        when (detection.format) {
            DetectedFormat.JSON -> {
                val migrationResult = versionMigrator.migrateJson(content, detection.version)
                val courses = parseJsonCourses(migrationResult.migratedData ?: content)
                when {
                    courses.isEmpty() -> ValidationResult(false, "JSON文件中没有找到课程数据")
                    courses.any { it.courseName.isEmpty() || it.dayOfWeek !in 1..7 || it.startSection < 1 } ->
                        ValidationResult(false, "发现无效课程，请检查课程名称、星期和节次")
                    else -> {
                        val versionInfo = detection.version?.let { "（版本: $it）" } ?: ""
                        val externalInfo = if (detection.isExternalSource) " [外部文件]" else ""
                        ValidationResult(true, "JSON文件格式正确$versionInfo$externalInfo，包含${courses.size}门课程")
                    }
                }
            }
            DetectedFormat.ICS -> {
                if (content.contains("BEGIN:VCALENDAR")) {
                    val externalInfo = if (detection.isExternalSource) " [外部日历文件]" else ""
                    ValidationResult(true, "ICS文件格式正确$externalInfo")
                } else ValidationResult(false, "不是有效的ICS文件")
            }
            DetectedFormat.CSV -> {
                val dataLines = content.lines().filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
                val count = if (dataLines.isNotEmpty() && dataLines.first().contains("课程")) dataLines.size - 1 else dataLines.size
                if (count > 0) {
                    val externalInfo = if (detection.isExternalSource) " [外部表格文件]" else ""
                    ValidationResult(true, "CSV文件格式正确$externalInfo，共${count}行数据")
                } else ValidationResult(false, "CSV文件为空")
            }
            DetectedFormat.HTML -> ValidationResult(false, "HTML格式仅支持导出，不支持导入")
            DetectedFormat.UNKNOWN -> ValidationResult(false, "无法识别的文件格式")
        }
    } catch (e: Exception) { ValidationResult(false, "文件验证失败: ${e.message}") }
}
