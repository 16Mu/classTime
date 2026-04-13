package com.wind.ggbond.classtime.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.wind.ggbond.classtime.receiver.AlarmReminderReceiver
import com.wind.ggbond.classtime.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderAlarmManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "ReminderAlarmManager"
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun createReminderIntent(
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

    fun createPendingIntent(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun cancelAllTypesForReminder(courseId: Long, weekNumber: Int) {
        listOf(
            Pair(false, false),
            Pair(true, false),
            Pair(false, true)
        ).forEach { (isNext, isClassEnd) ->
            val code = AlarmReminderReceiver.generateRequestCode(courseId, weekNumber, isNext, isClassEnd)
            alarmManager.cancel(createPendingIntent(code, createReminderIntent()))
        }
    }

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun setAlarmWithFallback(triggerTime: Long, pendingIntent: PendingIntent): Boolean {
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

    fun scheduleAlarm(
        courseId: Long, weekNumber: Int, triggerTime: Long,
        isNextCourse: Boolean = false, currentCourseName: String = "",
        isSameCourseClassroom: Boolean = false, isClassEnd: Boolean
    ): Boolean {
        val intent = createReminderIntent(courseId, weekNumber, isNextCourse, currentCourseName, isSameCourseClassroom, isClassEnd)
        val requestCode = AlarmReminderReceiver.generateRequestCode(courseId, weekNumber, isNextCourse, isClassEnd)
        return setAlarmWithFallback(triggerTime, createPendingIntent(requestCode, intent))
    }

    fun setAlarm(requestCode: Int, intent: Intent, triggerTime: Long): Boolean {
        return setAlarmWithFallback(triggerTime, createPendingIntent(requestCode, intent))
    }

    fun cancelAlarm(requestCode: Int, intent: Intent) {
        alarmManager.cancel(createPendingIntent(requestCode, intent))
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
}
