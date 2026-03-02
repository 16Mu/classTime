package com.wind.ggbond.classtime.data.repository

import com.wind.ggbond.classtime.data.local.dao.ExamDao
import com.wind.ggbond.classtime.data.local.entity.Exam
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 考试仓库
 */
@Singleton
class ExamRepository @Inject constructor(
    private val examDao: ExamDao
) {
    
    /**
     * 获取所有考试
     */
    fun getAllExams(): Flow<List<Exam>> {
        return examDao.getAllExams()
    }
    
    /**
     * 根据ID获取考试
     */
    suspend fun getExamById(examId: Long): Exam? {
        return examDao.getExamById(examId)
    }
    
    /**
     * 根据ID获取考试（Flow）
     */
    fun getExamByIdFlow(examId: Long): Flow<Exam?> {
        return examDao.getExamByIdFlow(examId)
    }
    
    /**
     * 获取指定课程的所有考试
     */
    fun getExamsByCourse(courseId: Long): Flow<List<Exam>> {
        return examDao.getExamsByCourse(courseId)
    }
    
    /**
     * 获取指定周次范围的考试（用于顶部横幅）
     */
    suspend fun getExamsByWeekRange(startWeek: Int, endWeek: Int): List<Exam> {
        return examDao.getExamsByWeekRange(startWeek, endWeek)
    }
    
    /**
     * 获取指定周次范围的考试（Flow版本）
     */
    fun getExamsByWeekRangeFlow(startWeek: Int, endWeek: Int): Flow<List<Exam>> {
        return examDao.getExamsByWeekRangeFlow(startWeek, endWeek)
    }
    
    /**
     * 获取指定周次且有具体节次的考试（用于课表显示）
     */
    suspend fun getExamsWithSectionByWeek(weekNumber: Int): List<Exam> {
        return examDao.getExamsWithSectionByWeek(weekNumber)
    }
    
    /**
     * 检查指定时间段是否有考试冲突
     */
    suspend fun checkExamConflict(
        weekNumber: Int,
        dayOfWeek: Int,
        startSection: Int,
        sectionCount: Int,
        excludeExamId: Long? = null
    ): List<Exam> {
        val conflicts = examDao.getExamsInTimeRange(
            weekNumber,
            dayOfWeek,
            startSection,
            startSection + sectionCount
        )
        
        return if (excludeExamId != null) {
            conflicts.filter { it.id != excludeExamId }
        } else {
            conflicts
        }
    }
    
    /**
     * 获取即将到来的考试
     */
    suspend fun getUpcomingExams(fromWeek: Int, limit: Int = 10): List<Exam> {
        return examDao.getUpcomingExams(fromWeek, limit)
    }
    
    /**
     * 插入考试
     */
    suspend fun insertExam(exam: Exam): Long {
        return examDao.insertExam(exam.copy(
            updatedAt = System.currentTimeMillis()
        ))
    }
    
    /**
     * 插入多个考试
     */
    suspend fun insertExams(exams: List<Exam>): List<Long> {
        val currentTime = System.currentTimeMillis()
        val updatedExams = exams.map { it.copy(updatedAt = currentTime) }
        return examDao.insertExams(updatedExams)
    }
    
    /**
     * 更新考试
     */
    suspend fun updateExam(exam: Exam) {
        examDao.updateExam(exam.copy(
            updatedAt = System.currentTimeMillis()
        ))
    }
    
    /**
     * 删除考试
     */
    suspend fun deleteExam(exam: Exam) {
        examDao.deleteExam(exam)
    }
    
    /**
     * 根据ID删除考试
     */
    suspend fun deleteExamById(examId: Long) {
        examDao.deleteExamById(examId)
    }
    
    /**
     * 删除指定课程的所有考试
     */
    suspend fun deleteExamsByCourse(courseId: Long) {
        examDao.deleteExamsByCourse(courseId)
    }
    
    /**
     * 删除所有考试
     */
    suspend fun deleteAllExams() {
        examDao.deleteAllExams()
    }
}



