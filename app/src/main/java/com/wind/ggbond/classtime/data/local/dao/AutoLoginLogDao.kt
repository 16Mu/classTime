package com.wind.ggbond.classtime.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.wind.ggbond.classtime.data.local.entity.AutoLoginLog
import kotlinx.coroutines.flow.Flow

/**
 * 自动登录日志 DAO
 */
@Dao
interface AutoLoginLogDao {
    
    /**
     * 插入日志
     */
    @Insert
    suspend fun insertLog(log: AutoLoginLog): Long
    
    /**
     * 获取所有日志（按时间倒序）
     */
    @Query("SELECT * FROM auto_login_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<AutoLoginLog>>
    
    /**
     * 获取最近N条日志
     */
    @Query("SELECT * FROM auto_login_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 50): Flow<List<AutoLoginLog>>
    
    /**
     * 获取最近一条日志
     */
    @Query("SELECT * FROM auto_login_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestLog(): AutoLoginLog?
    
    /**
     * 获取某个时间范围内的日志
     */
    @Query("SELECT * FROM auto_login_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getLogsByTimeRange(startTime: Long, endTime: Long): Flow<List<AutoLoginLog>>
    
    /**
     * 获取成功的日志数
     */
    @Query("SELECT COUNT(*) FROM auto_login_logs WHERE success = 1")
    suspend fun getSuccessCount(): Int
    
    /**
     * 获取失败的日志数
     */
    @Query("SELECT COUNT(*) FROM auto_login_logs WHERE success = 0")
    suspend fun getFailureCount(): Int
    
    /**
     * 获取总日志数
     */
    @Query("SELECT COUNT(*) FROM auto_login_logs")
    suspend fun getLogCount(): Int
    
    /**
     * 删除所有日志
     */
    @Query("DELETE FROM auto_login_logs")
    suspend fun deleteAllLogs()
    
    /**
     * 删除指定时间之前的日志
     */
    @Query("DELETE FROM auto_login_logs WHERE timestamp < :cutoffTime")
    suspend fun deleteLogsBeforeTime(cutoffTime: Long)
    
    /**
     * 按结果代码查询日志
     */
    @Query("SELECT * FROM auto_login_logs WHERE resultCode = :resultCode ORDER BY timestamp DESC LIMIT :limit")
    fun getLogsByResultCode(resultCode: String, limit: Int = 50): Flow<List<AutoLoginLog>>
}
