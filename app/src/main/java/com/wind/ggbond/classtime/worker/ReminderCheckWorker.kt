package com.wind.ggbond.classtime.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wind.ggbond.classtime.MainActivity
import com.wind.ggbond.classtime.R
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.repository.ClassTimeRepository
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.service.contract.IAlarmScheduler
import com.wind.ggbond.classtime.util.DateUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

@HiltWorker
class ReminderCheckWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val courseRepository: CourseRepository,
    private val scheduleRepository: ScheduleRepository,
    private val classTimeRepository: ClassTimeRepository,
    private val alarmReminderScheduler: IAlarmScheduler
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ReminderCheckWorker"
        // WorkManager唯一任务名称
        const val UNIQUE_WORK_NAME = "reminder_check_periodic"
        // 通知渠道ID（复用课程提醒渠道）
        private const val CHANNEL_ID = "course_reminder"

        /**
         * 注册周期性兜底检查任务
         * 每15分钟执行一次（WorkManager最小周期）
         *
         * @param context 应用上下文
         */
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderCheckWorker>(
                15, TimeUnit.MINUTES
            ).addTag(UNIQUE_WORK_NAME).build()

            // KEEP策略：如果任务已存在则保留，避免重复注册
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "兜底检查任务已注册（每15分钟）")
        }

        /**
         * 取消周期性兜底检查任务
         *
         * @param context 应用上下文
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            Log.d(TAG, "兜底检查任务已取消")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "===== 兜底检查开始 =====")
        try {
            // 获取当前课表（包含学期时间信息）
            val schedule = scheduleRepository.getCurrentSchedule()
            if (schedule == null) {
                Log.d(TAG, "未设置课表，跳过检查")
                return Result.success()
            }

            // 计算当前周次
            val today = LocalDate.now()
            val currentWeekNumber = DateUtils.calculateWeekNumber(schedule.startDate, today)
            val todayDayOfWeek = today.dayOfWeek.value

            // 获取上课时间配置
            val classTimes = classTimeRepository.getClassTimesByConfigSync()
            if (classTimes.isEmpty()) {
                Log.d(TAG, "未配置上课时间，跳过检查")
                return Result.success()
            }

            // 获取当前课表的所有课程
            val allCourses = courseRepository.getAllCoursesBySchedule(schedule.id).first()
            // 筛选今天、本周、启用提醒的课程
            val todayCourses = allCourses.filter { course ->
                course.dayOfWeek == todayDayOfWeek &&
                currentWeekNumber in course.weeks &&
                course.reminderEnabled
            }

            if (todayCourses.isEmpty()) {
                Log.d(TAG, "今天没有需要提醒的课程")
                // 即使今天没课，也尝试重新注册未来的AlarmManager提醒（自愈）
                tryRescheduleAlarms()
                return Result.success()
            }

            val now = LocalDateTime.now()
            // 检查窗口：当前时间 到 未来16分钟（覆盖到下次Worker执行）
            val checkWindowEnd = now.plusMinutes(16)
            var triggeredCount = 0

            todayCourses.forEach { course ->
                val classTime = classTimes.find { it.sectionNumber == course.startSection }
                    ?: return@forEach

                // 计算提醒时间（上课时间 - 提前分钟数）
                val courseStartTime = LocalDateTime.of(today, classTime.startTime)
                val reminderTime = courseStartTime.minusMinutes(course.reminderMinutes.toLong())

                // 判断提醒时间是否在检查窗口内
                if (reminderTime.isAfter(now.minusMinutes(2)) && reminderTime.isBefore(checkWindowEnd)) {
                    Log.d(TAG, "发现到期提醒: ${course.courseName}, 提醒时间=$reminderTime")
                    // 直接发送通知（兜底触发）
                    sendReminderNotification(course, classTime, currentWeekNumber)
                    triggeredCount++
                }
            }

            Log.d(TAG, "兜底检查完成，触发了 $triggeredCount 条提醒")

            // 自愈：重新注册AlarmManager提醒（防止下次检查前的提醒丢失）
            tryRescheduleAlarms()

            Log.d(TAG, "===== 兜底检查结束 =====")
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "兜底检查异常", e)
            return Result.retry()
        }
    }

    /**
     * 尝试重新注册所有AlarmManager提醒（自愈机制）
     * 如果AlarmManager提醒被系统清除，通过WorkManager重新注册
     */
    private suspend fun tryRescheduleAlarms() {
        try {
            alarmReminderScheduler.rescheduleAllReminders()
            Log.d(TAG, "AlarmManager提醒已重新注册（自愈）")
        } catch (e: Exception) {
            Log.e(TAG, "重新注册AlarmManager失败", e)
        }
    }

    /**
     * 发送课程提醒通知（兜底触发）
     *
     * @param course 课程信息
     * @param classTime 上课时间配置
     * @param weekNumber 当前周次
     */
    private fun sendReminderNotification(course: Course, classTime: ClassTime, weekNumber: Int) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            // 检查通知权限
            if (!notificationManager.areNotificationsEnabled()) {
                Log.w(TAG, "通知权限未授予")
                return
            }

            // 构建通知内容
            val classroomText = course.classroom.ifEmpty { "教室未设置" }
            val teacherText = course.teacher.ifEmpty { "教师未设置" }
            val dayOfWeekName = DateUtils.getDayOfWeekName(course.dayOfWeek)
            val sectionText = if (course.sectionCount > 1) {
                "第${course.startSection}-${course.startSection + course.sectionCount - 1}节"
            } else {
                "第${course.startSection}节"
            }

            val title = "时课 ${course.courseName}"
            val shortText = "地点：${classroomText}"
            val longText = buildString {
                append("上课地点：${classroomText}\n")
                if (course.teacher.isNotEmpty()) {
                    append("任课教师：${teacherText}\n")
                }
                append("上课时间：${dayOfWeekName} ${sectionText}\n")
                append("具体时间：${classTime.startTime}\n")
                // 添加更多详细信息
                append("课程编号：${course.id}\n")
                if (course.weeks.isNotEmpty()) {
                    append("上课周次：第${course.weeks.sorted().joinToString(",")}周\n")
                }
            }

            // 点击通知打开App
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("courseId", course.id)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                ("fallback_${course.id}_$weekNumber").hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 使用唯一ID（包含时间戳，避免与AlarmManager的通知冲突后被覆盖）
            val notificationId = ("fallback_${course.id}_${weekNumber}_${System.currentTimeMillis()}")
                .hashCode().and(0x7FFFFFFF)

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(shortText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(longText))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()

            notificationManager.notify(notificationId, notification)
            Log.d(TAG, "兜底通知已发送: ${course.courseName}, ID=$notificationId")

        } catch (e: Exception) {
            Log.e(TAG, "发送兜底通知失败: ${e.message}", e)
        }
    }
}
