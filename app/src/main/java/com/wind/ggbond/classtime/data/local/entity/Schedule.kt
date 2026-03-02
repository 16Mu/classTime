package com.wind.ggbond.classtime.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * 课表实体（支持多课表管理）
 * 
 * 课表是用户管理的核心单位，包含课表昵称、学期时间信息和课程配置。
 * 用户可以创建多个课表，每个课表拥有独立的开始日期、总周数和时间配置。
 */
@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val name: String,                          // 课表昵称（用户自定义，如 "大三下学期"、"选修课"）
    val schoolName: String = "",               // 学校名称
    
    // 学期时间信息（原 Semester 字段）
    val startDate: LocalDate = LocalDate.now(), // 学期开始日期（用于计算周次）
    val endDate: LocalDate = LocalDate.now().plusWeeks(20), // 学期结束日期
    val totalWeeks: Int = 20,                  // 总周数
    
    val isCurrent: Boolean = false,            // 是否为当前使用的课表
    
    val classTimeConfigName: String = "default", // 关联的时间配置名称，每个课表可以有独立的时间配置
    
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)



