package com.wind.ggbond.classtime.data.repository

import com.wind.ggbond.classtime.data.local.dao.AutoLoginLogDao
import com.wind.ggbond.classtime.data.local.entity.AutoLoginLog
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自动登录日志数据仓库
 */
@Singleton
class AutoLoginLogRepository @Inject constructor(
    private val logDao: AutoLoginLogDao
) {
    
    /**
     * 获取所有日志（按时间倒序）
     */
    fun getAllLogs(): Flow<List<AutoLoginLog>> {
        return logDao.getAllLogs()
    }
    
    /**
     * 获取最近N条日志
     */
    fun getRecentLogs(limit: Int = 50): Flow<List<AutoLoginLog>> {
        return logDao.getRecentLogs(limit)
    }
    
    /**
     * 获取最近一次日志
     */
    suspend fun getLatestLog(): AutoLoginLog? {
        return logDao.getLatestLog()
    }
    
    /**
     * 获取某个时间范围内的日志
     */
    fun getLogsByTimeRange(startTime: Long, endTime: Long): Flow<List<AutoLoginLog>> {
        return logDao.getLogsByTimeRange(startTime, endTime)
    }
    
    /**
     * 记录自动登录日志
     */
    suspend fun logAutoLogin(
        resultCode: String,
        resultMessage: String,
        username: String? = null,
        durationMs: Long = 0,
        success: Boolean = false,
        remark: String? = null
    ): Long {
        val log = AutoLoginLog(
            timestamp = System.currentTimeMillis(),
            resultCode = resultCode,
            resultMessage = resultMessage,
            username = username,
            durationMs = durationMs,
            success = success,
            remark = remark
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
    suspend fun getStatistics(): AutoLoginLogStatistics {
        val totalCount = logDao.getLogCount()
        val successCount = logDao.getSuccessCount()
        val failureCount = logDao.getFailureCount()
        return AutoLoginLogStatistics(
            totalCount = totalCount,
            successCount = successCount,
            failureCount = failureCount
        )
    }
    
    /**
     * 按结果代码查询日志
     */
    fun getLogsByResultCode(resultCode: String, limit: Int = 50): Flow<List<AutoLoginLog>> {
        return logDao.getLogsByResultCode(resultCode, limit)
    }
}

/**
 * 自动登录日志统计信息
 */
data class AutoLoginLogStatistics(
    val totalCount: Int = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0
)
