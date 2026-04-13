package com.wind.ggbond.classtime.data.local.dao

import androidx.room.*
import com.wind.ggbond.classtime.data.local.entity.Course
import kotlinx.coroutines.flow.Flow

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

    @Update
    suspend fun updateCourses(courses: List<Course>)

    @Query("UPDATE courses SET reminderEnabled = :enabled, reminderMinutes = CASE WHEN :enabled THEN CASE WHEN reminderMinutes > 0 THEN reminderMinutes ELSE :minutes END ELSE :minutes END, updatedAt = :updatedAt WHERE scheduleId = :scheduleId AND reminderEnabled != :enabled")
    suspend fun updateReminderBySchedule(scheduleId: Long, enabled: Boolean, minutes: Int, updatedAt: Long): Int

    @Query("DELETE FROM courses WHERE id IN (:ids)")
    suspend fun deleteCoursesByIds(ids: List<Long>)

    @Delete
    suspend fun deleteCourse(course: Course)

    @Query("DELETE FROM courses WHERE scheduleId = :scheduleId")
    suspend fun deleteAllCoursesBySchedule(scheduleId: Long)

    @Query("DELETE FROM courses WHERE id = :courseId")
    suspend fun deleteCourseById(courseId: Long)

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

    @Transaction
    suspend fun replaceCoursesForSchedule(scheduleId: Long, courses: List<Course>) {
        deleteAllCoursesBySchedule(scheduleId)
        insertCourses(courses)
    }
}
