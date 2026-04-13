package com.wind.ggbond.classtime.service

import android.content.Context
import android.net.Uri
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.repository.ClassTimeRepository
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.domain.usecase.ImportUseCase
import com.wind.ggbond.classtime.service.helper.CsvParser
import com.wind.ggbond.classtime.service.helper.FormatDetector
import com.wind.ggbond.classtime.service.helper.FormatDetector.DetectedFormat
import com.wind.ggbond.classtime.service.helper.ImportParser
import com.wind.ggbond.classtime.service.helper.ImportRouteDecision
import com.wind.ggbond.classtime.service.helper.ImportRouter
import com.wind.ggbond.classtime.service.helper.ImportValidator
import com.wind.ggbond.classtime.service.helper.TableImportConfig
import com.wind.ggbond.classtime.service.helper.VersionMigrator
import com.wind.ggbond.classtime.data.model.ParsedCourse
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
    private val importValidator: ImportValidator,
    private val importRouter: ImportRouter,
    private val csvParser: CsvParser,
    private val importUseCase: ImportUseCase
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
            val detection = importUseCase.detectFormat(content, fileName)

            AppLogger.d(TAG, "格式检测: ${detection.format}, 版本: ${detection.version}, 外部来源: ${detection.isExternalSource}")

            when (detection.format) {
                DetectedFormat.JSON -> importFromJson(content, scheduleId, detection.version, detection.isExternalSource)
                DetectedFormat.ICS -> importFromIcs(content, scheduleId, detection.isExternalSource)
                DetectedFormat.CSV -> importCsvFromUri(uri, scheduleId)
                DetectedFormat.HTML -> ImportResult(success = false, errorMessage = "HTML格式仅支持导出，不支持导入")
                DetectedFormat.EXCEL -> importExcelFromUri(uri, scheduleId)
                DetectedFormat.UNKNOWN -> ImportResult(success = false, errorMessage = "无法识别的文件格式，请选择 JSON/ICS/CSV/Excel 文件")
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

            val existingCourses = courseRepository.getAllCoursesBySchedule(scheduleId).first()

            val preparedCourses = validation.validCourses.map { prepareCourseWithDefaults(it, scheduleId) }
            val dedupResult = deduplicateAndPrepare(existingCourses, preparedCourses, checkWeeksOverlap = false)

            if (dedupResult.toInsert.isNotEmpty()) courseRepository.insertCourses(dedupResult.toInsert)

            ImportResult(
                success = true,
                importedCount = dedupResult.importedCount,
                duplicateCount = dedupResult.duplicateCount,
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

            val dedupResult = deduplicateAndPrepare(existingCourses, validation.validCourses, checkWeeksOverlap = true)

            if (dedupResult.toInsert.isNotEmpty()) courseRepository.insertCourses(dedupResult.toInsert)

            ImportResult(
                success = true,
                importedCount = dedupResult.importedCount,
                duplicateCount = dedupResult.duplicateCount,
                skippedCount = validation.skippedCourses.size,
                warnings = validation.warnings
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "ICS导入失败", e)
            ImportResult(success = false, errorMessage = "ICS解析失败: ${e.message}")
        }
    }

    suspend fun importCsvFromUri(
        uri: Uri,
        scheduleId: Long,
        forceTemplate: String? = null,
        forceFieldMapping: Map<String, Int>? = null,
        forceHeaderRow: Int? = null
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val routeResult = importRouter.routeCsvImport(
                context, uri,
                forceTemplate, forceFieldMapping, forceHeaderRow
            )

            if (routeResult.courses.isEmpty()) {
                return@withContext ImportResult(
                    success = false,
                    errorMessage = "未能从CSV文件中解析出课程信息",
                    warnings = routeResult.warnings
                )
            }

            val existingCourses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            val existingColors = existingCourses.map { it.color }.toMutableList()

            val courses = routeResult.courses.map { parsed ->
                val color = CourseColorPalette.getColorForCourse(parsed.courseName, existingColors)
                    .also { existingColors.add(it) }
                val parsedWeeks = parsed.weeks.takeIf { it.isNotEmpty() }
                    ?: importParser.parseWeeks(parsed.weekExpression)
                Course(
                    courseName = parsed.courseName,
                    teacher = parsed.teacher,
                    classroom = parsed.classroom,
                    dayOfWeek = parsed.dayOfWeek,
                    startSection = parsed.startSection,
                    sectionCount = parsed.sectionCount,
                    weekExpression = parsed.weekExpression,
                    weeks = parsedWeeks,
                    credit = parsed.credit,
                    courseCode = parsed.courseCode,
                    color = color,
                    scheduleId = scheduleId,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            }

            val validation = importValidator.validateCourses(courses)

            val dedupResult = deduplicateAndPrepare(existingCourses, validation.validCourses, checkWeeksOverlap = false)

            if (dedupResult.toInsert.isNotEmpty()) courseRepository.insertCourses(dedupResult.toInsert)

            ImportResult(
                success = true,
                importedCount = dedupResult.importedCount,
                duplicateCount = dedupResult.duplicateCount,
                skippedCount = validation.skippedCourses.size,
                warnings = validation.warnings + routeResult.warnings,
                detectedFormat = "CSV",
                isExternalSource = true
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "CSV导入失败", e)
            ImportResult(success = false, errorMessage = "CSV导入失败: ${e.message}")
        }
    }

    private fun parseJsonCourses(content: String): List<Course> {
        val result = importUseCase.parseJsonToParsedCourses(content)
        return result.courses.map { parsed ->
            val (weeks, weekExpr) = importUseCase.resolveWeeksForCourse(parsed)
            Course(
                courseName = parsed.courseName,
                teacher = parsed.teacher,
                classroom = parsed.classroom,
                dayOfWeek = parsed.dayOfWeek,
                startSection = parsed.startSection,
                sectionCount = parsed.sectionCount,
                weekExpression = weekExpr,
                weeks = weeks,
                credit = parsed.credit,
                courseCode = parsed.courseCode,
                scheduleId = 0,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    private fun readFileContent(uri: Uri): String = context.contentResolver.openInputStream(uri)?.use { inputStream ->
        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { it.readText() }
    } ?: throw Exception("无法打开文件")

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
        val detection = importUseCase.detectFormat(content, fileName)

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
            DetectedFormat.EXCEL -> ValidationResult(true, "Excel文件格式正确，将使用智能识别导入")
            DetectedFormat.UNKNOWN -> ValidationResult(false, "无法识别的文件格式")
        }
    } catch (e: Exception) { ValidationResult(false, "文件验证失败: ${e.message}") }

    data class ImportPreviewResult(
        val success: Boolean,
        val courses: List<ParsedCourse> = emptyList(),
        val decision: ImportRouteDecision? = null,
        val preview: List<List<String>> = emptyList(),
        val sheetNames: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
        val errorMessage: String? = null,
        val detectedEncoding: String? = null
    )

    suspend fun importExcelFromUri(
        uri: Uri,
        scheduleId: Long,
        sheetIndex: Int = 0,
        forceTemplate: String? = null,
        forceFieldMapping: Map<String, Int>? = null,
        forceHeaderRow: Int? = null
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val routeResult = importRouter.routeExcelImport(
                context, uri, sheetIndex,
                forceTemplate, forceFieldMapping, forceHeaderRow
            )

            if (routeResult.courses.isEmpty()) {
                return@withContext ImportResult(
                    success = false,
                    errorMessage = "未能从Excel文件中解析出课程信息",
                    warnings = routeResult.warnings
                )
            }

            val existingCourses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            val existingColors = existingCourses.map { it.color }.toMutableList()

            val courses = routeResult.courses.map { parsed ->
                val color = CourseColorPalette.getColorForCourse(parsed.courseName, existingColors)
                    .also { existingColors.add(it) }
                val parsedWeeks = parsed.weeks.takeIf { it.isNotEmpty() }
                    ?: importParser.parseWeeks(parsed.weekExpression)
                Course(
                    courseName = parsed.courseName,
                    teacher = parsed.teacher,
                    classroom = parsed.classroom,
                    dayOfWeek = parsed.dayOfWeek,
                    startSection = parsed.startSection,
                    sectionCount = parsed.sectionCount,
                    weekExpression = parsed.weekExpression,
                    weeks = parsedWeeks,
                    credit = parsed.credit,
                    courseCode = parsed.courseCode,
                    color = color,
                    scheduleId = scheduleId,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            }

            val validation = importValidator.validateCourses(courses)

            val dedupResult = deduplicateAndPrepare(existingCourses, validation.validCourses, checkWeeksOverlap = false)

            if (dedupResult.toInsert.isNotEmpty()) courseRepository.insertCourses(dedupResult.toInsert)

            ImportResult(
                success = true,
                importedCount = dedupResult.importedCount,
                duplicateCount = dedupResult.duplicateCount,
                skippedCount = validation.skippedCourses.size,
                warnings = validation.warnings + routeResult.warnings,
                detectedFormat = "EXCEL",
                isExternalSource = true
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Excel导入失败", e)
            ImportResult(success = false, errorMessage = "Excel导入失败: ${e.message}")
        }
    }

    fun previewExcelImport(
        uri: Uri,
        sheetIndex: Int = 0
    ): ImportPreviewResult {
        return try {
            val routeResult = importRouter.routeExcelImport(context, uri, sheetIndex)
            ImportPreviewResult(
                success = true,
                courses = routeResult.courses,
                decision = routeResult.decision,
                preview = routeResult.preview,
                sheetNames = routeResult.sheetNames,
                warnings = routeResult.warnings
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Excel预览失败", e)
            ImportPreviewResult(
                success = false,
                errorMessage = "Excel预览失败: ${e.message}"
            )
        }
    }

    fun previewCsvImport(
        uri: Uri
    ): ImportPreviewResult {
        return try {
            val routeResult = importRouter.routeCsvImport(context, uri)
            ImportPreviewResult(
                success = true,
                courses = routeResult.courses,
                decision = routeResult.decision,
                preview = routeResult.preview,
                warnings = routeResult.warnings,
                detectedEncoding = routeResult.detectedEncoding
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "CSV预览失败", e)
            ImportPreviewResult(
                success = false,
                errorMessage = "CSV预览失败: ${e.message}"
            )
        }
    }

    fun previewImport(
        uri: Uri,
        sheetIndex: Int = 0
    ): ImportPreviewResult {
        return try {
            val routeResult = importRouter.routeImport(context, uri, sheetIndex)
            ImportPreviewResult(
                success = true,
                courses = routeResult.courses,
                decision = routeResult.decision,
                preview = routeResult.preview,
                sheetNames = routeResult.sheetNames,
                warnings = routeResult.warnings,
                detectedEncoding = routeResult.detectedEncoding
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "导入预览失败", e)
            ImportPreviewResult(
                success = false,
                errorMessage = "导入预览失败: ${e.message}"
            )
        }
    }

    private data class DeduplicateResult(
        val toInsert: List<Course>,
        val importedCount: Int,
        val duplicateCount: Int
    )

    private fun deduplicateAndPrepare(
        existingCourses: List<Course>,
        validCourses: List<Course>,
        checkWeeksOverlap: Boolean = true
    ): DeduplicateResult {
        val existingColors = existingCourses.map { it.color }.toMutableList()
        var importedCount = 0
        var duplicateCount = 0
        val toInsert = mutableListOf<Course>()
        validCourses.forEach { course ->
            val isDuplicate = if (checkWeeksOverlap) {
                existingCourses.any {
                    it.courseName == course.courseName &&
                    it.dayOfWeek == course.dayOfWeek &&
                    it.startSection == course.startSection &&
                    it.weeks.intersect(course.weeks.toSet()).isNotEmpty()
                }
            } else {
                existingCourses.any {
                    it.courseName == course.courseName &&
                    it.dayOfWeek == course.dayOfWeek &&
                    it.startSection == course.startSection
                }
            }
            if (isDuplicate) {
                duplicateCount++
            } else {
                val color = CourseColorPalette.getColorForCourse(course.courseName, existingColors)
                    .also { existingColors.add(it) }
                toInsert.add(course.copy(color = color))
                importedCount++
            }
        }
        return DeduplicateResult(toInsert, importedCount, duplicateCount)
    }

    private fun prepareCourseWithDefaults(course: Course, scheduleId: Long): Course {
        val weeks = course.weeks.takeIf { it.isNotEmpty() } ?: run {
            if (course.weekExpression.isNotEmpty()) importParser.parseWeeks(course.weekExpression) else emptyList()
        }
        val weekExpr = course.weekExpression.takeIf { it.isNotEmpty() } ?: run {
            if (course.weeks.isNotEmpty()) "${course.weeks.first()}-${course.weeks.last()}周" else ""
        }
        return course.copy(
            id = 0,
            scheduleId = scheduleId,
            weeks = weeks,
            weekExpression = weekExpr,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
}
