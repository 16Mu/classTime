package com.wind.ggbond.classtime.domain.usecase

import com.wind.ggbond.classtime.data.local.entity.Exam
import com.wind.ggbond.classtime.data.repository.ExamRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExamUseCase @Inject constructor(
    private val examRepository: ExamRepository
) {
    
    fun getExamsByWeekRangeFlow(startWeek: Int, endWeek: Int): Flow<List<Exam>> {
        return examRepository.getExamsByWeekRangeFlow(startWeek, endWeek)
    }
    
    suspend fun getExamsWithSectionByWeek(weekNumber: Int): List<Exam> {
        return examRepository.getExamsWithSectionByWeek(weekNumber)
    }
}