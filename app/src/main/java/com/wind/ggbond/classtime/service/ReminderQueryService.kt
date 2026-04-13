package com.wind.ggbond.classtime.service

import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.Reminder
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.data.repository.ClassTimeRepository
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ReminderRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.domain.usecase.ReminderUseCase
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderQueryService @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val classTimeRepository: ClassTimeRepository,
    private val courseRepository: CourseRepository,
    private val reminderRepository: ReminderRepository
) {

    suspend fun getCurrentSchedule(): Schedule? =
        scheduleRepository.getCurrentSchedule()

    suspend fun getClassTimes(): List<ClassTime> =
        classTimeRepository.getClassTimesByConfigSync()

    suspend fun getCoursesBySchedule(scheduleId: Long): List<Course> =
        courseRepository.getAllCoursesBySchedule(scheduleId).first()

    suspend fun getCourseById(courseId: Long): Course? =
        courseRepository.getCourseById(courseId)

    suspend fun getAllReminders(): List<Reminder> =
        reminderRepository.getAll()

    suspend fun getRemindersByCourse(courseId: Long): List<Reminder> =
        reminderRepository.getAll().filter { it.courseId == courseId }

    suspend fun getRemindersByCourseAndWeek(courseId: Long, weekNumber: Int): List<Reminder> =
        reminderRepository.getAll().filter { it.courseId == courseId && it.weekNumber == weekNumber }

    suspend fun insertReminder(reminder: Reminder) =
        reminderRepository.insert(reminder)

    suspend fun deleteReminder(reminder: Reminder) =
        reminderRepository.delete(reminder)

    suspend fun deleteRemindersByCourse(courseId: Long) =
        reminderRepository.deleteRemindersByCourse(courseId)

    suspend fun deleteAllReminders() =
        reminderRepository.deleteAll()

    suspend fun getTodayReminders(): List<Reminder> {
        val todayStart = LocalDate.now().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return reminderRepository.getAll().filter { it.triggerTime in todayStart..(todayStart + ReminderUseCase.MS_PER_DAY) && it.isEnabled }
    }

    suspend fun getUpcomingReminders(): List<Reminder> {
        val now = System.currentTimeMillis()
        return reminderRepository.getAll().filter { it.triggerTime in now..(now + 60 * 60 * 1000) && it.isEnabled }
    }
}
