package com.wind.ggbond.classtime.data.local.dao

import androidx.room.*
import com.wind.ggbond.classtime.data.local.entity.CourseAdjustment
import kotlinx.coroutines.flow.Flow

/**
 * 临时调课数据访问对象
 */
@Dao
interface CourseAdjustmentDao {
    
    /**
     * 获取所有调课记录
     */
    @Query("SELECT * FROM course_adjustments ORDER BY createdAt DESC")
    fun getAllAdjustments(): Flow<List<CourseAdjustment>>
    
    /**
     * 获取某个课表的所有调课记录
     */
    @Query("SELECT * FROM course_adjustments WHERE scheduleId = :scheduleId")
    fun getAdjustmentsBySchedule(scheduleId: Long): Flow<List<CourseAdjustment>>
    
    /**
     * 获取某个课表的所有调课记录（同步方法，供 Widget 使用）
     */
    @Query("SELECT * FROM course_adjustments WHERE scheduleId = :scheduleId")
    suspend fun getAdjustmentsByScheduleSync(scheduleId: Long): List<CourseAdjustment>
    
    /**
     * 获取某个课程的所有调课记录
     */
    @Query("SELECT * FROM course_adjustments WHERE originalCourseId = :courseId")
    fun getAdjustmentsByCourse(courseId: Long): Flow<List<CourseAdjustment>>
    
    /**
     * 获取某个课程在特定周次的调课记录
     */
    @Query("""
        SELECT * FROM course_adjustments 
        WHERE originalCourseId = :courseId 
        AND originalWeekNumber = :weekNumber
        LIMIT 1
    """)
    suspend fun getAdjustmentByCourseAndWeek(courseId: Long, weekNumber: Int): CourseAdjustment?
    
    /**
     * 获取某个时间段的所有调课到这个时间的记录（用于冲突检测）
     */
    @Query("""
        SELECT * FROM course_adjustments 
        WHERE scheduleId = :scheduleId 
        AND newWeekNumber = :weekNumber
        AND newDayOfWeek = :dayOfWeek 
        AND newStartSection < :endSection 
        AND (newStartSection + newSectionCount) > :startSection
    """)
    suspend fun getAdjustmentsInNewTimeRange(
        scheduleId: Long,
        weekNumber: Int,
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int
    ): List<CourseAdjustment>
    
    /**
     * 根据原始时间查找调课记录（用于检查某个时间段的课是否被调走）
     */
    @Query("""
        SELECT * FROM course_adjustments 
        WHERE scheduleId = :scheduleId 
        AND originalWeekNumber = :weekNumber
        AND originalDayOfWeek = :dayOfWeek 
        AND originalStartSection < :endSection 
        AND (originalStartSection + originalSectionCount) > :startSection
    """)
    suspend fun getAdjustmentsFromOriginalTime(
        scheduleId: Long,
        weekNumber: Int,
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int
    ): List<CourseAdjustment>
    
    /**
     * 插入调课记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdjustment(adjustment: CourseAdjustment): Long
    
    /**
     * 更新调课记录
     */
    @Update
    suspend fun updateAdjustment(adjustment: CourseAdjustment)
    
    /**
     * 删除调课记录（取消调课）
     */
    @Delete
    suspend fun deleteAdjustment(adjustment: CourseAdjustment)
    
    /**
     * 根据ID删除调课记录
     */
    @Query("DELETE FROM course_adjustments WHERE id = :id")
    suspend fun deleteAdjustmentById(id: Long)
    
    /**
     * 删除某个课程的所有调课记录
     */
    @Query("DELETE FROM course_adjustments WHERE originalCourseId = :courseId")
    suspend fun deleteAdjustmentsByCourse(courseId: Long)
    
    /**
     * 删除某个课表的所有调课记录
     */
    @Query("DELETE FROM course_adjustments WHERE scheduleId = :scheduleId")
    suspend fun deleteAdjustmentsBySchedule(scheduleId: Long)
    
    /**
     * 删除某个课程在特定周次的调课记录
     */
    @Query("""
        DELETE FROM course_adjustments 
        WHERE originalCourseId = :courseId 
        AND originalWeekNumber = :weekNumber
    """)
    suspend fun deleteAdjustmentByCourseAndWeek(courseId: Long, weekNumber: Int)
}


