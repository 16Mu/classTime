package com.wind.ggbond.classtime.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.wind.ggbond.classtime.data.local.converter.Converters

/**
 * 课程实体
 */
@Entity(tableName = "courses")
@TypeConverters(Converters::class)
data class Course(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // 基本信息
    val courseName: String,           // 课程名称
    val teacher: String = "",         // 教师
    val classroom: String = "",       // 教室
    
    // 时间信息
    val dayOfWeek: Int,               // 星期几 (1-7, 1=周一)
    val startSection: Int,            // 开始节次 (从1开始)
    val sectionCount: Int = 1,        // 持续节数
    
    // 周次信息
    val weeks: List<Int> = emptyList(), // 上课周次列表 [1,2,3,4...]
    val weekExpression: String = "",    // 周次表达式 "1-16周" "单周" 等（用于显示）
    
    // 所属课表
    val scheduleId: Long = 1,         // 所属课表ID
    
    // 附加信息
    val credit: Float = 0f,           // 学分
    val courseCode: String = "",      // 课程代码
    val note: String = "",            // 备注
    val color: String = "#42A5F5",    // 课程颜色 (16进制颜色值)
    
    // 提醒设置
    val reminderEnabled: Boolean = true,   // 是否启用提醒（默认开启）
    val reminderMinutes: Int = 10,         // 提前提醒分钟数
    
    // 时间戳
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)



