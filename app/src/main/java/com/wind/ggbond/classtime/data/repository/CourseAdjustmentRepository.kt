package com.wind.ggbond.classtime.data.repository

import com.wind.ggbond.classtime.data.local.dao.CourseAdjustmentDao
import com.wind.ggbond.classtime.data.local.entity.CourseAdjustment
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 临时调课数据仓库
 */
@Singleton
class CourseAdjustmentRepository @Inject constructor(
    private val adjustmentDao: CourseAdjustmentDao
) {
    
    /**
     * 获取所有调课记录
     */
    fun getAllAdjustments(): Flow<List<CourseAdjustment>> {
        return adjustmentDao.getAllAdjustments()
    }
    
    /**
     * 获取某个课表的所有调课记录
     */
    fun getAdjustmentsBySchedule(scheduleId: Long): Flow<List<CourseAdjustment>> {
        return adjustmentDao.getAdjustmentsBySchedule(scheduleId)
    }
    
    /**
     * 获取某个课程的所有调课记录
     */
    fun getAdjustmentsByCourse(courseId: Long): Flow<List<CourseAdjustment>> {
        return adjustmentDao.getAdjustmentsByCourse(courseId)
    }
    
    /**
     * 获取某个课程在特定周次的调课记录
     */
    suspend fun getAdjustmentByCourseAndWeek(courseId: Long, weekNumber: Int): CourseAdjustment? {
        return adjustmentDao.getAdjustmentByCourseAndWeek(courseId, weekNumber)
    }
    
    /**
     * 检查新时间段是否有冲突
     * @return 冲突的调课记录列表
     */
    suspend fun checkNewTimeConflict(
        scheduleId: Long,
        weekNumber: Int,
        dayOfWeek: Int,
        startSection: Int,
        sectionCount: Int
    ): List<CourseAdjustment> {
        val endSection = startSection + sectionCount
        return adjustmentDao.getAdjustmentsInNewTimeRange(
            scheduleId, weekNumber, dayOfWeek, startSection, endSection
        )
    }
    
    /**
     * 检查原始时间段是否有课被调走
     */
    suspend fun getAdjustedCoursesFromTime(
        scheduleId: Long,
        weekNumber: Int,
        dayOfWeek: Int,
        startSection: Int,
        sectionCount: Int
    ): List<CourseAdjustment> {
        val endSection = startSection + sectionCount
        return adjustmentDao.getAdjustmentsFromOriginalTime(
            scheduleId, weekNumber, dayOfWeek, startSection, endSection
        )
    }
    
    /**
     * 创建或更新调课记录
     */
    suspend fun saveAdjustment(adjustment: CourseAdjustment): Long {
        return if (adjustment.id == 0L) {
            adjustmentDao.insertAdjustment(adjustment)
        } else {
            adjustmentDao.updateAdjustment(adjustment.copy(updatedAt = System.currentTimeMillis()))
            adjustment.id
        }
    }
    
    /**
     * 取消调课
     */
    suspend fun cancelAdjustment(adjustment: CourseAdjustment) {
        adjustmentDao.deleteAdjustment(adjustment)
    }
    
    /**
     * 取消某个课程在特定周次的调课
     */
    suspend fun cancelAdjustmentByCourseAndWeek(courseId: Long, weekNumber: Int) {
        adjustmentDao.deleteAdjustmentByCourseAndWeek(courseId, weekNumber)
    }
    
    /**
     * 删除某个课程的所有调课记录
     */
    suspend fun deleteAdjustmentsByCourse(courseId: Long) {
        adjustmentDao.deleteAdjustmentsByCourse(courseId)
    }
}


