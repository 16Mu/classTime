package com.wind.ggbond.classtime.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 提醒实体
 */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = Course::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("courseId"), Index(value = ["courseId", "isEnabled"]), Index("triggerTime"), Index("dayOfWeek")]
)
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val courseId: Long,               // 关联的课程ID
    val minutesBefore: Int = 10,      // 提前多少分钟提醒
    val isEnabled: Boolean = true,    // 是否启用
    
    val weekNumber: Int,              // 第几周
    val dayOfWeek: Int,               // 星期几
    val triggerTime: Long,            // 触发时间戳
    
    val workRequestId: String = "",   // 提醒请求ID（AlarmManager 或 WorkManager）
    
    val createdAt: Long = System.currentTimeMillis()
)



