package com.wind.ggbond.classtime.domain.usecase

import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.CourseAdjustment
import com.wind.ggbond.classtime.data.repository.CourseAdjustmentRepository
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.service.contract.IAlarmScheduler
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.util.DateUtils
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdjustmentUseCase @Inject constructor(
    private val adjustmentRepository: CourseAdjustmentRepository,
    private val courseRepository: CourseRepository,
    private val alarmScheduler: IAlarmScheduler
) {
    
    companion object {
        private const val TAG = "AdjustmentUseCase"
    }

    data class ConflictResult(
        val hasConflict: Boolean,
        val message: String,
        val conflictingCourses: List<Course> = emptyList(),
        val conflictingAdjustments: List<CourseAdjustment> = emptyList()
    )

    suspend fun checkAdjustmentConflict(
        scheduleId: Long,
        weekNumber: Int,
        dayOfWeek: Int,
        startSection: Int,
        sectionCount: Int,
        excludeCourseId: Long? = null
    ): ConflictResult {
        val conflictingAdjustments = adjustmentRepository.checkNewTimeConflict(
            scheduleId = scheduleId,
            weekNumber = weekNumber,
            dayOfWeek = dayOfWeek,
            startSection = startSection,
            sectionCount = sectionCount
        )

        val conflictingCourses = courseRepository.getCoursesInTimeRange(
            scheduleId = scheduleId,
            dayOfWeek = dayOfWeek,
            startSection = startSection,
            endSection = startSection + sectionCount
        ).filter { it.weeks.contains(weekNumber) && it.id != excludeCourseId }

        return if (conflictingAdjustments.isNotEmpty() || conflictingCourses.isNotEmpty()) {
            val message = when {
                conflictingCourses.isNotEmpty() ->
                    "与课程《${conflictingCourses.first().courseName}》时间冲突"
                else ->
                    "与其他调课记录时间冲突"
            }
            ConflictResult(
                hasConflict = true,
                message = message,
                conflictingCourses = conflictingCourses,
                conflictingAdjustments = conflictingAdjustments
            )
        } else {
            ConflictResult(hasConflict = false, message = "")
        }
    }

    fun createAdjustment(
        course: Course,
        originalWeekNumber: Int,
        newWeekNumber: Int,
        newDayOfWeek: Int,
        newStartSection: Int,
        newSectionCount: Int,
        newClassroom: String,
        reason: String
    ): CourseAdjustment {
        return CourseAdjustment(
            originalCourseId = course.id,
            scheduleId = course.scheduleId,
            originalWeekNumber = originalWeekNumber,
            originalDayOfWeek = course.dayOfWeek,
            originalStartSection = course.startSection,
            originalSectionCount = course.sectionCount,
            newWeekNumber = newWeekNumber,
            newDayOfWeek = newDayOfWeek,
            newStartSection = newStartSection,
            newSectionCount = newSectionCount,
            newClassroom = newClassroom,
            reason = reason
        )
    }

    suspend fun saveAdjustment(adjustment: CourseAdjustment): Long {
        val savedId = adjustmentRepository.saveAdjustment(adjustment)
        val savedAdjustment = adjustment.copy(id = savedId)
        alarmScheduler.rescheduleRemindersForAdjustment(savedAdjustment)
        AppLogger.d(TAG, "调课记录已保存: ID=$savedId, 课程ID=${adjustment.originalCourseId}")
        AppLogger.d(TAG, "原时间: 第${adjustment.originalWeekNumber}周 ${DateUtils.getDayOfWeekName(adjustment.originalDayOfWeek)} 第${adjustment.originalStartSection}节")
        AppLogger.d(TAG, "新时间: 第${adjustment.newWeekNumber}周 ${DateUtils.getDayOfWeekName(adjustment.newDayOfWeek)} 第${adjustment.newStartSection}节")
        return savedId
    }

    suspend fun cancelAdjustment(adjustment: CourseAdjustment) {
        adjustmentRepository.cancelAdjustment(adjustment)
    }

    suspend fun cancelAllAdjustments(adjustments: List<CourseAdjustment>) {
        adjustments.forEach { adjustmentRepository.cancelAdjustment(it) }
    }
    
    fun getAdjustmentsBySchedule(scheduleId: Long): Flow<List<CourseAdjustment>> {
        return adjustmentRepository.getAdjustmentsBySchedule(scheduleId)
    }
    
    fun mergeCoursesWithAdjustments(
        courses: List<Course>,
        adjustments: List<CourseAdjustment>,
        weekNumber: Int
    ): Map<Int, List<Course>> {
        AppLogger.d(TAG, "=== 开始合并课程和调课记录 ===")
        AppLogger.d(TAG, "周次=$weekNumber, 原始课程数=${courses.size}, 调课记录数=${adjustments.size}")
        
        val displayCoursesMap = mutableMapOf<Int, MutableList<Course>>()
        val adjustedCourseKeys = mutableSetOf<String>()
        
        adjustments.forEach { adjustment ->
            AppLogger.d(TAG, "处理调课记录: ID=${adjustment.id}, 课程ID=${adjustment.originalCourseId}")
            AppLogger.d(TAG, "  原始: 第${adjustment.originalWeekNumber}周 ${DateUtils.getDayOfWeekName(adjustment.originalDayOfWeek)} 第${adjustment.originalStartSection}节")
            AppLogger.d(TAG, "  新的: 第${adjustment.newWeekNumber}周 ${DateUtils.getDayOfWeekName(adjustment.newDayOfWeek)} 第${adjustment.newStartSection}节")
            
            if (adjustment.originalWeekNumber == weekNumber) {
                val key = "${adjustment.originalCourseId}_${weekNumber}_${adjustment.originalDayOfWeek}_${adjustment.originalStartSection}"
                adjustedCourseKeys.add(key)
                AppLogger.d(TAG, "  → 标记原时间不显示: $key")
                
                if (adjustment.newWeekNumber == weekNumber) {
                    val course = courses.find { it.id == adjustment.originalCourseId }
                    if (course != null) {
                        val adjustedCourse = course.copy(
                            dayOfWeek = adjustment.newDayOfWeek,
                            startSection = adjustment.newStartSection,
                            sectionCount = adjustment.newSectionCount,
                            weeks = if (adjustment.newWeekNumber in course.weeks) course.weeks else course.weeks + adjustment.newWeekNumber,
                            classroom = if (adjustment.newClassroom.isNotEmpty()) adjustment.newClassroom else course.classroom
                        )
                        displayCoursesMap.getOrPut(adjustment.newDayOfWeek) { mutableListOf() }
                            .add(adjustedCourse)
                        AppLogger.d(TAG, "  ✓ 新时间显示: ${course.courseName} 在${DateUtils.getDayOfWeekName(adjustment.newDayOfWeek)}第${adjustment.newStartSection}节")
                    } else {
                        AppLogger.w(TAG, "  ⚠️ 未找到课程ID=${adjustment.originalCourseId}")
                    }
                }
            } else if (adjustment.newWeekNumber == weekNumber) {
                val course = courses.find { it.id == adjustment.originalCourseId }
                if (course != null) {
                    val adjustedCourse = course.copy(
                        dayOfWeek = adjustment.newDayOfWeek,
                        startSection = adjustment.newStartSection,
                        sectionCount = adjustment.newSectionCount,
                        weeks = if (adjustment.newWeekNumber in course.weeks) course.weeks else course.weeks + adjustment.newWeekNumber,
                        classroom = if (adjustment.newClassroom.isNotEmpty()) adjustment.newClassroom else course.classroom
                    )
                    displayCoursesMap.getOrPut(adjustment.newDayOfWeek) { mutableListOf() }
                        .add(adjustedCourse)
                    AppLogger.d(TAG, "  ✓ 其他周调入本周: ${course.courseName} (从第${adjustment.originalWeekNumber}周)")
                } else {
                    AppLogger.w(TAG, "  ⚠️ 未找到课程ID=${adjustment.originalCourseId}")
                }
            }
        }
        
        AppLogger.d(TAG, "--- 添加正常课程 ---")
        courses.forEach { course ->
            if (weekNumber in course.weeks) {
                val key = "${course.id}_${weekNumber}_${course.dayOfWeek}_${course.startSection}"
                if (!adjustedCourseKeys.contains(key)) {
                    displayCoursesMap.getOrPut(course.dayOfWeek) { mutableListOf() }
                        .add(course)
                    AppLogger.d(TAG, "  ✓ 添加正常课程: ${course.courseName} ${DateUtils.getDayOfWeekName(course.dayOfWeek)}第${course.startSection}节")
                } else {
                    AppLogger.d(TAG, "  ✗ 跳过被调走课程: ${course.courseName} (key=$key)")
                }
            }
        }
        
        AppLogger.d(TAG, "--- 合并结果 ---")
        displayCoursesMap.forEach { (day, courseList) ->
            AppLogger.d(TAG, "第${weekNumber}周 ${DateUtils.getDayOfWeekName(day)}: ${courseList.size}门课")
            courseList.forEach { course ->
                AppLogger.d(TAG, "  - ${course.courseName} 第${course.startSection}节")
            }
        }
        AppLogger.d(TAG, "=== 合并完成 ===")
        
        return (1..7).associateWith { dayOfWeek ->
            displayCoursesMap[dayOfWeek]?.sortedBy { it.startSection } ?: emptyList()
        }
    }
    
    fun buildAdjustmentMap(adjustments: List<CourseAdjustment>): Map<String, CourseAdjustment> {
        val map = mutableMapOf<String, CourseAdjustment>()
        adjustments.forEach { adj ->
            val key = "${adj.originalCourseId}_${adj.newWeekNumber}_${adj.newDayOfWeek}_${adj.newStartSection}"
            map[key] = adj
        }
        return map
    }
}