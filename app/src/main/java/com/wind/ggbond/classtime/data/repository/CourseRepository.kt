package com.wind.ggbond.classtime.data.repository

import android.content.Context
import com.wind.ggbond.classtime.data.local.dao.CourseDao
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.util.CourseColorPalette
import com.wind.ggbond.classtime.widget.WidgetRefreshHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 课程数据仓库
 */
@Singleton
class CourseRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val courseDao: CourseDao
) {
    
    /**
     * 获取时间段内的课程（用于冲突检测）
     */
    suspend fun getCoursesInTimeRange(
        scheduleId: Long,
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int
    ): List<Course> {
        return courseDao.getCoursesInTimeRange(scheduleId, dayOfWeek, startSection, endSection)
    }
    
    fun getAllCoursesBySchedule(scheduleId: Long): Flow<List<Course>> {
        return courseDao.getAllCoursesBySchedule(scheduleId)
    }
    
    fun getCoursesByDay(scheduleId: Long, dayOfWeek: Int): Flow<List<Course>> {
        return courseDao.getCoursesByDay(scheduleId, dayOfWeek)
    }
    
    suspend fun getCourseById(courseId: Long): Course? {
        return courseDao.getCourseById(courseId)
    }
    
    fun getCourseByIdFlow(courseId: Long): Flow<Course?> {
        return courseDao.getCourseByIdFlow(courseId)
    }
    
    suspend fun insertCourse(course: Course): Long {
        val id = courseDao.insertCourse(course)
        notifyWidgetRefresh()
        return id
    }
    
    suspend fun insertCourses(courses: List<Course>): List<Long> {
        val ids = courseDao.insertCourses(courses)
        notifyWidgetRefresh()
        return ids
    }
    
    suspend fun updateCourse(course: Course) {
        courseDao.updateCourse(course.copy(updatedAt = System.currentTimeMillis()))
        notifyWidgetRefresh()
    }
    
    suspend fun deleteCourse(course: Course) {
        courseDao.deleteCourse(course)
        notifyWidgetRefresh()
    }
    
    suspend fun deleteCourseById(courseId: Long) {
        courseDao.deleteCourseById(courseId)
        notifyWidgetRefresh()
    }
    
    suspend fun deleteAllCoursesBySchedule(scheduleId: Long) {
        courseDao.deleteAllCoursesBySchedule(scheduleId)
        notifyWidgetRefresh()
    }
    
    /**
     * 通知桌面小组件刷新数据
     * 仅在有活跃 Widget 时触发，避免不必要的开销
     */
    private fun notifyWidgetRefresh() {
        if (WidgetRefreshHelper.hasActiveWidgets(context)) {
            WidgetRefreshHelper.refreshAllWidgets(context)
        }
    }
    
    /**
     * 检测课程时间冲突
     * 
     * ✅ 增强版：同时检测时间和周次冲突
     */
    suspend fun detectConflict(
        scheduleId: Long,
        dayOfWeek: Int,
        startSection: Int,
        sectionCount: Int,
        excludeCourseId: Long? = null
    ): List<Course> {
        val courses = courseDao.getCoursesInTimeRange(
            scheduleId,
            dayOfWeek,
            startSection,
            startSection + sectionCount
        )
        return courses.filter { it.id != excludeCourseId }
    }
    
    /**
     * ✅ 新增：检测课程冲突（包含周次检测）
     * 
     * @param scheduleId 课表ID
     * @param dayOfWeek 星期几
     * @param startSection 起始节次
     * @param sectionCount 节次数
     * @param weeks 周次列表
     * @param excludeCourseId 排除的课程ID（编辑时）
     * @return 冲突的课程列表
     */
    suspend fun detectConflictWithWeeks(
        scheduleId: Long,
        dayOfWeek: Int,
        startSection: Int,
        sectionCount: Int,
        weeks: List<Int>,
        excludeCourseId: Long? = null
    ): List<Course> {
        // 先获取时间范围内的所有课程
        val candidateCourses = courseDao.getCoursesInTimeRange(
            scheduleId,
            dayOfWeek,
            startSection,
            startSection + sectionCount
        ).filter { it.id != excludeCourseId }
        
        // ✅ 进一步过滤：只返回周次有交集的课程
        return candidateCourses.filter { existingCourse ->
            // 检查周次是否有交集
            existingCourse.weeks.any { it in weeks }
        }
    }
    
    /**
     * ✅ 新增：获取冲突详情
     * 
     * @return 冲突描述信息
     */
    data class ConflictInfo(
        val conflictingCourse: Course,
        val conflictWeeks: List<Int>,
        val conflictSections: IntRange
    )
    
    suspend fun getConflictDetails(
        scheduleId: Long,
        dayOfWeek: Int,
        startSection: Int,
        sectionCount: Int,
        weeks: List<Int>,
        excludeCourseId: Long? = null
    ): List<ConflictInfo> {
        val conflicts = detectConflictWithWeeks(
            scheduleId, dayOfWeek, startSection, sectionCount, weeks, excludeCourseId
        )
        
        return conflicts.map { course ->
            val conflictWeeks = course.weeks.filter { it in weeks }
            val conflictSections = maxOf(startSection, course.startSection)..
                    minOf(startSection + sectionCount - 1, course.startSection + course.sectionCount - 1)
            
            ConflictInfo(
                conflictingCourse = course,
                conflictWeeks = conflictWeeks,
                conflictSections = conflictSections
            )
        }
    }
    
    /**
     * 批量更新课程颜色（根据课程名称自动分配）
     * 用于将旧课程更新为新的颜色方案
     * 智能分配：确保不同课程使用不同颜色
     * 
     * @param scheduleId 课表ID，如果为null则更新所有课表
     * @return 更新的课程数量
     */
    suspend fun updateCoursesColor(scheduleId: Long? = null): Int {
        val courses = if (scheduleId != null) {
            getAllCoursesBySchedule(scheduleId).first()
        } else {
            courseDao.getAllCourses()
        }
        
        // 清除颜色缓存，重新分配
        CourseColorPalette.clearCache()
        
        // 获取所有不同的课程名称
        val courseNames = courses.map { it.courseName }.distinct().shuffled()
        
        // 批量分配颜色，确保不同课程使用不同颜色
        val colorMapping = CourseColorPalette.assignColorsForCourses(courseNames)
        
        var updatedCount = 0
        val toUpdate = mutableListOf<Course>()
        courses.forEach { course ->
            val newColor = colorMapping[course.courseName] ?: CourseColorPalette.getColorByIndex(0)
            if (course.color != newColor) {
                toUpdate.add(course.copy(color = newColor))
                updatedCount++
            }
        }
        if (toUpdate.isNotEmpty()) {
            courseDao.updateCourses(toUpdate)
            notifyWidgetRefresh()
        }
        
        return updatedCount
    }
    
    /**
     * ✅ 新增：批量开启所有课程的提醒
     * 
     * @param scheduleId 课表ID
     * @param defaultReminderMinutes 默认提醒时间（分钟）
     * @return 更新的课程数量
     */
    suspend fun enableAllCoursesReminder(scheduleId: Long, defaultReminderMinutes: Int): Int {
        var courses: List<Course> = emptyList()
        var retryCount = 0
        val maxRetries = 3
        
        while (courses.isEmpty() && retryCount < maxRetries) {
            courses = try {
                getAllCoursesBySchedule(scheduleId).first()
            } catch (e: Exception) {
                AppLogger.w("CourseRepository", "获取课程数据失败，重试 ${retryCount + 1}/$maxRetries")
                emptyList()
            }
            
            if (courses.isEmpty()) {
                retryCount++
                if (retryCount < maxRetries) {
                    kotlinx.coroutines.delay(100)
                }
            }
        }
        
        if (courses.isEmpty()) {
            AppLogger.w("CourseRepository", "经过 $maxRetries 次重试后仍无法获取课程数据，scheduleId: $scheduleId")
            return 0
        }
        
        AppLogger.d("CourseRepository", "成功获取到 ${courses.size} 门课程，开始批量开启提醒")

        courseDao.updateReminderBySchedule(
            scheduleId = scheduleId,
            enabled = true,
            minutes = defaultReminderMinutes,
            updatedAt = System.currentTimeMillis()
        )
        notifyWidgetRefresh()

        AppLogger.d("CourseRepository", "批量开启提醒完成")
        return courses.size
    }
    
    suspend fun disableAllCoursesReminder(scheduleId: Long): Int {
        var courses: List<Course> = emptyList()
        var retryCount = 0
        val maxRetries = 3
        
        while (courses.isEmpty() && retryCount < maxRetries) {
            courses = try {
                getAllCoursesBySchedule(scheduleId).first()
            } catch (e: Exception) {
                AppLogger.w("CourseRepository", "获取课程数据失败，重试 ${retryCount + 1}/$maxRetries")
                emptyList()
            }
            
            if (courses.isEmpty()) {
                retryCount++
                if (retryCount < maxRetries) {
                    kotlinx.coroutines.delay(100)
                }
            }
        }
        
        if (courses.isEmpty()) {
            AppLogger.w("CourseRepository", "经过 $maxRetries 次重试后仍无法获取课程数据，scheduleId: $scheduleId")
            return 0
        }
        
        AppLogger.d("CourseRepository", "成功获取到 ${courses.size} 门课程，开始批量关闭提醒")

        val updatedCount = courseDao.updateReminderBySchedule(
            scheduleId = scheduleId,
            enabled = false,
            minutes = 0,
            updatedAt = System.currentTimeMillis()
        )
        if (updatedCount > 0) notifyWidgetRefresh()

        AppLogger.d("CourseRepository", "批量关闭提醒完成，共更新 $updatedCount 门课程")
        return updatedCount
    }
}
