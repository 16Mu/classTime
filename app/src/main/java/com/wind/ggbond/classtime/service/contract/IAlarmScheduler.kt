package com.wind.ggbond.classtime.service.contract

import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.CourseAdjustment
import com.wind.ggbond.classtime.data.local.entity.Reminder

interface IAlarmScheduler {

    suspend fun scheduleCourseReminders(course: Course)

    suspend fun scheduleAllCourseReminders(scheduleId: Long)

    suspend fun cancelCourseReminders(courseId: Long)

    suspend fun cancelAllReminders()

    suspend fun cancelAllCourseReminders(scheduleId: Long)

    suspend fun rescheduleAllReminders()

    suspend fun cleanExpiredReminders()

    suspend fun getReminderStats(): ReminderStats

    suspend fun updateReminderTime(courseId: Long, newReminderMinutes: Int)

    suspend fun rescheduleRemindersForAdjustment(adjustment: CourseAdjustment)

    suspend fun toggleCourseReminder(courseId: Long, enabled: Boolean)

    suspend fun toggleSingleReminder(reminder: Reminder, enabled: Boolean)

    suspend fun getTodayReminders(): List<Reminder>

    suspend fun getUpcomingReminders(): List<Reminder>

    fun scheduleTestAlarm(
        courseId: Long,
        weekNumber: Int,
        triggerTime: Long,
        isNextCourse: Boolean = false,
        currentCourseName: String = "",
        isSameCourseClassroom: Boolean = false,
        isClassEnd: Boolean = false
    ): Boolean

    data class ReminderStats(
        val totalReminders: Int,
        val activeReminders: Int,
        val todayReminders: Int,
        val upcomingReminders: Int
    )
}
