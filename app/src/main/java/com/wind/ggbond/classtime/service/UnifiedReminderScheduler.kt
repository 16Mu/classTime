package com.wind.ggbond.classtime.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.CourseAdjustment
import com.wind.ggbond.classtime.data.local.entity.Reminder
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.domain.usecase.ReminderUseCase
import com.wind.ggbond.classtime.service.contract.IAlarmScheduler
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.worker.ReminderCheckWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnifiedReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: ReminderAlarmManager,
    private val queryService: ReminderQueryService,
    private val reminderUseCase: ReminderUseCase
) : IAlarmScheduler {

    companion object {
        private const val TAG = "UnifiedReminderScheduler"
        private const val DAILY_SYNC_WORK_NAME = "daily_reminder_sync"
    }

    suspend override fun scheduleCourseReminders(course: Course) {
        if (!course.reminderEnabled) return

        val schedule = queryService.getCurrentSchedule() ?: return
        val classTimes = queryService.getClassTimes()
        cancelCourseReminders(course.id)

        var scheduledCount = 0
        var failedCount = 0
        course.weeks.forEach { week ->
            if (scheduleWeekReminder(course, schedule, week, classTimes)) scheduledCount++ else failedCount++
        }
        AppLogger.d(TAG, "课程 ${course.courseName}: $scheduledCount 个提醒创建成功, $failedCount 失败")
    }

    suspend override fun scheduleAllCourseReminders(scheduleId: Long) {
        val courses = queryService.getCoursesBySchedule(scheduleId)
            .filter { it.reminderEnabled }
        if (courses.isEmpty()) return

        val schedule = queryService.getCurrentSchedule() ?: return
        val classTimes = queryService.getClassTimes()
        cancelAllCourseReminders(scheduleId)

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

            if (!alarmManager.scheduleAlarm(course.id, weekNumber, reminderTime, isClassEnd = false)) return false

            queryService.insertReminder(Reminder(
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

    override fun scheduleTestAlarm(
        courseId: Long, weekNumber: Int, triggerTime: Long,
        isNextCourse: Boolean, currentCourseName: String,
        isSameCourseClassroom: Boolean, isClassEnd: Boolean
    ): Boolean = alarmManager.scheduleAlarm(courseId, weekNumber, triggerTime, isNextCourse, currentCourseName, isSameCourseClassroom, isClassEnd)

    suspend override fun cancelCourseReminders(courseId: Long) {
        try {
            queryService.getRemindersByCourse(courseId).forEach { rem ->
                alarmManager.cancelAllTypesForReminder(rem.courseId, rem.weekNumber)
            }
            queryService.deleteRemindersByCourse(courseId)
        } catch (e: Exception) {
            AppLogger.e(TAG, "取消课程提醒失败", e)
        }
    }

    suspend override fun cancelAllReminders() {
        try {
            queryService.getAllReminders().forEach { rem ->
                alarmManager.cancelAllTypesForReminder(rem.courseId, rem.weekNumber)
            }
            queryService.deleteAllReminders()
        } catch (e: Exception) {
            AppLogger.e(TAG, "取消所有提醒失败", e)
        }
    }

    suspend override fun cancelAllCourseReminders(scheduleId: Long) {
        try {
            queryService.getCoursesBySchedule(scheduleId).forEach { course ->
                cancelCourseReminders(course.id)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "批量取消失败", e)
        }
    }

    private suspend fun cancelReminderForWeek(courseId: Long, weekNumber: Int) {
        try {
            val reminders = queryService.getRemindersByCourseAndWeek(courseId, weekNumber)
            reminders.forEach { alarmManager.cancelAllTypesForReminder(it.courseId, it.weekNumber) }
            reminders.forEach { queryService.deleteReminder(it) }
        } catch (e: Exception) {
            AppLogger.e(TAG, "取消指定周次提醒失败", e)
        }
    }

    suspend override fun rescheduleAllReminders() {
        val schedule = queryService.getCurrentSchedule() ?: return
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
        val course = queryService.getCourseById(courseId) ?: return
        scheduleCourseReminders(course.copy(reminderMinutes = newReminderMinutes))
    }

    suspend override fun toggleCourseReminder(courseId: Long, enabled: Boolean) {
        val course = queryService.getCourseById(courseId) ?: return
        if (enabled) scheduleCourseReminders(course) else cancelCourseReminders(courseId)
    }

    suspend override fun toggleSingleReminder(reminder: Reminder, enabled: Boolean) {
        if (enabled) {
            val intent = alarmManager.createReminderIntent(reminder.courseId, reminder.weekNumber)
            val requestCode = com.wind.ggbond.classtime.receiver.AlarmReminderReceiver.generateRequestCode(reminder.courseId, reminder.weekNumber, false)
            alarmManager.setAlarm(requestCode, intent, reminder.triggerTime)
        } else {
            val requestCode = com.wind.ggbond.classtime.receiver.AlarmReminderReceiver.generateRequestCode(reminder.courseId, reminder.weekNumber, false)
            alarmManager.cancelAlarm(requestCode, alarmManager.createReminderIntent())
        }
    }

    suspend override fun rescheduleRemindersForAdjustment(adjustment: CourseAdjustment) {
        try {
            val course = queryService.getCourseById(adjustment.originalCourseId) ?: return
            if (!course.reminderEnabled) return
            val schedule = queryService.getCurrentSchedule() ?: return
            val classTimes = queryService.getClassTimes()

            cancelReminderForWeek(course.id, adjustment.originalWeekNumber)

            val reminderTimeMillis = reminderUseCase.calculateAdjustedReminderTime(
                course, schedule, adjustment, classTimes
            ) ?: return

            if (!alarmManager.scheduleAlarm(course.id, adjustment.newWeekNumber, reminderTimeMillis, isClassEnd = false)) return

            queryService.insertReminder(Reminder(
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
            val allCourses = queryService.getCoursesBySchedule(currentCourse.scheduleId)
            val info = reminderUseCase.calculateNextCourseReminderInfo(
                currentCourse, schedule, weekNumber, classTimes, allCourses
            ) ?: return

            if (!alarmManager.scheduleAlarm(info.nextCourse.id, weekNumber, info.triggerTime, isNextCourse = true,
                    currentCourseName = currentCourse.courseName, isSameCourseClassroom = info.isSameCourseAndClassroom, isClassEnd = false)) return

            queryService.insertReminder(Reminder(
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

            if (!alarmManager.scheduleAlarm(course.id, weekNumber, millis, isClassEnd = true)) return

            queryService.insertReminder(Reminder(
                courseId = course.id, minutesBefore = ReminderUseCase.CLASS_END_REMINDER_MINUTES, isEnabled = true,
                weekNumber = weekNumber, dayOfWeek = course.dayOfWeek, triggerTime = millis,
                workRequestId = "alarm_class_end_${course.id}_$weekNumber"
            ))
        } catch (e: Exception) {
            AppLogger.e(TAG, "下课提醒失败", e)
        }
    }

    suspend override fun getTodayReminders(): List<Reminder> =
        queryService.getTodayReminders()

    suspend override fun getUpcomingReminders(): List<Reminder> =
        queryService.getUpcomingReminders()

    fun setupDailyReminderSync() {
        val dailyWorkRequest = PeriodicWorkRequestBuilder<ReminderCheckWorker>(
            15, TimeUnit.MINUTES
        ).addTag(ReminderCheckWorker.UNIQUE_WORK_NAME).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            dailyWorkRequest
        )
        AppLogger.d(TAG, "每日提醒同步任务已注册")
    }

    fun cancelDailyReminderSync() {
        WorkManager.getInstance(context).cancelUniqueWork(DAILY_SYNC_WORK_NAME)
        AppLogger.d(TAG, "每日提醒同步任务已取消")
    }

    private fun calculateInitialDelayToMidnight(): Long {
        val now = LocalDateTime.now()
        val nextSync = if (now.hour >= 2) {
            now.plusDays(1).withHour(2).withMinute(0).withSecond(0)
        } else {
            now.withHour(2).withMinute(0).withSecond(0)
        }
        val delay = nextSync.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() -
                System.currentTimeMillis()
        return delay.coerceAtLeast(0)
    }
}
