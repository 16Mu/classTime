package com.wind.ggbond.classtime.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wind.ggbond.classtime.MainActivity
import com.wind.ggbond.classtime.R
import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.util.DateUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val courseRepository: CourseRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "course_reminder"
        const val KEY_COURSE_ID = "courseId"
        const val KEY_REMINDER_ID = "reminderId"
        private const val TAG = "ReminderWorker"

        private fun generateNotificationId(courseId: Long, weekNumber: Int): Int {
            val idString = "${courseId}_${weekNumber}"
            return idString.hashCode().and(0x7FFFFFFF)
        }
    }

    override suspend fun doWork(): Result {
        val courseId = inputData.getLong(KEY_COURSE_ID, 0)
        if (courseId == 0L) {
            AppLogger.e(TAG, "courseId 为 0，无法创建提醒")
            return Result.failure()
        }

        val course = courseRepository.getCourseById(courseId) ?: run {
            AppLogger.e(TAG, "未找到课程 ID: $courseId")
            return Result.failure()
        }

        val weekNumber = inputData.getInt("weekNumber", 0)
        val isNextCourseReminder = inputData.getBoolean("isNextCourseReminder", false)
        val currentCourseName = inputData.getString("currentCourseName") ?: ""
        val isSameCourseAndClassroom = inputData.getBoolean("isSameCourseAndClassroom", false)

        val classroomText = if (course.classroom.isNotEmpty()) course.classroom else "教室未设置"
        val teacherText = if (course.teacher.isNotEmpty()) course.teacher else "教师未设置"
        val dayOfWeekName = DateUtils.getDayOfWeekName(course.dayOfWeek)
        val sectionText = if (course.sectionCount > 1) {
            "第${course.startSection}-${course.startSection + course.sectionCount - 1}节"
        } else {
            "第${course.startSection}节"
        }

        val shortText = if (course.classroom.isNotEmpty()) "地点：${course.classroom}" else "地点：$classroomText"

        val longText = buildString {
            append("上课地点：${classroomText}\n")
            if (course.teacher.isNotEmpty()) append("任课教师：${teacherText}\n")
            append("上课时间：${dayOfWeekName} ${sectionText}\n")
            if (course.weeks.isNotEmpty()) append("上课周次：第${course.weeks.sorted().joinToString(",")}周\n")
        }

        val notificationManager = NotificationManagerCompat.from(applicationContext)
        if (!notificationManager.areNotificationsEnabled()) {
            AppLogger.w(TAG, "通知权限未授予，无法显示通知")
            return Result.success()
        }

        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) {
                AppLogger.w(TAG, "通知渠道已被用户禁用")
                return Result.success()
            }
        }

        val baseNotificationId = if (isNextCourseReminder) {
            generateNotificationId(courseId, weekNumber) + 1000000
        } else {
            generateNotificationId(courseId, weekNumber)
        }
        val notificationId = baseNotificationId

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("courseId", courseId)
        }

        val requestCode = (courseId.toString() + weekNumber.toString()).hashCode()
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val headsUpEnabled = try {
            val settingsDataStore = com.wind.ggbond.classtime.data.datastore.DataStoreManager.getSettingsDataStore(applicationContext)
            settingsDataStore.data.first()[com.wind.ggbond.classtime.data.datastore.DataStoreManager.SettingsKeys.HEADS_UP_NOTIFICATION_ENABLED_KEY] ?: true
        } catch (e: Exception) {
            AppLogger.w(TAG, "读取弹窗设置失败，使用默认值: ${e.message}")
            true
        }

        val notificationTitle = if (isNextCourseReminder) {
            if (isSameCourseAndClassroom) "时课 课程继续：${course.courseName}"
            else "时课 下节课提醒：${course.courseName}"
        } else "时课 ${course.courseName}"

        val nextCourseShortText = if (isNextCourseReminder) {
            if (isSameCourseAndClassroom) {
                if (course.classroom.isNotEmpty()) "继续在 ${course.classroom} 上课，无需更换教室"
                else "课程继续进行，无需更换教室"
            } else {
                "地点：${course.classroom.ifEmpty { classroomText }}" + if (currentCourseName.isNotEmpty()) "（${currentCourseName}即将下课）" else ""
            }
        } else shortText

        val nextCourseLongText = if (isNextCourseReminder) {
            buildString {
                if (isSameCourseAndClassroom) {
                    append("课程继续提醒\n\n")
                    append("上课地点：${classroomText}（继续在当前教室）\n")
                    if (course.teacher.isNotEmpty()) append("任课教师：${teacherText}\n")
                    append("时间：${dayOfWeekName} ${sectionText}\n\n")
                    append("提示：当前课程将继续进行，无需更换教室")
                } else {
                    append("下节课提醒\n\n")
                    append("上课地点：${classroomText}\n")
                    if (course.teacher.isNotEmpty()) append("任课教师：${teacherText}\n")
                    append("时间：${dayOfWeekName} ${sectionText}\n")
                    if (currentCourseName.isNotEmpty()) append("\n提示：当前课程「${currentCourseName}」即将下课")
                }
            }
        } else longText

        val currentTime = System.currentTimeMillis()

        val notificationBuilder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notificationTitle)
            .setContentText(if (isNextCourseReminder) nextCourseShortText else shortText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (isNextCourseReminder) nextCourseLongText else longText))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setShowWhen(true)
            .setWhen(currentTime)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(false)

        if (headsUpEnabled) {
            notificationBuilder
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
            AppLogger.d(TAG, "弹窗通知已启用，将显示高优先级悬浮通知（时间戳：$currentTime）")
        } else {
            notificationBuilder.setCategory(NotificationCompat.CATEGORY_REMINDER)
            AppLogger.d(TAG, "弹窗通知已禁用，仅显示通知栏通知")
        }

        val notification = notificationBuilder.build()

        try {
            notificationManager.notify(notificationId, notification)
            AppLogger.d(TAG, "通知已显示：${course.courseName}, ID: $notificationId, 时间戳：$currentTime")
            return Result.success()
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "显示通知失败：${e.message}", e)
            return Result.failure()
        } catch (e: Exception) {
            AppLogger.e(TAG, "显示通知时发生未知错误：${e.message}", e)
            return Result.failure()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)

            if (existingChannel == null) {
                val name = applicationContext.getString(R.string.reminder_channel_name)
                val descriptionText = applicationContext.getString(R.string.reminder_channel_description)
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 250, 250)
                    enableLights(true)
                    lightColor = android.graphics.Color.BLUE
                    setSound(
                        Settings.System.DEFAULT_NOTIFICATION_URI,
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build()
                    )
                    setBypassDnd(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
                AppLogger.d(TAG, "通知渠道已创建")
            } else if (existingChannel.importance == NotificationManager.IMPORTANCE_NONE) {
                AppLogger.w(TAG, "通知渠道已被用户禁用")
            }
        }
    }
}
