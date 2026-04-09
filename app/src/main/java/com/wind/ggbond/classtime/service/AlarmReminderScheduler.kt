package com.wind.ggbond.classtime.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.CourseAdjustment
import com.wind.ggbond.classtime.data.local.entity.Reminder
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.data.repository.ClassTimeRepository
import com.wind.ggbond.classtime.data.repository.CourseAdjustmentRepository
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ReminderRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.receiver.AlarmReminderReceiver
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.util.DateUtils
import com.wind.ggbond.classtime.service.contract.IAlarmScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminderRepository: ReminderRepository,
    private val scheduleRepository: ScheduleRepository,
    private val classTimeRepository: ClassTimeRepository,
    private val courseRepository: CourseRepository,
    private val adjustmentRepository: CourseAdjustmentRepository
) : IAlarmScheduler {

    companion object {
        private const val TAG = "AlarmReminderScheduler"
        private val NEXT_COURSE_SECTIONS = listOf(2, 4, 6, 8, 10)
        private const val CLASS_END_REMINDER_MINUTES = 1
        private const val NEXT_COURSE_REMINDER_MINUTES = 1
        private const val MS_PER_DAY = 24 * 60 * 60 * 1000L
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // ==================== 共享辅助方法 ====================

    private fun createReminderIntent(
        courseId: Long = 0,
        weekNumber: Int = 0,
        isNextCourse: Boolean = false,
        currentCourseName: String = "",
        isSameCourseClassroom: Boolean = false,
        isClassEnd: Boolean = false
    ) = Intent(context, AlarmReminderReceiver::class.java).apply {
        action = AlarmReminderReceiver.ACTION_COURSE_REMINDER
        if (courseId > 0) putExtra(AlarmReminderReceiver.EXTRA_COURSE_ID, courseId)
        if (weekNumber > 0) putExtra(AlarmReminderReceiver.EXTRA_WEEK_NUMBER, weekNumber)
        if (isNextCourse) putExtra(AlarmReminderReceiver.EXTRA_IS_NEXT_COURSE, true)
        if (currentCourseName.isNotEmpty()) putExtra(AlarmReminderReceiver.EXTRA_CURRENT_COURSE_NAME, currentCourseName)
        if (isSameCourseClassroom) putExtra(AlarmReminderReceiver.EXTRA_IS_SAME_COURSE_CLASSROOM, true)
        if (isClassEnd) putExtra(AlarmReminderReceiver.EXTRA_IS_CLASS_END, true)
    }

    private fun createPendingIntent(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun cancelAllTypesForReminder(courseId: Long, weekNumber: Int) {
        listOf(
            Pair(false, false),
            Pair(true, false),
            Pair(false, true)
        ).forEach { (isNext, isClassEnd) ->
            val code = AlarmReminderReceiver.generateRequestCode(courseId, weekNumber, isNext, isClassEnd)
            alarmManager.cancel(createPendingIntent(code, createReminderIntent()))
        }
    }

    private fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun setAlarmWithFallback(triggerTime: Long, pendingIntent: PendingIntent): Boolean {
        return try {
            if (canScheduleExactAlarms()) {
                setExactAlarm(triggerTime, pendingIntent)
            } else {
                setInexactAlarm(triggerTime, pendingIntent)
            }
            true
        } catch (e: SecurityException) {
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                true
            } catch (fallbackEx: Exception) {
                AppLogger.e(TAG, "降级闹钟也失败", fallbackEx)
                false
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "设置闹钟失败", e)
            false
        }
    }

    private fun setExactAlarm(triggerTime: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pi)
        }
    }

    private fun setInexactAlarm(triggerTime: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pi)
        }
    }

    // ==================== 公开调度方法 ====================

    suspend override fun scheduleCourseReminders(course: Course) {
        if (!course.reminderEnabled) return

        val schedule = scheduleRepository.getCurrentSchedule() ?: return
        val classTimes = classTimeRepository.getClassTimesByConfigSync()
        cancelCourseReminders(course.id)

        var scheduledCount = 0
        var failedCount = 0
        course.weeks.forEach { week ->
            if (scheduleWeekReminder(course, schedule, week, classTimes)) scheduledCount++ else failedCount++
        }
        AppLogger.d(TAG, "课程 ${course.courseName}: $scheduledCount 个提醒创建成功, $failedCount 失败")
    }

    suspend override fun scheduleAllCourseReminders(scheduleId: Long) {
        val courses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            .filter { it.reminderEnabled }
        if (courses.isEmpty()) return

        val schedule = scheduleRepository.getCurrentSchedule() ?: return
        val classTimes = classTimeRepository.getClassTimesByConfigSync()
        cancelAllReminders()

        var totalScheduled = 0
        var totalFailed = 0
        courses.forEach { course ->
            try {
                course.weeks.forEach { week ->
                    if (scheduleWeekReminder(course, schedule, week, classTimes)) totalScheduled++
                    else totalFailed++
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "课程 ${course.courseName} 调度异常: ${e.message}")
                totalFailed++
            }
        }
        if (totalScheduled == 0 && courses.isNotEmpty()) {
            AppLogger.e(TAG, "批量创建全部失败！共 $totalFailed 个")
        }
        AppLogger.d(TAG, "批量完成: $totalScheduled 成功, $totalFailed 失败")
    }

    // ==================== 核心调度逻辑 ====================

    private suspend fun scheduleWeekReminder(
        course: Course, schedule: Schedule, weekNumber: Int, classTimes: List<ClassTime>
    ): Boolean {
        return try {
        val monday = DateUtils.getMondayOfWeek(schedule.startDate, weekNumber)
        val courseDate = monday.plusDays((course.dayOfWeek - 1).toLong())
        if (courseDate.isBefore(LocalDate.now())) return false

        val classTime = classTimes.find { it.sectionNumber == course.startSection } ?: return false
        val reminderDateTime = LocalDateTime.of(courseDate, classTime.startTime)
            .minusMinutes(course.reminderMinutes.toLong())
        if (reminderDateTime.isBefore(LocalDateTime.now())) return false

        val reminderTime = reminderDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (!scheduleAlarm(course.id, weekNumber, reminderTime, isClassEnd = false)) return false

        reminderRepository.insert(Reminder(
            courseId = course.id, minutesBefore = course.reminderMinutes,
            isEnabled = true, weekNumber = weekNumber, dayOfWeek = course.dayOfWeek,
            triggerTime = reminderTime, workRequestId = "alarm_${course.id}_$weekNumber"
        ))

        scheduleNextCourseReminder(course, schedule, weekNumber, classTimes)
        scheduleClassEndReminder(course, schedule, weekNumber, classTimes, courseDate)
        true
        } catch (e: Exception) {
            AppLogger.e(TAG, "创建提醒失败", e)
            false
        }
    }

    private fun scheduleAlarm(
        courseId: Long, weekNumber: Int, triggerTime: Long,
        isNextCourse: Boolean = false, currentCourseName: String = "",
        isSameCourseClassroom: Boolean = false, isClassEnd: Boolean
    ): Boolean {
        val intent = createReminderIntent(courseId, weekNumber, isNextCourse, currentCourseName, isSameCourseClassroom, isClassEnd)
        val requestCode = AlarmReminderReceiver.generateRequestCode(courseId, weekNumber, isNextCourse, isClassEnd)
        return setAlarmWithFallback(triggerTime, createPendingIntent(requestCode, intent))
    }

    override fun scheduleTestAlarm(
        courseId: Long, weekNumber: Int, triggerTime: Long,
        isNextCourse: Boolean, currentCourseName: String,
        isSameCourseClassroom: Boolean, isClassEnd: Boolean
    ): Boolean = scheduleAlarm(courseId, weekNumber, triggerTime, isNextCourse, currentCourseName, isSameCourseClassroom, isClassEnd)

    // ==================== 取消方法 ====================

    suspend override fun cancelCourseReminders(courseId: Long) {
        try {
            reminderRepository.getAll().filter { it.courseId == courseId }.forEach { rem ->
                cancelAllTypesForReminder(rem.courseId, rem.weekNumber)
            }
            reminderRepository.deleteRemindersByCourse(courseId)
        } catch (e: Exception) {
            AppLogger.e(TAG, "取消课程提醒失败", e)
        }
    }

    suspend override fun cancelAllReminders() {
        try {
            reminderRepository.getAll().forEach { rem ->
                cancelAllTypesForReminder(rem.courseId, rem.weekNumber)
            }
            reminderRepository.deleteAll()
        } catch (e: Exception) {
            AppLogger.e(TAG, "取消所有提醒失败", e)
        }
    }

    suspend override fun cancelAllCourseReminders(scheduleId: Long) {
        try {
            courseRepository.getAllCoursesBySchedule(scheduleId).first().forEach { course ->
                cancelCourseReminders(course.id)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "批量取消失败", e)
        }
    }

    private suspend fun cancelReminderForWeek(courseId: Long, weekNumber: Int) {
        try {
            val reminders = reminderRepository.getAll().filter {
                it.courseId == courseId && it.weekNumber == weekNumber
            }
            reminders.forEach { cancelAllTypesForReminder(it.courseId, it.weekNumber) }
            reminders.forEach { reminderRepository.delete(it) }
        } catch (e: Exception) {
            AppLogger.e(TAG, "取消指定周次提醒失败", e)
        }
    }

    // ==================== 重调度/更新方法 ====================

    suspend override fun rescheduleAllReminders() {
        val schedule = scheduleRepository.getCurrentSchedule() ?: return
        scheduleAllCourseReminders(schedule.id)
    }

    suspend override fun cleanExpiredReminders() {
        try {
            reminderRepository.deleteExpiredReminders()
        } catch (e: Exception) {
            AppLogger.e(TAG, "清理过期提醒失败", e)
        }
    }

    suspend override fun getReminderStats(): IAlarmScheduler.ReminderStats {
        val allReminders = reminderRepository.getAll()
        val now = System.currentTimeMillis()
        val todayStart = LocalDate.now().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayEnd = todayStart + MS_PER_DAY

        return IAlarmScheduler.ReminderStats(
            totalReminders = allReminders.size,
            activeReminders = allReminders.count { it.triggerTime > now },
            todayReminders = allReminders.count { it.triggerTime in todayStart..todayEnd && it.isEnabled },
            upcomingReminders = allReminders.count { it.triggerTime in (todayEnd + 1)..(todayEnd + 7 * MS_PER_DAY) && it.isEnabled }
        )
    }

    suspend override fun updateReminderTime(courseId: Long, newReminderMinutes: Int) {
        val course = courseRepository.getCourseById(courseId) ?: return
        scheduleCourseReminders(course.copy(reminderMinutes = newReminderMinutes))
    }

    suspend override fun toggleCourseReminder(courseId: Long, enabled: Boolean) {
        val course = courseRepository.getCourseById(courseId) ?: return
        if (enabled) scheduleCourseReminders(course) else cancelCourseReminders(courseId)
    }

    suspend override fun toggleSingleReminder(reminder: Reminder, enabled: Boolean) {
        if (enabled) {
            val intent = createReminderIntent(reminder.courseId, reminder.weekNumber)
            val requestCode = AlarmReminderReceiver.generateRequestCode(reminder.courseId, reminder.weekNumber, false)
            setAlarmWithFallback(reminder.triggerTime, createPendingIntent(requestCode, intent))
        } else {
            val requestCode = AlarmReminderReceiver.generateRequestCode(reminder.courseId, reminder.weekNumber, false)
            alarmManager.cancel(createPendingIntent(requestCode, createReminderIntent()))
        }
    }

    // ==================== 调课相关 ====================

    suspend override fun rescheduleRemindersForAdjustment(adjustment: CourseAdjustment) {
        try {
            val course = courseRepository.getCourseById(adjustment.originalCourseId) ?: return
            if (!course.reminderEnabled) return
            val schedule = scheduleRepository.getCurrentSchedule() ?: return
            val classTimes = classTimeRepository.getClassTimesByConfigSync()

            cancelReminderForWeek(course.id, adjustment.originalWeekNumber)

            val reminderDateTime = calculateAdjustedReminderTime(course, schedule, adjustment, classTimes) ?: return
            val reminderTimeMillis = reminderDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (reminderTimeMillis < System.currentTimeMillis()) return

            if (!scheduleAlarm(course.id, adjustment.newWeekNumber, reminderTimeMillis, isClassEnd = false)) return

            reminderRepository.insert(Reminder(
                courseId = course.id, minutesBefore = course.reminderMinutes, isEnabled = true,
                weekNumber = adjustment.newWeekNumber, dayOfWeek = adjustment.newDayOfWeek,
                triggerTime = reminderTimeMillis, workRequestId = "alarm_${course.id}_${adjustment.newWeekNumber}"
            ))
        } catch (e: Exception) {
            AppLogger.e(TAG, "调课重新调度失败", e)
        }
    }

    private fun calculateAdjustedReminderTime(
        course: Course, schedule: Schedule, adjustment: CourseAdjustment, classTimes: List<ClassTime>
    ): LocalDateTime? {
        return try {
        val monday = DateUtils.getMondayOfWeek(schedule.startDate, adjustment.newWeekNumber)
        val courseDate = monday.plusDays((adjustment.newDayOfWeek - 1).toLong())
        if (courseDate.isBefore(LocalDate.now())) return null
        val classTime = classTimes.find { it.sectionNumber == adjustment.newStartSection } ?: return null
        LocalDateTime.of(courseDate, classTime.startTime).minusMinutes(course.reminderMinutes.toLong())
        } catch (e: Exception) {
            AppLogger.e(TAG, "计算调课提醒时间失败", e)
            null
        }
    }

    // ==================== 下节课提醒 ====================

    private suspend fun scheduleNextCourseReminder(
        currentCourse: Course, schedule: Schedule, weekNumber: Int, classTimes: List<ClassTime>
    ) {
        try {
            val endSection = currentCourse.startSection + currentCourse.sectionCount - 1
            if (endSection !in NEXT_COURSE_SECTIONS) return

            val endClassTime = classTimes.find { it.sectionNumber == endSection } ?: return
            val monday = DateUtils.getMondayOfWeek(schedule.startDate, weekNumber)
            val courseDate = monday.plusDays((currentCourse.dayOfWeek - 1).toLong())
            val endTime = LocalDateTime.of(courseDate, endClassTime.endTime)
            if (endTime.isBefore(LocalDateTime.now())) return

            val nextCourses = findNextCoursesInTimeRange(
                currentCourse.scheduleId, currentCourse.dayOfWeek, endSection + 1,
                weekNumber, schedule, endTime, endTime.plusHours(1), classTimes
            )
            val nextCourse = nextCourses.find { it.startSection == endSection + 1 } ?: return

            val isSame = nextCourse.courseName == currentCourse.courseName && nextCourse.classroom == currentCourse.classroom
            val reminderTime = endTime.minusMinutes(NEXT_COURSE_REMINDER_MINUTES.toLong())
            if (reminderTime.isBefore(LocalDateTime.now())) return

            val millis = reminderTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (!scheduleAlarm(nextCourse.id, weekNumber, millis, isNextCourse = true,
                    currentCourseName = currentCourse.courseName, isSameCourseClassroom = isSame, isClassEnd = false)) return

            reminderRepository.insert(Reminder(
                courseId = nextCourse.id, minutesBefore = NEXT_COURSE_REMINDER_MINUTES, isEnabled = true,
                weekNumber = weekNumber, dayOfWeek = nextCourse.dayOfWeek, triggerTime = millis,
                workRequestId = "alarm_next_${currentCourse.id}_${nextCourse.id}_$weekNumber"
            ))
        } catch (e: Exception) {
            AppLogger.e(TAG, "下节课提醒失败", e)
        }
    }

    private suspend fun findNextCoursesInTimeRange(
        scheduleId: Long, dayOfWeek: Int, startSection: Int, weekNumber: Int,
        schedule: Schedule, startTime: LocalDateTime, endTime: LocalDateTime, classTimes: List<ClassTime>
    ): List<Course> {
        val allCourses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            .filter { it.dayOfWeek == dayOfWeek && weekNumber in it.weeks }

        return allCourses.filter { course ->
            val ct = classTimes.find { it.sectionNumber == course.startSection } ?: return@filter false
            val monday = DateUtils.getMondayOfWeek(schedule.startDate, weekNumber)
            val cs = LocalDateTime.of(monday.plusDays((course.dayOfWeek - 1).toLong()), ct.startTime)
            cs.isAfter(startTime) && cs.isBefore(endTime) && course.startSection >= startSection
        }
    }

    // ==================== 下课提醒 ====================

    private suspend fun scheduleClassEndReminder(
        course: Course, schedule: Schedule, weekNumber: Int, classTimes: List<ClassTime>, courseDate: LocalDate
    ) {
        try {
            val endSection = course.startSection + course.sectionCount - 1
            val endClassTime = classTimes.find { it.sectionNumber == endSection } ?: return
            val endTime = LocalDateTime.of(courseDate, endClassTime.endTime)
            if (endTime.isBefore(LocalDateTime.now())) return

            val reminderTime = endTime.minusMinutes(CLASS_END_REMINDER_MINUTES.toLong())
            if (reminderTime.isBefore(LocalDateTime.now())) return

            val millis = reminderTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (!scheduleAlarm(course.id, weekNumber, millis, isClassEnd = true)) return

            reminderRepository.insert(Reminder(
                courseId = course.id, minutesBefore = CLASS_END_REMINDER_MINUTES, isEnabled = true,
                weekNumber = weekNumber, dayOfWeek = course.dayOfWeek, triggerTime = millis,
                workRequestId = "alarm_class_end_${course.id}_$weekNumber"
            ))
        } catch (e: Exception) {
            AppLogger.e(TAG, "下课提醒失败", e)
        }
    }

    // ==================== 查询方法 ====================

    suspend override fun getTodayReminders(): List<Reminder> {
        val todayStart = LocalDate.now().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return reminderRepository.getAll().filter { it.triggerTime in todayStart..(todayStart + MS_PER_DAY) && it.isEnabled }
    }

    suspend override fun getUpcomingReminders(): List<Reminder> {
        val now = System.currentTimeMillis()
        return reminderRepository.getAll().filter { it.triggerTime in now..(now + 60 * 60 * 1000) && it.isEnabled }
    }
}
