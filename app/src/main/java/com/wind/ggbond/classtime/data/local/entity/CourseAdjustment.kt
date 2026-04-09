package com.wind.ggbond.classtime.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * 临时调课实体
 * 用于记录某个课程在某个周次的临时调整
 */
@Entity(
    tableName = "course_adjustments",
    foreignKeys = [
        ForeignKey(
            entity = Course::class,
            parentColumns = ["id"],
            childColumns = ["originalCourseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Schedule::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        androidx.room.Index("originalCourseId"),
        androidx.room.Index("scheduleId")
    ]
)
data class CourseAdjustment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // 关联信息
    val originalCourseId: Long,       // 原始课程ID
    val scheduleId: Long,              // 所属课表ID
    
    // 原始时间信息
    val originalWeekNumber: Int,       // 原始周次
    val originalDayOfWeek: Int,        // 原始星期几 (1-7, 1=周一)
    val originalStartSection: Int,     // 原始开始节次
    val originalSectionCount: Int,     // 原始持续节数
    
    // 新的时间信息
    val newWeekNumber: Int,            // 新的周次
    val newDayOfWeek: Int,             // 新的星期几 (1-7, 1=周一)
    val newStartSection: Int,          // 新的开始节次
    val newSectionCount: Int,          // 新的持续节数
    val newClassroom: String = "",     // 新的教室
    
    // 调课信息
    val reason: String = "",           // 调课原因
    
    // 时间戳
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)


