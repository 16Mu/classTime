package com.wind.ggbond.classtime.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalTime

/**
 * 上下课时间配置实体
 */
@Entity(
    tableName = "class_times",
    indices = [androidx.room.Index(value = ["configName", "sectionNumber"], unique = true)]
)
data class ClassTime(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val sectionNumber: Int,           // 节次编号 (1, 2, 3...)
    val startTime: LocalTime,         // 开始时间
    val endTime: LocalTime,           // 结束时间
    
    val configName: String = "default", // 配置名称，支持多套时间表
    
    val createdAt: Long = System.currentTimeMillis()
)



