package com.wind.ggbond.classtime.data.repository

import com.wind.ggbond.classtime.data.local.dao.ExamDao
import com.wind.ggbond.classtime.data.local.entity.Exam
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExamRepository @Inject constructor(
    examDao: ExamDao
) : BaseRepository<Exam, ExamDao>(examDao) {

    override suspend fun getAll(): List<Exam> {
        return dao.getAllExams().first()
    }

    override fun getAllFlow(): Flow<List<Exam>> = dao.getAllExams()

    override suspend fun getById(id: Long): Exam? = dao.getExamById(id)

    override fun getByIdFlow(id: Long): Flow<Exam?> = dao.getExamByIdFlow(id)

    override suspend fun insert(entity: Exam): Long =
        dao.insertExam(entity.copy(updatedAt = System.currentTimeMillis()))

    override suspend fun insertAll(entities: List<Exam>): List<Long> {
        val currentTime = System.currentTimeMillis()
        return dao.insertExams(entities.map { it.copy(updatedAt = currentTime) })
    }

    override suspend fun update(entity: Exam) =
        dao.updateExam(entity.copy(updatedAt = System.currentTimeMillis()))

    override suspend fun delete(entity: Exam) = dao.deleteExam(entity)

    override suspend fun deleteById(id: Long) = dao.deleteExamById(id)

    override suspend fun deleteAll() = dao.deleteAllExams()

    suspend fun deleteExamsByCourse(courseId: Long) = dao.deleteExamsByCourse(courseId)

    fun getExamsByCourse(courseId: Long): Flow<List<Exam>> = dao.getExamsByCourse(courseId)

    suspend fun getExamsByWeekRange(startWeek: Int, endWeek: Int): List<Exam> =
        dao.getExamsByWeekRange(startWeek, endWeek)

    fun getExamsByWeekRangeFlow(startWeek: Int, endWeek: Int): Flow<List<Exam>> =
        dao.getExamsByWeekRangeFlow(startWeek, endWeek)

    suspend fun getExamsWithSectionByWeek(weekNumber: Int): List<Exam> =
        dao.getExamsWithSectionByWeek(weekNumber)

    suspend fun checkExamConflict(
        weekNumber: Int,
        dayOfWeek: Int,
        startSection: Int,
        sectionCount: Int,
        excludeExamId: Long? = null
    ): List<Exam> {
        val conflicts = dao.getExamsInTimeRange(
            weekNumber, dayOfWeek, startSection, startSection + sectionCount
        )
        return if (excludeExamId != null) conflicts.filter { it.id != excludeExamId } else conflicts
    }

    suspend fun getUpcomingExams(fromWeek: Int, limit: Int = 10): List<Exam> =
        dao.getUpcomingExams(fromWeek, limit)
}
