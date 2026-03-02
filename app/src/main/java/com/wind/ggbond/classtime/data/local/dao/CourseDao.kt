package com.wind.ggbond.classtime.data.local.dao

import androidx.room.*
import com.wind.ggbond.classtime.data.local.entity.Course
import kotlinx.coroutines.flow.Flow

/**
 * 课程数据访问对象
 */
@Dao
interface CourseDao {
    
    @Query("SELECT * FROM courses ORDER BY scheduleId, dayOfWeek, startSection")
    suspend fun getAllCourses(): List<Course>
    
    @Query("SELECT * FROM courses WHERE scheduleId = :scheduleId ORDER BY dayOfWeek, startSection")
    fun getAllCoursesBySchedule(scheduleId: Long): Flow<List<Course>>
    
    @Query("SELECT * FROM courses WHERE scheduleId = :scheduleId AND dayOfWeek = :dayOfWeek ORDER BY startSection")
    fun getCoursesByDay(scheduleId: Long, dayOfWeek: Int): Flow<List<Course>>
    
    @Query("SELECT * FROM courses WHERE id = :courseId")
    suspend fun getCourseById(courseId: Long): Course?
    
    @Query("SELECT * FROM courses WHERE id = :courseId")
    fun getCourseByIdFlow(courseId: Long): Flow<Course?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: Course): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<Course>): List<Long>
    
    @Update
    suspend fun updateCourse(course: Course)
    
    @Delete
    suspend fun deleteCourse(course: Course)
    
    @Query("DELETE FROM courses WHERE scheduleId = :scheduleId")
    suspend fun deleteAllCoursesBySchedule(scheduleId: Long)
    
    @Query("DELETE FROM courses WHERE id = :courseId")
    suspend fun deleteCourseById(courseId: Long)
    
    // 查询某个时间段的课程（用于冲突检测）
    @Query("""
        SELECT * FROM courses 
        WHERE scheduleId = :scheduleId 
        AND dayOfWeek = :dayOfWeek 
        AND startSection < :endSection 
        AND (startSection + sectionCount) > :startSection
    """)
    suspend fun getCoursesInTimeRange(
        scheduleId: Long,
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int
    ): List<Course>
}



