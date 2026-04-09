package com.wind.ggbond.classtime.domain.usecase

import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.util.CourseColorPalette
import com.wind.ggbond.classtime.util.TextCourseParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

@Singleton
class ClipboardUseCase @Inject constructor(
    private val courseRepository: CourseRepository,
    private val textCourseParser: TextCourseParser
) {
    
    companion object {
        private const val TAG = "ClipboardUseCase"
    }
    
    private val _clipboard = MutableStateFlow<Pair<Course, Int>?>(null)
    val clipboard: StateFlow<Pair<Course, Int>?> = _clipboard.asStateFlow()
    
    private val _showClipboardImport = MutableStateFlow(false)
    val showClipboardImport: StateFlow<Boolean> = _showClipboardImport.asStateFlow()
    
    private val _clipboardImportResult = MutableStateFlow<String?>(null)
    val clipboardImportResult: StateFlow<String?> = _clipboardImportResult.asStateFlow()
    
    fun copyCourse(course: Course, sourceWeekNumber: Int) {
        _clipboard.value = Pair(course, sourceWeekNumber)
        AppLogger.d(TAG, "✅ 课程已复制到剪贴板: ${course.courseName} (周$sourceWeekNumber)")
    }
    
    suspend fun pasteCourse(
        targetWeekNumber: Int,
        dayOfWeek: Int,
        startSection: Int
    ): Boolean {
        val (copiedCourse, _) = _clipboard.value ?: return false
        
        try {
            val conflicts = courseRepository.detectConflictWithWeeks(
                scheduleId = copiedCourse.scheduleId,
                dayOfWeek = dayOfWeek,
                startSection = startSection,
                sectionCount = copiedCourse.sectionCount,
                weeks = listOf(targetWeekNumber)
            )
            
            if (conflicts.isNotEmpty()) {
                AppLogger.w(TAG, "粘贴失败：目标时间段已有课程")
                return false
            }
            
            val newCourse = copiedCourse.copy(
                id = 0,
                weeks = listOf(targetWeekNumber),
                dayOfWeek = dayOfWeek,
                startSection = startSection,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            
            courseRepository.insertCourse(newCourse)
            
            AppLogger.d(TAG, "课程已粘贴到周$targetWeekNumber 星期$dayOfWeek 第$startSection: ${newCourse.courseName}")
            return true
        } catch (e: Exception) {
            AppLogger.e(TAG, "粘贴课程失败", e)
            return false
        }
    }
    
    suspend fun importFromClipboard(
        clipboardText: String,
        targetWeekNumber: Int,
        targetDayOfWeek: Int,
        targetStartSection: Int,
        scheduleId: Long
    ): String {
        if (clipboardText.isBlank()) {
            _clipboardImportResult.value = "剪贴板内容为空"
            return "剪贴板内容为空"
        }

        val parsedCourses = textCourseParser.parse(clipboardText)
        if (parsedCourses.isEmpty()) {
            _clipboardImportResult.value = "未能识别课程信息"
            return "未能识别课程信息"
        }

        var successCount = 0
        var errorMessages = mutableListOf<String>()

        kotlinx.coroutines.coroutineScope {
            parsedCourses.forEach { parsed ->
            try {
                val weeks = if (parsed.weeks.isNotEmpty()) parsed.weeks else listOf(targetWeekNumber)
                
                val newCourse = com.wind.ggbond.classtime.data.local.entity.Course(
                    id = 0,
                    scheduleId = scheduleId,
                    courseName = parsed.courseName,
                    teacher = parsed.teacher,
                    classroom = parsed.classroom,
                    dayOfWeek = targetDayOfWeek,
                    startSection = targetStartSection,
                    sectionCount = if (parsed.endSection > 0) parsed.endSection else 1,
                    weeks = weeks,
                    color = CourseColorPalette.getColorForCourse(parsed.courseName, emptyList()),
                    credit = parsed.credit,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                val sectionCount = if (parsed.endSection > 0) parsed.endSection else 1

                val conflicts = courseRepository.detectConflictWithWeeks(
                    scheduleId = scheduleId,
                    dayOfWeek = targetDayOfWeek,
                    startSection = targetStartSection,
                    sectionCount = sectionCount,
                    weeks = weeks
                )

                if (conflicts.isNotEmpty()) {
                    errorMessages.add("${parsed.courseName}: 时间冲突")
                    return@forEach
                }

                courseRepository.insertCourse(newCourse)
                successCount++
                
                AppLogger.d(TAG, "✅ 导入课程成功: ${parsed.courseName}")
            } catch (e: Exception) {
                AppLogger.e(TAG, "导入课程失败: ${parsed.courseName}", e)
                errorMessages.add("${parsed.courseName}: ${e.message}")
            }
        }
        }

        val result = if (successCount > 0) {
            "成功导入 $successCount 门课程" + 
            if (errorMessages.isNotEmpty()) {
                "\n失败: ${errorMessages.joinToString(", ")}"
            } else ""
        } else {
            "导入失败: ${errorMessages.joinToString(", ")}"
        }

        _clipboardImportResult.value = result
        return result
    }
    
    fun clearClipboard() {
        _clipboard.value = null
    }
    
    fun showClipboardImport() {
        _showClipboardImport.value = true
    }
    
    fun hideClipboardImport() {
        _showClipboardImport.value = false
    }
    
    fun clearClipboardImportResult() {
        _clipboardImportResult.value = null
    }
}