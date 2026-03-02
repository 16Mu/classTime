package com.wind.ggbond.classtime.data.model

import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.CourseAdjustment

/**
 * 用于显示的课程数据模型
 * 整合了原始课程和临时调课信息
 */
data class DisplayCourse(
    val course: Course,                       // 原始课程数据
    val displayWeekNumber: Int,               // 显示的周次
    val displayDayOfWeek: Int,                // 显示的星期几
    val displayStartSection: Int,             // 显示的开始节次
    val displaySectionCount: Int,             // 显示的持续节数
    val isAdjusted: Boolean = false,          // 是否是调课后的时间
    val adjustment: CourseAdjustment? = null, // 调课记录（如果有）
    val adjustmentReason: String = "",        // 调课原因
    val originalTimeDesc: String = ""         // 原始时间描述（如果是调课）
) {
    /**
     * 获取显示用的课程ID
     */
    val displayId: String
        get() = if (isAdjusted) {
            "adjusted_${course.id}_${displayWeekNumber}"
        } else {
            "normal_${course.id}_${displayWeekNumber}"
        }
    
    /**
     * 是否在指定周次的指定时间显示
     */
    fun shouldDisplayAt(weekNumber: Int, dayOfWeek: Int, startSection: Int): Boolean {
        return displayWeekNumber == weekNumber && 
               displayDayOfWeek == dayOfWeek && 
               displayStartSection == startSection
    }
    
    companion object {
        /**
         * 从原始课程创建显示课程
         */
        fun fromCourse(course: Course, weekNumber: Int): DisplayCourse? {
            // 检查该周是否有课
            if (weekNumber !in course.weeks) {
                return null
            }
            
            return DisplayCourse(
                course = course,
                displayWeekNumber = weekNumber,
                displayDayOfWeek = course.dayOfWeek,
                displayStartSection = course.startSection,
                displaySectionCount = course.sectionCount,
                isAdjusted = false
            )
        }
        
        /**
         * 从调课记录创建显示课程（调整后的位置）
         */
        fun fromAdjustment(
            course: Course,
            adjustment: CourseAdjustment,
            originalTimeDesc: String
        ): DisplayCourse {
            return DisplayCourse(
                course = course,
                displayWeekNumber = adjustment.newWeekNumber,
                displayDayOfWeek = adjustment.newDayOfWeek,
                displayStartSection = adjustment.newStartSection,
                displaySectionCount = adjustment.newSectionCount,
                isAdjusted = true,
                adjustment = adjustment,
                adjustmentReason = adjustment.reason,
                originalTimeDesc = originalTimeDesc
            )
        }
    }
}


