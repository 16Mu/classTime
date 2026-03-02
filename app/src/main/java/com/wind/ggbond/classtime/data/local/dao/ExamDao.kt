package com.wind.ggbond.classtime.data.local.dao

import androidx.room.*
import com.wind.ggbond.classtime.data.local.entity.Exam
import kotlinx.coroutines.flow.Flow

/**
 * 考试数据访问对象
 */
@Dao
interface ExamDao {
    
    /**
     * 查询所有考试
     */
    @Query("SELECT * FROM exams ORDER BY weekNumber, dayOfWeek, startSection")
    fun getAllExams(): Flow<List<Exam>>
    
    /**
     * 根据ID查询考试
     */
    @Query("SELECT * FROM exams WHERE id = :examId")
    suspend fun getExamById(examId: Long): Exam?
    
    /**
     * 根据ID查询考试（Flow）
     */
    @Query("SELECT * FROM exams WHERE id = :examId")
    fun getExamByIdFlow(examId: Long): Flow<Exam?>
    
    /**
     * 查询指定课程的所有考试
     */
    @Query("SELECT * FROM exams WHERE courseId = :courseId ORDER BY weekNumber")
    fun getExamsByCourse(courseId: Long): Flow<List<Exam>>
    
    /**
     * 查询指定周次范围的所有考试（用于顶部横幅）
     */
    @Query("""
        SELECT * FROM exams 
        WHERE weekNumber >= :startWeek AND weekNumber <= :endWeek
        ORDER BY weekNumber, dayOfWeek, startSection
    """)
    suspend fun getExamsByWeekRange(startWeek: Int, endWeek: Int): List<Exam>
    
    /**
     * 查询指定周次范围的所有考试（Flow版本）
     */
    @Query("""
        SELECT * FROM exams 
        WHERE weekNumber >= :startWeek AND weekNumber <= :endWeek
        ORDER BY weekNumber, dayOfWeek, startSection
    """)
    fun getExamsByWeekRangeFlow(startWeek: Int, endWeek: Int): Flow<List<Exam>>
    
    /**
     * 查询指定周次且有具体节次的考试（用于课表格子显示）
     */
    @Query("""
        SELECT * FROM exams 
        WHERE weekNumber = :weekNumber 
        AND dayOfWeek IS NOT NULL 
        AND startSection IS NOT NULL
        ORDER BY dayOfWeek, startSection
    """)
    suspend fun getExamsWithSectionByWeek(weekNumber: Int): List<Exam>
    
    /**
     * 查询指定周次、星期、节次的考试（用于冲突检测）
     */
    @Query("""
        SELECT * FROM exams 
        WHERE weekNumber = :weekNumber 
        AND dayOfWeek = :dayOfWeek 
        AND startSection IS NOT NULL
        AND startSection < :endSection 
        AND (startSection + sectionCount) > :startSection
    """)
    suspend fun getExamsInTimeRange(
        weekNumber: Int,
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int
    ): List<Exam>
    
    /**
     * 查询未来的考试（从指定周次开始）
     */
    @Query("""
        SELECT * FROM exams 
        WHERE weekNumber >= :fromWeek
        ORDER BY weekNumber, dayOfWeek, startSection
        LIMIT :limit
    """)
    suspend fun getUpcomingExams(fromWeek: Int, limit: Int = 10): List<Exam>
    
    /**
     * 插入考试
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExam(exam: Exam): Long
    
    /**
     * 插入多个考试
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExams(exams: List<Exam>): List<Long>
    
    /**
     * 更新考试
     */
    @Update
    suspend fun updateExam(exam: Exam)
    
    /**
     * 删除考试
     */
    @Delete
    suspend fun deleteExam(exam: Exam)
    
    /**
     * 根据ID删除考试
     */
    @Query("DELETE FROM exams WHERE id = :examId")
    suspend fun deleteExamById(examId: Long)
    
    /**
     * 删除指定课程的所有考试
     */
    @Query("DELETE FROM exams WHERE courseId = :courseId")
    suspend fun deleteExamsByCourse(courseId: Long)
    
    /**
     * 删除所有考试
     */
    @Query("DELETE FROM exams")
    suspend fun deleteAllExams()
}



