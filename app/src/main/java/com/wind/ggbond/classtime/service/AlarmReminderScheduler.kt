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
import com.wind.ggbond.classtime.domain.usecase.ReminderUseCase
import com.wind.ggbond.classtime.receiver.AlarmReminderReceiver
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.service.contract.IAlarmScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Deprecated("使用 UnifiedReminderScheduler 替代")
@Singleton
class AlarmReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminderRepository: ReminderRepository,
    private val scheduleRepository: ScheduleRepository,
    private val classTimeRepository: ClassTimeRepository,
    private val courseRepository: CourseRepository,
    private val adjustmentRepository: CourseAdjustmentRepository,
    private val reminderUseCase: ReminderUseCase
) : IAlarmScheduler {

    companion object {
        private const val TAG = "AlarmReminderScheduler"
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

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

    private suspend fun scheduleWeekReminder(
        course: Course, schedule: Schedule, weekNumber: Int, classTimes: List<ClassTime>
    ): Boolean {
        return try {
            val reminderTime = reminderUseCase.calculateReminderTime(course, schedule, weekNumber, classTimes)
                ?: return false

            if (!scheduleAlarm(course.id, weekNumber, reminderTime, isClassEnd = false)) return false

            reminderRepository.insert(Reminder(
                courseId = course.id, minutesBefore = course.reminderMinutes,
                isEnabled = true, weekNumber = weekNumber, dayOfWeek = course.dayOfWeek,
                triggerTime = reminderTime, workRequestId = "alarm_${course.id}_$weekNumber"
            ))

            scheduleNextCourseReminder(course, schedule, weekNumber, classTimes)
            scheduleClassEndReminder(course, schedule, weekNumber, classTimes)
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

    suspend override fun rescheduleAllReminders() {
        val schedule = scheduleRepository.getCurrentSchedule() ?: return
        scheduleAllCourseReminders(schedule.id)
    }

    suspend override fun cleanExpiredReminders() {
        try {
            reminderUseCase.cleanExpiredReminders()
        } catch (e: Exception) {
            AppLogger.e(TAG, "清理过期提醒失败", e)
        }
    }

    suspend override fun getReminderStats(): IAlarmScheduler.ReminderStats {
        val stats = reminderUseCase.getReminderStats()
        return IAlarmScheduler.ReminderStats(
            totalReminders = stats.totalReminders,
            activeReminders = stats.activeReminders,
            todayReminders = stats.todayReminders,
            upcomingReminders = stats.upcomingReminders
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

    suspend override fun rescheduleRemindersForAdjustment(adjustment: CourseAdjustment) {
        try {
            val course = courseRepository.getCourseById(adjustment.originalCourseId) ?: return
            if (!course.reminderEnabled) return
            val schedule = scheduleRepository.getCurrentSchedule() ?: return
            val classTimes = classTimeRepository.getClassTimesByConfigSync()

            cancelReminderForWeek(course.id, adjustment.originalWeekNumber)

            val reminderTimeMillis = reminderUseCase.calculateAdjustedReminderTime(
                course, schedule, adjustment, classTimes
            ) ?: return

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

    private suspend fun scheduleNextCourseReminder(
        currentCourse: Course, schedule: Schedule, weekNumber: Int, classTimes: List<ClassTime>
    ) {
        try {
            val allCourses = courseRepository.getAllCoursesBySchedule(currentCourse.scheduleId).first()
            val info = reminderUseCase.calculateNextCourseReminderInfo(
                currentCourse, schedule, weekNumber, classTimes, allCourses
            ) ?: return

            if (!scheduleAlarm(info.nextCourse.id, weekNumber, info.triggerTime, isNextCourse = true,
                    currentCourseName = currentCourse.courseName, isSameCourseClassroom = info.isSameCourseAndClassroom, isClassEnd = false)) return

            reminderRepository.insert(Reminder(
                courseId = info.nextCourse.id, minutesBefore = ReminderUseCase.NEXT_COURSE_REMINDER_MINUTES, isEnabled = true,
                weekNumber = weekNumber, dayOfWeek = info.nextCourse.dayOfWeek, triggerTime = info.triggerTime,
                workRequestId = "alarm_next_${currentCourse.id}_${info.nextCourse.id}_$weekNumber"
            ))
        } catch (e: Exception) {
            AppLogger.e(TAG, "下节课提醒失败", e)
        }
    }

    private suspend fun scheduleClassEndReminder(
        course: Course, schedule: Schedule, weekNumber: Int, classTimes: List<ClassTime>
    ) {
        try {
            val millis = reminderUseCase.calculateClassEndReminderTime(
                course, schedule, weekNumber, classTimes
            ) ?: return

            if (!scheduleAlarm(course.id, weekNumber, millis, isClassEnd = true)) return

            reminderRepository.insert(Reminder(
                courseId = course.id, minutesBefore = ReminderUseCase.CLASS_END_REMINDER_MINUTES, isEnabled = true,
                weekNumber = weekNumber, dayOfWeek = course.dayOfWeek, triggerTime = millis,
                workRequestId = "alarm_class_end_${course.id}_$weekNumber"
            ))
        } catch (e: Exception) {
            AppLogger.e(TAG, "下课提醒失败", e)
        }
    }

    suspend override fun getTodayReminders(): List<Reminder> {
        val todayStart = LocalDate.now().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return reminderRepository.getAll().filter { it.triggerTime in todayStart..(todayStart + ReminderUseCase.MS_PER_DAY) && it.isEnabled }
    }

    suspend override fun getUpcomingReminders(): List<Reminder> {
        val now = System.currentTimeMillis()
        return reminderRepository.getAll().filter { it.triggerTime in now..(now + 60 * 60 * 1000) && it.isEnabled }
    }
}
