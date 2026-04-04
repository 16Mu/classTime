package com.wind.ggbond.classtime.service.helper

import android.util.Log
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.util.DateUtils
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderSchedulingHelper @Inject constructor() {

    private val TAG = "ReminderSchedulingHelper"

    fun calculateWeekReminderTime(
        course: Course,
        schedule: Schedule,
        weekNumber: Int,
        classTimes: List<ClassTime>
    ): LocalDateTime? {
        return try {
            val monday = DateUtils.getMondayOfWeek(schedule.startDate, weekNumber)
            val courseDate = monday.plusDays((course.dayOfWeek - 1).toLong())

            if (courseDate.isBefore(LocalDate.now())) {
                return null
            }

            val classTime = classTimes.find { it.sectionNumber == course.startSection }
                ?: run {
                    Log.w(TAG, "未找到节次 ${course.startSection} 的时间配置")
                    return null
                }

            val courseDateTime = LocalDateTime.of(courseDate, classTime.startTime)
            val reminderDateTime = courseDateTime.minusMinutes(course.reminderMinutes.toLong())

            if (reminderDateTime.isBefore(LocalDateTime.now())) {
                return null
            }

            reminderDateTime
        } catch (e: Exception) {
            Log.e(TAG, "计算提醒时间失败: ${e.message}", e)
            null
        }
    }

    fun calculateAdjustedReminderTime(
        course: Course,
        schedule: Schedule,
        adjustment: com.wind.ggbond.classtime.data.local.entity.CourseAdjustment,
        classTimes: List<ClassTime>
    ): LocalDateTime? {
        return try {
            val monday = DateUtils.getMondayOfWeek(schedule.startDate, adjustment.newWeekNumber)
            val courseDate = monday.plusDays((adjustment.newDayOfWeek - 1).toLong())

            if (courseDate.isBefore(LocalDate.now())) return null

            val classTime = classTimes.find { it.sectionNumber == adjustment.newStartSection }
                ?: return null

            val courseDateTime = LocalDateTime.of(courseDate, classTime.startTime)
            courseDateTime.minusMinutes(course.reminderMinutes.toLong())
        } catch (e: Exception) {
            Log.e(TAG, "计算调课提醒时间失败", e)
            null
        }
    }

    fun shouldScheduleNextCourseReminder(currentEndSection: Int): Boolean {
        return currentEndSection in listOf(2, 4, 6, 8, 10)
    }

    suspend fun findNextCoursesInTimeRange(
        scheduleId: Long,
        dayOfWeek: Int,
        startSection: Int,
        weekNumber: Int,
        schedule: Schedule,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        classTimes: List<ClassTime>,
        courseRepository: com.wind.ggbond.classtime.data.repository.CourseRepository
    ): List<Course> {
        val allCourses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            .filter { 
                it.dayOfWeek == dayOfWeek && 
                weekNumber in it.weeks
            }

        return allCourses.filter { course ->
            val courseStartClassTime = classTimes.find { it.sectionNumber == course.startSection }
                ?: return@filter false

            val monday = DateUtils.getMondayOfWeek(schedule.startDate, weekNumber)
            val courseDate = monday.plusDays((course.dayOfWeek - 1).toLong())
            val courseStartTime = LocalDateTime.of(courseDate, courseStartClassTime.startTime)

            courseStartTime.isAfter(startTime) && 
            courseStartTime.isBefore(endTime) &&
            course.startSection >= startSection
        }
    }

    fun calculateNextCourseReminderTime(
        currentCourse: Course,
        schedule: Schedule,
        weekNumber: Int,
        classTimes: List<ClassTime>
    ): LocalDateTime? {
        return try {
            val currentEndSection = currentCourse.startSection + currentCourse.sectionCount - 1

            val currentEndClassTime = classTimes.find { it.sectionNumber == currentEndSection }
                ?: return null

            val monday = DateUtils.getMondayOfWeek(schedule.startDate, weekNumber)
            val courseDate = monday.plusDays((currentCourse.dayOfWeek - 1).toLong())
            val currentCourseEndTime = LocalDateTime.of(courseDate, currentEndClassTime.endTime)

            if (currentCourseEndTime.isBefore(LocalDateTime.now())) {
                return null
            }

            currentCourseEndTime.minusMinutes(1)
        } catch (e: Exception) {
            Log.e(TAG, "计算下节课提醒时间失败", e)
            null
        }
    }
}
