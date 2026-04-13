package com.wind.ggbond.classtime.domain.usecase

import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.CourseAdjustment
import com.wind.ggbond.classtime.data.local.entity.Reminder
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.data.repository.ReminderRepository
import com.wind.ggbond.classtime.ui.screen.reminder.ReminderFilterType
import com.wind.ggbond.classtime.util.DateUtils
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderUseCase @Inject constructor(
    private val reminderRepository: ReminderRepository
) {

    companion object {
        val NEXT_COURSE_SECTIONS = listOf(2, 4, 6, 8, 10)
        const val CLASS_END_REMINDER_MINUTES = 1
        const val NEXT_COURSE_REMINDER_MINUTES = 1
        const val MS_PER_DAY = 24 * 60 * 60 * 1000L
    }

    data class NextCourseReminderInfo(
        val nextCourse: Course,
        val triggerTime: Long,
        val isSameCourseAndClassroom: Boolean
    )

    data class ReminderStats(
        val totalReminders: Int,
        val activeReminders: Int,
        val todayReminders: Int,
        val upcomingReminders: Int
    )

    fun calculateReminderTime(
        course: Course,
        schedule: Schedule,
        weekNumber: Int,
        classTimes: List<ClassTime>
    ): Long? {
        val courseDate = calculateCourseDate(schedule, weekNumber, course.dayOfWeek)
        if (isCourseDatePassed(courseDate)) return null

        val classTime = classTimes.find { it.sectionNumber == course.startSection } ?: return null
        val reminderDateTime = LocalDateTime.of(courseDate, classTime.startTime)
            .minusMinutes(course.reminderMinutes.toLong())
        if (isReminderDateTimePassed(reminderDateTime)) return null

        return reminderDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun calculateAdjustedReminderTime(
        course: Course,
        schedule: Schedule,
        adjustment: CourseAdjustment,
        classTimes: List<ClassTime>
    ): Long? {
        val courseDate = calculateCourseDate(schedule, adjustment.newWeekNumber, adjustment.newDayOfWeek)
        if (isCourseDatePassed(courseDate)) return null

        val classTime = classTimes.find { it.sectionNumber == adjustment.newStartSection } ?: return null
        val reminderDateTime = LocalDateTime.of(courseDate, classTime.startTime)
            .minusMinutes(course.reminderMinutes.toLong())
        if (isReminderDateTimePassed(reminderDateTime)) return null

        return reminderDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun calculateNextCourseReminderInfo(
        currentCourse: Course,
        schedule: Schedule,
        weekNumber: Int,
        classTimes: List<ClassTime>,
        allCourses: List<Course>
    ): NextCourseReminderInfo? {
        val endSection = currentCourse.startSection + currentCourse.sectionCount - 1
        if (!shouldCheckNextCourseReminder(endSection)) return null

        val endClassTime = classTimes.find { it.sectionNumber == endSection } ?: return null
        val monday = DateUtils.getMondayOfWeek(schedule.startDate, weekNumber)
        val courseDate = monday.plusDays((currentCourse.dayOfWeek - 1).toLong())
        val endTime = LocalDateTime.of(courseDate, endClassTime.endTime)
        if (endTime.isBefore(LocalDateTime.now())) return null

        val nextCourse = findNextCourse(
            currentCourse, schedule, weekNumber, classTimes, allCourses
        ) ?: return null

        val isSame = isSameCourseAndClassroom(currentCourse, nextCourse)
        val reminderTime = endTime.minusMinutes(NEXT_COURSE_REMINDER_MINUTES.toLong())
        if (reminderTime.isBefore(LocalDateTime.now())) return null

        val millis = reminderTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return NextCourseReminderInfo(nextCourse, millis, isSame)
    }

    fun calculateClassEndReminderTime(
        course: Course,
        schedule: Schedule,
        weekNumber: Int,
        classTimes: List<ClassTime>
    ): Long? {
        val endSection = course.startSection + course.sectionCount - 1
        val endClassTime = classTimes.find { it.sectionNumber == endSection } ?: return null

        val courseDate = calculateCourseDate(schedule, weekNumber, course.dayOfWeek)
        val endTime = LocalDateTime.of(courseDate, endClassTime.endTime)
        if (endTime.isBefore(LocalDateTime.now())) return null

        val reminderTime = endTime.minusMinutes(CLASS_END_REMINDER_MINUTES.toLong())
        if (reminderTime.isBefore(LocalDateTime.now())) return null

        return reminderTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun calculateCourseDate(schedule: Schedule, weekNumber: Int, dayOfWeek: Int): LocalDate {
        val monday = DateUtils.getMondayOfWeek(schedule.startDate, weekNumber)
        return monday.plusDays((dayOfWeek - 1).toLong())
    }

    fun findNextCourse(
        currentCourse: Course,
        schedule: Schedule,
        weekNumber: Int,
        classTimes: List<ClassTime>,
        allCourses: List<Course>
    ): Course? {
        val endSection = currentCourse.startSection + currentCourse.sectionCount - 1
        val endClassTime = classTimes.find { it.sectionNumber == endSection } ?: return null

        val monday = DateUtils.getMondayOfWeek(schedule.startDate, weekNumber)
        val courseDate = monday.plusDays((currentCourse.dayOfWeek - 1).toLong())
        val endTime = LocalDateTime.of(courseDate, endClassTime.endTime)

        val nextSectionNumber = endSection + 1

        val candidates = allCourses.filter { course ->
            course.dayOfWeek == currentCourse.dayOfWeek && weekNumber in course.weeks
        }.filter { course ->
            val ct = classTimes.find { it.sectionNumber == course.startSection } ?: return@filter false
            val cs = LocalDateTime.of(monday.plusDays((course.dayOfWeek - 1).toLong()), ct.startTime)
            cs.isAfter(endTime) && cs.isBefore(endTime.plusHours(1)) && course.startSection >= nextSectionNumber
        }

        return candidates.find { it.startSection == nextSectionNumber }
    }

    fun isSameCourseAndClassroom(course1: Course, course2: Course): Boolean =
        course1.courseName == course2.courseName && course1.classroom == course2.classroom

    fun shouldCheckNextCourseReminder(endSection: Int): Boolean =
        endSection in NEXT_COURSE_SECTIONS

    suspend fun getReminderStats(): ReminderStats {
        val allReminders = reminderRepository.getAll()
        val now = System.currentTimeMillis()
        val todayStart = LocalDate.now().atStartOfDay()
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayEnd = todayStart + MS_PER_DAY

        return ReminderStats(
            totalReminders = allReminders.size,
            activeReminders = allReminders.count { it.triggerTime > now },
            todayReminders = allReminders.count { it.triggerTime in todayStart..todayEnd && it.isEnabled },
            upcomingReminders = allReminders.count {
                it.triggerTime in (todayEnd + 1)..(todayEnd + 7 * MS_PER_DAY) && it.isEnabled
            }
        )
    }

    fun filterReminders(reminders: List<Reminder>, filterType: ReminderFilterType): List<Reminder> {
        val now = System.currentTimeMillis()
        val todayStart = LocalDate.now().atStartOfDay()
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayEnd = todayStart + MS_PER_DAY
        val weekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY).atStartOfDay()
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val weekEnd = weekStart + 7 * MS_PER_DAY

        return when (filterType) {
            ReminderFilterType.ALL -> reminders
            ReminderFilterType.TODAY -> reminders.filter { it.triggerTime in todayStart until todayEnd }
            ReminderFilterType.THIS_WEEK -> reminders.filter { it.triggerTime in weekStart until weekEnd }
            ReminderFilterType.EXPIRED -> reminders.filter { it.triggerTime < now }
        }
    }

    fun groupRemindersByDate(reminders: List<Reminder>): Map<LocalDate, List<Reminder>> {
        return reminders.groupBy { reminder ->
            LocalDateTime.ofInstant(
                Instant.ofEpochMilli(reminder.triggerTime),
                ZoneId.systemDefault()
            ).toLocalDate()
        }.toSortedMap()
    }

    fun isReminderExpired(triggerTime: Long): Boolean = triggerTime < System.currentTimeMillis()

    fun isCourseDatePassed(courseDate: LocalDate): Boolean = courseDate.isBefore(LocalDate.now())

    fun isReminderDateTimePassed(reminderDateTime: LocalDateTime): Boolean =
        reminderDateTime.isBefore(LocalDateTime.now())

    suspend fun deleteReminder(reminder: Reminder) = reminderRepository.delete(reminder)

    suspend fun cleanExpiredReminders() {
        reminderRepository.deleteExpiredReminders()
    }

    fun toggleReminderEnabled(reminder: Reminder): Reminder =
        reminder.copy(isEnabled = !reminder.isEnabled)
}
