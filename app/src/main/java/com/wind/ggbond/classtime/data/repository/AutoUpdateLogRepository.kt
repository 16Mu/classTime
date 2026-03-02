package com.wind.ggbond.classtime.data.repository

import com.wind.ggbond.classtime.data.local.dao.AutoUpdateLogDao
import com.wind.ggbond.classtime.data.local.entity.AutoUpdateLog
import com.wind.ggbond.classtime.data.local.entity.UpdateResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自动更新日志数据仓库
 */
@Singleton
class AutoUpdateLogRepository @Inject constructor(
    private val logDao: AutoUpdateLogDao
) {
    
    /**
     * 获取所有日志（按时间倒序）
     */
    fun getAllLogs(): Flow<List<AutoUpdateLog>> {
        return logDao.getAllLogs()
    }
    
    /**
     * 获取最近N条日志
     */
    fun getRecentLogs(limit: Int = 50): Flow<List<AutoUpdateLog>> {
        return logDao.getRecentLogs(limit)
    }
    
    /**
     * 获取最近一次日志
     */
    suspend fun getLatestLog(): AutoUpdateLog? {
        return logDao.getLatestLog()
    }
    
    /**
     * 获取某个时间范围内的日志
     */
    fun getLogsByTimeRange(startTime: Long, endTime: Long): Flow<List<AutoUpdateLog>> {
        return logDao.getLogsByTimeRange(startTime, endTime)
    }
    
    /**
     * 记录更新日志
     */
    suspend fun logUpdate(
        triggerEvent: String,
        result: UpdateResult,
        successMessage: String? = null,
        failureReason: String? = null,
        scheduleId: Long? = null,
        durationMs: Long = 0
    ): Long {
        val log = AutoUpdateLog(
            timestamp = System.currentTimeMillis(),
            triggerEvent = triggerEvent,
            result = result,
            successMessage = successMessage,
            failureReason = failureReason,
            scheduleId = scheduleId,
            durationMs = durationMs
        )
        return logDao.insertLog(log)
    }
    
    /**
     * 清空所有日志
     */
    suspend fun clearAllLogs() {
        logDao.deleteAllLogs()
    }
    
    /**
     * 清理旧日志（保留最近30天）
     */
    suspend fun cleanOldLogs(daysToKeep: Int = 30) {
        val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        logDao.deleteLogsBeforeTime(cutoffTime)
    }
    
    /**
     * 获取统计信息
     */
    suspend fun getStatistics(): LogStatistics {
        val totalCount = logDao.getLogCount()
        val successCount = logDao.getSuccessCount()
        val failedCount = logDao.getFailedCount()
        return LogStatistics(
            totalCount = totalCount,
            successCount = successCount,
            failedCount = failedCount,
            skippedCount = totalCount - successCount - failedCount
        )
    }
}

/**
 * 日志统计信息
 */
data class LogStatistics(
    val totalCount: Int,
    val successCount: Int,
    val failedCount: Int,
    val skippedCount: Int
)











