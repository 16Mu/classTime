package com.wind.ggbond.classtime.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 自动更新日志实体
 * 用于记录自动更新课表的历史记录
 */
@Entity(tableName = "auto_update_logs")
data class AutoUpdateLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // 更新时间
    val timestamp: Long = System.currentTimeMillis(),
    
    // 触发事件（如：进入软件、后台定时任务等）
    val triggerEvent: String,
    
    // 更新结果：SUCCESS, FAILED, SKIPPED
    val result: UpdateResult,
    
    // 成功时的详细信息（如：检测到3处课程变化）
    val successMessage: String? = null,
    
    // 失败原因（仅在失败时）
    val failureReason: String? = null,
    
    // 课表ID（可选）
    val scheduleId: Long? = null,
    
    // 更新耗时（毫秒）
    val durationMs: Long = 0
)

/**
 * 更新结果枚举
 */
enum class UpdateResult {
    SUCCESS,    // 更新成功
    FAILED,     // 更新失败
    SKIPPED     // 跳过更新（如：不满足更新条件、距上次更新时间太短等）
}











