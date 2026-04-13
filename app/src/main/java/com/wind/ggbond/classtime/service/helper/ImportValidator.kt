package com.wind.ggbond.classtime.service.helper

import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportValidator @Inject constructor() {

    companion object {
        private const val TAG = "ImportValidator"
    }

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>,
        val warnings: List<String>,
        val validCourses: List<Course>,
        val skippedCourses: List<Pair<Course, String>>
    )

    data class FieldValidation(
        val fieldName: String,
        val isValid: Boolean,
        val message: String
    )

    fun validateCourses(courses: List<Course>): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val validCourses = mutableListOf<Course>()
        val skippedCourses = mutableListOf<Pair<Course, String>>()

        if (courses.isEmpty()) {
            return ValidationResult(
                isValid = false,
                errors = listOf("没有找到课程数据"),
                warnings = emptyList(),
                validCourses = emptyList(),
                skippedCourses = emptyList()
            )
        }

        courses.forEach { course ->
            val fieldValidations = validateCourseFields(course)
            val criticalErrors = fieldValidations.filter { !it.isValid }

            if (criticalErrors.isNotEmpty()) {
                val errorMsg = criticalErrors.joinToString("; ") { it.message }
                skippedCourses.add(course to errorMsg)
                criticalErrors.filter { it.message.contains("必填") }.forEach {
                    errors.add("${course.courseName}: ${it.message}")
                }
            } else {
                val warningMessages = fieldValidations.filter { it.isValid && it.message.isNotEmpty() }
                    .map { "${course.courseName}: ${it.message}" }
                warnings.addAll(warningMessages)
                validCourses.add(course)
            }
        }

        return ValidationResult(
            isValid = validCourses.isNotEmpty(),
            errors = errors,
            warnings = warnings,
            validCourses = validCourses,
            skippedCourses = skippedCourses
        )
    }

    fun validateCourseFields(course: Course): List<FieldValidation> {
        val validations = mutableListOf<FieldValidation>()

        validations.add(validateCourseName(course.courseName))
        validations.add(validateDayOfWeek(course.dayOfWeek))
        validations.add(validateStartSection(course.startSection))
        validations.add(validateSectionCount(course.sectionCount))
        validations.add(validateWeeks(course.weeks, course.weekExpression))
        validations.add(validateCredit(course.credit))

        return validations
    }

    private fun validateCourseName(name: String): FieldValidation {
        return when {
            name.isBlank() -> FieldValidation("courseName", false, "课程名称为必填字段")
            name.length > 100 -> FieldValidation("courseName", false, "课程名称过长（超过100字符）")
            else -> FieldValidation("courseName", true, "")
        }
    }

    private fun validateDayOfWeek(dayOfWeek: Int): FieldValidation {
        return when {
            dayOfWeek !in 1..7 -> FieldValidation("dayOfWeek", false, "星期必须在1-7之间，当前值: $dayOfWeek")
            else -> FieldValidation("dayOfWeek", true, "")
        }
    }

    private fun validateStartSection(startSection: Int): FieldValidation {
        return when {
            startSection < 1 -> FieldValidation("startSection", false, "开始节次必须大于0，当前值: $startSection")
            startSection > 16 -> FieldValidation("startSection", true, "开始节次超过16，可能不兼容部分配置")
            else -> FieldValidation("startSection", true, "")
        }
    }

    private fun validateSectionCount(sectionCount: Int): FieldValidation {
        return when {
            sectionCount < 1 -> FieldValidation("sectionCount", false, "持续节数必须大于0，当前值: $sectionCount")
            sectionCount > 12 -> FieldValidation("sectionCount", true, "持续节数超过12，可能不兼容部分配置")
            else -> FieldValidation("sectionCount", true, "")
        }
    }

    private fun validateWeeks(weeks: List<Int>, weekExpression: String): FieldValidation {
        return when {
            weeks.isEmpty() && weekExpression.isBlank() ->
                FieldValidation("weeks", false, "周次信息为必填字段，未能自动识别")
            weeks.any { it < 1 || it > 30 } ->
                FieldValidation("weeks", false, "周次超出有效范围(1-30)")
            else -> FieldValidation("weeks", true, "")
        }
    }

    private fun validateCredit(credit: Float): FieldValidation {
        return when {
            credit < 0 -> FieldValidation("credit", false, "学分不能为负数")
            credit > 20 -> FieldValidation("credit", true, "学分超过20，请确认是否正确")
            else -> FieldValidation("credit", true, "")
        }
    }

    fun detectConflicts(
        newCourses: List<Course>,
        existingCourses: List<Course>
    ): List<CourseConflict> {
        val conflicts = mutableListOf<CourseConflict>()

        newCourses.forEach { newCourse ->
            existingCourses.forEach { existing ->
                if (newCourse.dayOfWeek == existing.dayOfWeek &&
                    newCourse.startSection < existing.startSection + existing.sectionCount &&
                    existing.startSection < newCourse.startSection + newCourse.sectionCount) {
                    val overlapWeeks = newCourse.weeks.intersect(existing.weeks.toSet())
                    if (overlapWeeks.isNotEmpty()) {
                        conflicts.add(CourseConflict(
                            newCourse = newCourse,
                            existingCourse = existing,
                            overlapWeeks = overlapWeeks.sorted(),
                            conflictType = ConflictType.TIME_OVERLAP
                        ))
                    }
                }
            }
        }

        return conflicts
    }

    data class CourseConflict(
        val newCourse: Course,
        val existingCourse: Course,
        val overlapWeeks: List<Int>,
        val conflictType: ConflictType
    )

    enum class ConflictType {
        TIME_OVERLAP,
        DUPLICATE_COURSE
    }

    enum class ConflictResolution {
        SKIP,
        REPLACE,
        KEEP_BOTH
    }
}
