package com.wind.ggbond.classtime.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * 考试实体类
 * 
 * 支持两种模式：
 * 1. 周次模式：仅指定周次，在横幅显示
 * 2. 精确模式：指定周次+星期+节次，在横幅和课表格子中显示
 */
@Entity(
    tableName = "exams",
    foreignKeys = [
        ForeignKey(
            entity = Course::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [androidx.room.Index("courseId")]
)
data class Exam(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // 关联信息
    val courseId: Long,              // 关联课程ID
    val courseName: String,          // 课程名称
    
    // 考试基本信息
    val examType: String,            // 考试类型：期中考试/期末考试/随堂测验/补考
    val weekNumber: Int,             // 考试周次（必填）
    
    // 精确时间信息（可选）
    val dayOfWeek: Int? = null,      // 星期几（1-7，可选）
    val startSection: Int? = null,   // 开始节次（可选）
    val sectionCount: Int = 2,       // 持续节数（默认2节）
    
    // 详细信息
    val examTime: String = "",       // 时间段，如"09:00-11:00"
    val location: String = "",       // 考试地点
    val seat: String = "",           // 座位号
    
    // 提醒设置
    val reminderEnabled: Boolean = true,  // 是否开启提醒
    val reminderDays: Int = 3,       // 提前几天提醒
    
    // 其他信息
    val note: String = "",           // 备注（如"开卷"、"可带计算器"）
    
    // 时间戳
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * 判断是否为精确模式（指定了具体节次）
     */
    fun isPreciseMode(): Boolean {
        return dayOfWeek != null && startSection != null
    }
    
    /**
     * 获取考试结束节次
     */
    fun getEndSection(): Int? {
        return startSection?.let { it + sectionCount - 1 }
    }
    
    /**
     * 获取考试时间描述
     */
    fun getTimeDescription(): String {
        return when {
            examTime.isNotEmpty() -> examTime
            isPreciseMode() -> "第${startSection}-${getEndSection()}节"
            else -> "第${weekNumber}周"
        }
    }
    
    /**
     * 获取完整地点信息（包括座位）
     */
    fun getFullLocation(): String {
        return when {
            location.isEmpty() && seat.isEmpty() -> ""
            location.isEmpty() -> seat
            seat.isEmpty() -> location
            else -> "$location ($seat)"
        }
    }
}

















