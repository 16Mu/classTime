package com.wind.ggbond.classtime.data.local.dao

import androidx.room.*
import com.wind.ggbond.classtime.data.local.entity.Schedule
import kotlinx.coroutines.flow.Flow

/**
 * 课表数据访问对象
 * 
 * 课表是用户管理的核心单位，包含昵称、学期时间和课程配置。
 * 已合并原 SemesterDao 的功能。
 */
@Dao
interface ScheduleDao {
    
    // 获取所有课表（按创建时间倒序）
    @Query("SELECT * FROM schedules ORDER BY createdAt DESC")
    fun getAllSchedules(): Flow<List<Schedule>>
    
    // 获取所有课表（挂起版本，非 Flow）
    @Query("SELECT * FROM schedules ORDER BY createdAt DESC")
    suspend fun getAllSchedulesList(): List<Schedule>
    
    // 根据 ID 获取课表
    @Query("SELECT * FROM schedules WHERE id = :scheduleId")
    suspend fun getScheduleById(scheduleId: Long): Schedule?
    
    // 获取当前使用的课表
    @Query("SELECT * FROM schedules WHERE isCurrent = 1 LIMIT 1")
    suspend fun getCurrentSchedule(): Schedule?
    
    // 获取当前课表（Flow 版本，用于 UI 实时监听）
    @Query("SELECT * FROM schedules WHERE isCurrent = 1 LIMIT 1")
    fun getCurrentScheduleFlow(): Flow<Schedule?>
    
    // 插入课表
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: Schedule): Long
    
    // 更新课表
    @Update
    suspend fun updateSchedule(schedule: Schedule)
    
    // 删除课表
    @Delete
    suspend fun deleteSchedule(schedule: Schedule)
    
    // 清除所有课表的当前状态
    @Query("UPDATE schedules SET isCurrent = 0")
    suspend fun clearCurrentSchedule()
    
    // 设置指定课表为当前课表
    @Query("UPDATE schedules SET isCurrent = 1 WHERE id = :scheduleId")
    suspend fun setCurrentSchedule(scheduleId: Long)

    @Transaction
    suspend fun switchCurrentSchedule(scheduleId: Long) {
        clearCurrentSchedule()
        setCurrentSchedule(scheduleId)
    }
}



