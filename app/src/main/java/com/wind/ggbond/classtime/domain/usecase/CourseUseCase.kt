package com.wind.ggbond.classtime.domain.usecase

import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.util.Constants
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

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
        return weekCoursesCache[weekNumber] ?: run {
            val coursesMap = (1..7).associateWith { dayOfWeek ->
                courses.filter {
                    it.dayOfWeek == dayOfWeek && weekNumber in it.weeks
                }.sortedBy { it.startSection }
            }
            
            if (weekCoursesCache.size >= CACHE_MAX_SIZE) {
                weekCoursesCache.keys.firstOrNull()?.let { weekCoursesCache.remove(it) }
            }
            
            weekCoursesCache[weekNumber] = coursesMap
            coursesMap
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
        
        val preparedMaps = mutableMapOf<Int, Map<Int, List<Course>>>()
        
        weeksToPreload.forEach { week ->
            if (week in 1..totalWeeks && !weekCoursesCache.containsKey(week)) {
                val coursesMap = (1..7).associateWith { dayOfWeek ->
                    coursesList.filter {
                        it.dayOfWeek == dayOfWeek && week in it.weeks
                    }.sortedBy { it.startSection }
                }
                preparedMaps[week] = coursesMap
            }
        }
        
        preparedMaps.forEach { (week, map) ->
            weekCoursesCache.putIfAbsent(week, map)
        }
    }
    
    fun clearCache() {
        weekCoursesCache.clear()
    }
    
    suspend fun deleteCourse(course: Course) {
        courseRepository.deleteCourse(course)
    }
    
    suspend fun updateCourse(course: Course) {
        courseRepository.updateCourse(course)
    }
    
    suspend fun insertCourse(course: Course): Long {
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
        return courseRepository.detectConflictWithWeeks(
            scheduleId,
            dayOfWeek,
            startSection,
            sectionCount,
            weeks,
            excludeCourseId
        )
    }
}