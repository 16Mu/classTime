package com.wind.ggbond.classtime.domain.usecase

import android.util.Log
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.CourseAdjustment
import com.wind.ggbond.classtime.data.repository.CourseAdjustmentRepository
import com.wind.ggbond.classtime.util.DateUtils
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdjustmentUseCase @Inject constructor(
    private val adjustmentRepository: CourseAdjustmentRepository
) {
    
    companion object {
        private const val TAG = "AdjustmentUseCase"
    }
    
    fun getAdjustmentsBySchedule(scheduleId: Long): Flow<List<CourseAdjustment>> {
        return adjustmentRepository.getAdjustmentsBySchedule(scheduleId)
    }
    
    fun mergeCoursesWithAdjustments(
        courses: List<Course>,
        adjustments: List<CourseAdjustment>,
        weekNumber: Int
    ): Map<Int, List<Course>> {
        Log.d(TAG, "=== 开始合并课程和调课记录 ===")
        Log.d(TAG, "周次=$weekNumber, 原始课程数=${courses.size}, 调课记录数=${adjustments.size}")
        
        val displayCoursesMap = mutableMapOf<Int, MutableList<Course>>()
        val adjustedCourseKeys = mutableSetOf<String>()
        
        adjustments.forEach { adjustment ->
            Log.d(TAG, "处理调课记录: ID=${adjustment.id}, 课程ID=${adjustment.originalCourseId}")
            Log.d(TAG, "  原始: 第${adjustment.originalWeekNumber}周 ${DateUtils.getDayOfWeekName(adjustment.originalDayOfWeek)} 第${adjustment.originalStartSection}节")
            Log.d(TAG, "  新的: 第${adjustment.newWeekNumber}周 ${DateUtils.getDayOfWeekName(adjustment.newDayOfWeek)} 第${adjustment.newStartSection}节")
            
            if (adjustment.originalWeekNumber == weekNumber) {
                val key = "${adjustment.originalCourseId}_${weekNumber}_${adjustment.originalDayOfWeek}_${adjustment.originalStartSection}"
                adjustedCourseKeys.add(key)
                Log.d(TAG, "  → 标记原时间不显示: $key")
                
                if (adjustment.newWeekNumber == weekNumber) {
                    val course = courses.find { it.id == adjustment.originalCourseId }
                    if (course != null) {
                        val adjustedCourse = course.copy(
                            dayOfWeek = adjustment.newDayOfWeek,
                            startSection = adjustment.newStartSection,
                            sectionCount = adjustment.newSectionCount,
                            weeks = if (adjustment.newWeekNumber in course.weeks) course.weeks else course.weeks + adjustment.newWeekNumber
                        )
                        displayCoursesMap.getOrPut(adjustment.newDayOfWeek) { mutableListOf() }
                            .add(adjustedCourse)
                        Log.d(TAG, "  ✓ 新时间显示: ${course.courseName} 在${DateUtils.getDayOfWeekName(adjustment.newDayOfWeek)}第${adjustment.newStartSection}节")
                    } else {
                        Log.w(TAG, "  ⚠️ 未找到课程ID=${adjustment.originalCourseId}")
                    }
                }
            } else if (adjustment.newWeekNumber == weekNumber) {
                val course = courses.find { it.id == adjustment.originalCourseId }
                if (course != null) {
                    val adjustedCourse = course.copy(
                        dayOfWeek = adjustment.newDayOfWeek,
                        startSection = adjustment.newStartSection,
                        sectionCount = adjustment.newSectionCount,
                        weeks = if (adjustment.newWeekNumber in course.weeks) course.weeks else course.weeks + adjustment.newWeekNumber
                    )
                    displayCoursesMap.getOrPut(adjustment.newDayOfWeek) { mutableListOf() }
                        .add(adjustedCourse)
                    Log.d(TAG, "  ✓ 其他周调入本周: ${course.courseName} (从第${adjustment.originalWeekNumber}周)")
                } else {
                    Log.w(TAG, "  ⚠️ 未找到课程ID=${adjustment.originalCourseId}")
                }
            }
        }
        
        Log.d(TAG, "--- 添加正常课程 ---")
        courses.forEach { course ->
            if (weekNumber in course.weeks) {
                val key = "${course.id}_${weekNumber}_${course.dayOfWeek}_${course.startSection}"
                if (!adjustedCourseKeys.contains(key)) {
                    displayCoursesMap.getOrPut(course.dayOfWeek) { mutableListOf() }
                        .add(course)
                    Log.d(TAG, "  ✓ 添加正常课程: ${course.courseName} ${DateUtils.getDayOfWeekName(course.dayOfWeek)}第${course.startSection}节")
                } else {
                    Log.d(TAG, "  ✗ 跳过被调走课程: ${course.courseName} (key=$key)")
                }
            }
        }
        
        Log.d(TAG, "--- 合并结果 ---")
        displayCoursesMap.forEach { (day, courseList) ->
            Log.d(TAG, "第${weekNumber}周 ${DateUtils.getDayOfWeekName(day)}: ${courseList.size}门课")
            courseList.forEach { course ->
                Log.d(TAG, "  - ${course.courseName} 第${course.startSection}节")
            }
        }
        Log.d(TAG, "=== 合并完成 ===")
        
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