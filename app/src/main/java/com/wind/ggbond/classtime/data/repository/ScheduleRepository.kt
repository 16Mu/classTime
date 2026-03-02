package com.wind.ggbond.classtime.data.repository

import com.wind.ggbond.classtime.data.local.dao.ScheduleDao
import com.wind.ggbond.classtime.data.local.entity.Schedule
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 课表数据仓库
 * 
 * 课表是用户管理的核心单位，已合并原 SemesterRepository 的功能。
 * 每个课表包含昵称、学期时间信息和课程配置。
 */
@Singleton
class ScheduleRepository @Inject constructor(
    private val scheduleDao: ScheduleDao
) {
    
    // 获取所有课表（Flow 版本，用于 UI 实时监听）
    fun getAllSchedules(): Flow<List<Schedule>> {
        return scheduleDao.getAllSchedules()
    }
    
    // 获取所有课表（挂起版本，用于一次性读取）
    suspend fun getAllSchedulesList(): List<Schedule> {
        return scheduleDao.getAllSchedulesList()
    }
    
    // 根据 ID 获取课表
    suspend fun getScheduleById(scheduleId: Long): Schedule? {
        return scheduleDao.getScheduleById(scheduleId)
    }
    
    // 获取当前使用的课表
    suspend fun getCurrentSchedule(): Schedule? {
        return scheduleDao.getCurrentSchedule()
    }
    
    // 获取当前课表（Flow 版本）
    fun getCurrentScheduleFlow(): Flow<Schedule?> {
        return scheduleDao.getCurrentScheduleFlow()
    }
    
    // 插入课表
    suspend fun insertSchedule(schedule: Schedule): Long {
        return scheduleDao.insertSchedule(schedule)
    }
    
    // 更新课表
    suspend fun updateSchedule(schedule: Schedule) {
        scheduleDao.updateSchedule(schedule.copy(updatedAt = System.currentTimeMillis()))
    }
    
    // 删除课表
    suspend fun deleteSchedule(schedule: Schedule) {
        scheduleDao.deleteSchedule(schedule)
    }
    
    /**
     * 设置当前课表
     * 先清除所有课表的当前状态，再将指定课表设为当前
     */
    suspend fun setCurrentSchedule(scheduleId: Long) {
        scheduleDao.clearCurrentSchedule()
        scheduleDao.setCurrentSchedule(scheduleId)
    }
}



