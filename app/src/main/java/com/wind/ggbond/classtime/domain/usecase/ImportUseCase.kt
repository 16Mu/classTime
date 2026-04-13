package com.wind.ggbond.classtime.domain.usecase

import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.service.helper.FormatDetector
import com.wind.ggbond.classtime.service.helper.ImportParser
import com.wind.ggbond.classtime.service.helper.ImportValidator
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.util.CourseColorPalette
import com.wind.ggbond.classtime.util.WeekParser
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportUseCase @Inject constructor(
    private val formatDetector: FormatDetector,
    private val importParser: ImportParser,
    private val importValidator: ImportValidator
) {

    companion object {
        private const val TAG = "ImportUseCase"
    }

    private val gson = Gson()

    data class FormatDetectionResult(
        val format: FormatDetector.DetectedFormat,
        val confidence: Float,
        val version: String?,
        val isExternalSource: Boolean
    )

    data class ParsedCourseResult(
        val courses: List<ParsedCourse>,
        val errors: List<String> = emptyList()
    )

    data class CourseConversionResult(
        val courses: List<Course>,
        val validCount: Int,
        val skippedCount: Int,
        val warnings: List<String>,
        val errors: List<String>
    )

    fun detectFormat(content: String, fileName: String? = null): FormatDetectionResult {
        val result = formatDetector.detectFormat(content, fileName)
        return FormatDetectionResult(
            format = result.format,
            confidence = result.confidence,
            version = result.version,
            isExternalSource = result.isExternalSource
        )
    }

    fun parseJsonToParsedCourses(jsonContent: String): ParsedCourseResult {
        return try {
            val jsonObject = gson.fromJson(jsonContent, JsonObject::class.java)
            val courses: List<ParsedCourse> = if (jsonObject.has("courses")) {
                val coursesArray = jsonObject.getAsJsonArray("courses")
                val fullCourses: List<Course> = gson.fromJson(
                    coursesArray,
                    object : TypeToken<List<Course>>() {}.type
                )
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
                        credit = course.credit,
                        courseCode = course.courseCode
                    )
                }
            } else {
                try {
                    val type = object : TypeToken<List<ParsedCourse>>() {}.type
                    gson.fromJson<List<ParsedCourse>>(jsonContent, type) ?: emptyList()
                } catch (e: Exception) {
                    AppLogger.e(TAG, "无法解析JSON为任何已知格式", e)
                    emptyList()
                }
            }

            val validCourses = courses.filter { course ->
                course.courseName.isNotBlank() &&
                course.dayOfWeek in 1..7 &&
                course.startSection > 0 &&
                course.sectionCount > 0 &&
                course.weeks.isNotEmpty()
            }

            AppLogger.d(TAG, "JSON解析结果：总数${courses.size}，有效${validCourses.size}")
            ParsedCourseResult(courses = validCourses)
        } catch (e: Exception) {
            AppLogger.e(TAG, "JSON解析失败", e)
            ParsedCourseResult(courses = emptyList(), errors = listOf("JSON解析失败：${e.message}"))
        }
    }

    fun convertParsedCoursesToCourses(
        parsedCourses: List<ParsedCourse>,
        scheduleId: Long,
        existingColors: MutableList<String> = mutableListOf()
    ): CourseConversionResult {
        val courseNames = parsedCourses.map { it.courseName }.distinct()
        val colorMapping = CourseColorPalette.assignColorsForCourses(courseNames)

        val courses = parsedCourses.map { parsed ->
            val weeks = if (parsed.weeks.isNotEmpty()) {
                parsed.weeks
            } else {
                WeekParser.parseWeekExpression(parsed.weekExpression)
            }

            val color = colorMapping[parsed.courseName]
                ?: CourseColorPalette.getColorForCourse(parsed.courseName, existingColors)
                    .also { existingColors.add(it) }

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
                color = color,
                scheduleId = scheduleId,
                credit = parsed.credit,
                courseCode = parsed.courseCode,
                reminderEnabled = false
            )
        }

        val validation = importValidator.validateCourses(courses)

        return CourseConversionResult(
            courses = validation.validCourses,
            validCount = validation.validCourses.size,
            skippedCount = validation.skippedCourses.size,
            warnings = validation.warnings,
            errors = validation.errors
        )
    }

    fun parseWeekExpression(expression: String): List<Int> {
        return if (expression.isNotEmpty()) {
            WeekParser.parseWeekExpression(expression)
        } else {
            emptyList()
        }
    }

    fun resolveWeeksForCourse(parsed: ParsedCourse): Pair<List<Int>, String> {
        val weeks = if (parsed.weeks.isNotEmpty()) {
            parsed.weeks
        } else {
            importParser.parseWeeks(parsed.weekExpression).takeIf { it.isNotEmpty() }
                ?: WeekParser.parseWeekExpression(parsed.weekExpression)
        }
        val finalWeeks = weeks.takeIf { it.isNotEmpty() } ?: emptyList()
        val weekExpr = parsed.weekExpression.takeIf { it.isNotEmpty() }
            ?: if (finalWeeks.isNotEmpty()) WeekParser.formatWeekList(finalWeeks) else ""
        return Pair(finalWeeks, weekExpr)
    }
}
