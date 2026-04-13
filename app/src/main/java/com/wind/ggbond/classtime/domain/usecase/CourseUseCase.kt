package com.wind.ggbond.classtime.domain.usecase

import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.util.Constants
import com.wind.ggbond.classtime.util.CourseColorProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class ConflictResult(
    val hasConflict: Boolean,
    val conflictingCourses: List<Course>,
    val message: String?
)

data class DeleteCourseForWeekResult(
    val wasFullDelete: Boolean,
    val originalCourse: Course,
    val weekNumber: Int
)

@Singleton
class CourseUseCase @Inject constructor(
    private val courseRepository: CourseRepository
) {
    
    companion object {
        private const val CACHE_MAX_SIZE = Constants.UI.LRU_CACHE_MAX_SIZE
        private const val TAG = "CourseUseCase"
    }
    
    private val weekCoursesCache = ConcurrentHashMap<Int, Map<Int, List<Course>>>()
    
    fun getCoursesBySchedule(scheduleId: Long): Flow<List<Course>> {
        return courseRepository.getAllCoursesBySchedule(scheduleId)
    }
    
    fun getCoursesForWeek(
        courses: List<Course>,
        weekNumber: Int
    ): Map<Int, List<Course>> {
        return weekCoursesCache.computeIfAbsent(weekNumber) {
            if (weekCoursesCache.size >= CACHE_MAX_SIZE) {
                weekCoursesCache.keys.firstOrNull()?.let { key -> weekCoursesCache.remove(key) }
            }
            (1..7).associateWith { dayOfWeek ->
                courses.filter {
                    it.dayOfWeek == dayOfWeek && weekNumber in it.weeks
                }.sortedBy { it.startSection }
            }
        }
    }
    
    suspend fun preloadAdjacentWeeks(
        coursesList: List<Course>,
        currentWeek: Int,
        totalWeeks: Int
    ) {
        val weeksToPreload = listOf(
            currentWeek - 2, currentWeek - 1,
            currentWeek + 1, currentWeek + 2
        )

        weeksToPreload.forEach { week ->
            if (week in 1..totalWeeks) {
                weekCoursesCache.computeIfAbsent(week) {
                    (1..7).associateWith { dayOfWeek ->
                        coursesList.filter {
                            it.dayOfWeek == dayOfWeek && week in it.weeks
                        }.sortedBy { it.startSection }
                    }
                }
            }
        }
    }
    
    fun clearCache() {
        weekCoursesCache.clear()
    }
    
    suspend fun deleteCourse(course: Course) {
        weekCoursesCache.clear()
        courseRepository.deleteCourse(course)
    }
    
    suspend fun updateCourse(course: Course) {
        weekCoursesCache.clear()
        courseRepository.updateCourse(course)
    }
    
    suspend fun insertCourse(course: Course): Long {
        weekCoursesCache.clear()
        return courseRepository.insertCourse(course)
    }
    
    suspend fun getCourseById(courseId: Long): Course? {
        return courseRepository.getCourseById(courseId)
    }
    
    suspend fun detectConflict(
        scheduleId: Long,
        dayOfWeek: Int,
        startSection: Int,
        sectionCount: Int,
        excludeCourseId: Long? = null
    ): List<Course> {
        require(dayOfWeek in Constants.Course.MIN_DAY_OF_WEEK..Constants.Course.MAX_DAY_OF_WEEK) { "dayOfWeek 超出范围: $dayOfWeek" }
        require(startSection >= Constants.Course.MIN_SECTION_NUMBER) { "startSection 超出范围: $startSection" }
        require(sectionCount >= 1) { "sectionCount 必须大于0: $sectionCount" }
        return courseRepository.detectConflict(
            scheduleId,
            dayOfWeek,
            startSection,
            sectionCount,
            excludeCourseId
        )
    }
    
    suspend fun detectConflictWithWeeks(
        scheduleId: Long,
        dayOfWeek: Int,
        startSection: Int,
        sectionCount: Int,
        weeks: List<Int>,
        excludeCourseId: Long? = null
    ): List<Course> {
        require(dayOfWeek in Constants.Course.MIN_DAY_OF_WEEK..Constants.Course.MAX_DAY_OF_WEEK) { "dayOfWeek 超出范围: $dayOfWeek" }
        require(startSection >= Constants.Course.MIN_SECTION_NUMBER) { "startSection 超出范围: $startSection" }
        require(sectionCount >= 1) { "sectionCount 必须大于0: $sectionCount" }
        return courseRepository.detectConflictWithWeeks(
            scheduleId,
            dayOfWeek,
            startSection,
            sectionCount,
            weeks,
            excludeCourseId
        )
    }
    
    suspend fun checkCourseConflict(
        scheduleId: Long,
        dayOfWeek: Int,
        startSection: Int,
        sectionCount: Int,
        weeks: List<Int>,
        excludeCourseId: Long? = null
    ): ConflictResult {
        val conflicts = detectConflictWithWeeks(
            scheduleId, dayOfWeek, startSection, sectionCount, weeks, excludeCourseId
        )
        return if (conflicts.isNotEmpty()) {
            val conflictNames = conflicts.joinToString(", ") { it.courseName }
            ConflictResult(
                hasConflict = true,
                conflictingCourses = conflicts,
                message = "课程时间冲突：与「$conflictNames」冲突"
            )
        } else {
            ConflictResult(
                hasConflict = false,
                conflictingCourses = emptyList(),
                message = null
            )
        }
    }
    
    suspend fun getColorForCourse(courseName: String, existingColors: List<String>): String {
        return CourseColorProvider.getColorForCourse(courseName, existingColors)
    }
    
    suspend fun getExistingCoursesColors(scheduleId: Long): List<String> {
        return try {
            courseRepository.getAllCoursesBySchedule(scheduleId).first().map { it.color }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun deleteCourseForWeek(course: Course, weekNumber: Int): DeleteCourseForWeekResult {
        return if (course.weeks.size == 1) {
            deleteCourse(course)
            DeleteCourseForWeekResult(wasFullDelete = true, originalCourse = course, weekNumber = weekNumber)
        } else {
            val updatedWeeks = course.weeks.filter { it != weekNumber }
            if (updatedWeeks.isNotEmpty()) {
                updateCourse(course.copy(
                    weeks = updatedWeeks,
                    updatedAt = System.currentTimeMillis()
                ))
                DeleteCourseForWeekResult(wasFullDelete = false, originalCourse = course, weekNumber = weekNumber)
            } else {
                deleteCourse(course)
                DeleteCourseForWeekResult(wasFullDelete = true, originalCourse = course, weekNumber = weekNumber)
            }
        }
    }
    
    suspend fun undoDeleteCourse(originalCourse: Course, weekNumber: Int, wasFullDelete: Boolean) {
        if (wasFullDelete) {
            insertCourse(originalCourse.copy(id = 0))
        } else {
            val currentCourse = getCourseById(originalCourse.id)
            if (currentCourse != null) {
                val restoredWeeks = (currentCourse.weeks + weekNumber).sorted()
                updateCourse(currentCourse.copy(
                    weeks = restoredWeeks,
                    updatedAt = System.currentTimeMillis()
                ))
            } else {
                insertCourse(originalCourse.copy(id = 0))
            }
        }
    }
}