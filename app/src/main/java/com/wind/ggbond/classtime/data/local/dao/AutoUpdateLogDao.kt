package com.wind.ggbond.classtime.data.local.dao

import androidx.room.*
import com.wind.ggbond.classtime.data.local.entity.AutoUpdateLog
import kotlinx.coroutines.flow.Flow

/**
 * 自动更新日志数据访问对象
 */
@Dao
interface AutoUpdateLogDao {
    
    /**
     * 获取所有日志（按时间倒序）
     */
    @Query("SELECT * FROM auto_update_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<AutoUpdateLog>>
    
    /**
     * 获取最近N条日志
     */
    @Query("SELECT * FROM auto_update_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int): Flow<List<AutoUpdateLog>>
    
    /**
     * 获取最近一次日志
     */
    @Query("SELECT * FROM auto_update_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestLog(): AutoUpdateLog?
    
    /**
     * 获取某个时间范围内的日志
     */
    @Query("SELECT * FROM auto_update_logs WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getLogsByTimeRange(startTime: Long, endTime: Long): Flow<List<AutoUpdateLog>>
    
    /**
     * 获取某个结果类型的日志
     */
    @Query("SELECT * FROM auto_update_logs WHERE result = :result ORDER BY timestamp DESC")
    fun getLogsByResult(result: String): Flow<List<AutoUpdateLog>>
    
    /**
     * 插入日志
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AutoUpdateLog): Long
    
    /**
     * 删除所有日志
     */
    @Query("DELETE FROM auto_update_logs")
    suspend fun deleteAllLogs()
    
    /**
     * 删除某个时间之前的日志（用于清理旧日志）
     */
    @Query("DELETE FROM auto_update_logs WHERE timestamp < :timestamp")
    suspend fun deleteLogsBeforeTime(timestamp: Long)
    
    /**
     * 获取日志总数
     */
    @Query("SELECT COUNT(*) FROM auto_update_logs")
    suspend fun getLogCount(): Int
    
    /**
     * 获取成功的日志数量
     */
    @Query("SELECT COUNT(*) FROM auto_update_logs WHERE result = 'SUCCESS'")
    suspend fun getSuccessCount(): Int
    
    /**
     * 获取失败的日志数量
     */
    @Query("SELECT COUNT(*) FROM auto_update_logs WHERE result = 'FAILED'")
    suspend fun getFailedCount(): Int
}











