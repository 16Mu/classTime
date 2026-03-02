package com.wind.ggbond.classtime.data.repository

import com.wind.ggbond.classtime.data.local.dao.ClassTimeDao
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Schedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 上下课时间配置数据仓库
 */
@Singleton
class ClassTimeRepository @Inject constructor(
    private val classTimeDao: ClassTimeDao,
    private val scheduleRepository: ScheduleRepository
) {
    
    fun getClassTimesByConfig(configName: String = "default"): Flow<List<ClassTime>> {
        return classTimeDao.getClassTimesByConfig(configName)
    }
    
    suspend fun getClassTimesByConfigSync(configName: String = "default"): List<ClassTime> {
        return classTimeDao.getClassTimesByConfigSync(configName)
    }
    
    suspend fun getClassTime(configName: String = "default", sectionNumber: Int): ClassTime? {
        return classTimeDao.getClassTime(configName, sectionNumber)
    }
    
    /**
     * 根据课表ID获取对应的时间配置
     */
    suspend fun getClassTimesBySchedule(scheduleId: Long): List<ClassTime> {
        val schedule = scheduleRepository.getScheduleById(scheduleId)
        val configName = schedule?.classTimeConfigName ?: "default"
        return classTimeDao.getClassTimesByConfigSync(configName)
    }
    
    /**
     * 根据课表ID获取对应的时间配置（Flow版本）
     */
    fun getClassTimesByScheduleFlow(scheduleId: Long): Flow<List<ClassTime>> {
        // 由于需要先获取课表信息，这里使用一个简化的实现
        // 实际使用中，可以在ViewModel中处理这个逻辑
        return classTimeDao.getClassTimesByConfig("default")
    }
    
    /**
     * 为课表设置时间配置
     */
    suspend fun setClassTimeConfigForSchedule(scheduleId: Long, configName: String) {
        val schedule = scheduleRepository.getScheduleById(scheduleId)
        if (schedule != null) {
            val updatedSchedule = schedule.copy(
                classTimeConfigName = configName,
                updatedAt = System.currentTimeMillis()
            )
            scheduleRepository.updateSchedule(updatedSchedule)
        }
    }
    
    suspend fun insertClassTime(classTime: ClassTime): Long {
        return classTimeDao.insertClassTime(classTime)
    }
    
    suspend fun insertClassTimes(classTimes: List<ClassTime>) {
        classTimeDao.insertClassTimes(classTimes)
    }
    
    suspend fun updateClassTime(classTime: ClassTime) {
        classTimeDao.updateClassTime(classTime)
    }
    
    suspend fun deleteClassTime(classTime: ClassTime) {
        classTimeDao.deleteClassTime(classTime)
    }
    
    suspend fun deleteAllByConfig(configName: String = "default") {
        classTimeDao.deleteAllByConfig(configName)
    }
}



