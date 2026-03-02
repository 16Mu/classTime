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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wind.ggbond.classtime.MainActivity
import com.wind.ggbond.classtime.R
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.util.DateUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 课程提醒 Worker
 */
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
        
        /**
         * 生成唯一的通知ID
         * 使用课程ID和周次生成固定ID，确保同一提醒有相同的ID
         */
        private fun generateNotificationId(courseId: Long, weekNumber: Int): Int {
            // 使用课程ID和周次生成唯一ID（不包括时间戳，确保同一提醒有固定ID）
            val idString = "${courseId}_${weekNumber}"
            return idString.hashCode().and(0x7FFFFFFF)  // 确保为正数
        }
    }
    
    override suspend fun doWork(): Result {
        val courseId = inputData.getLong(KEY_COURSE_ID, 0)
        if (courseId == 0L) {
            Log.e(TAG, "courseId 为 0，无法创建提醒")
            return Result.failure()
        }
        
        val course = courseRepository.getCourseById(courseId) ?: run {
            Log.e(TAG, "未找到课程 ID: $courseId")
            return Result.failure()
        }
        
        val weekNumber = inputData.getInt("weekNumber", 0)
        
        // ✅ 检查是否是下节课提醒
        val isNextCourseReminder = inputData.getBoolean("isNextCourseReminder", false)
        val currentCourseName = inputData.getString("currentCourseName") ?: ""
        // ✅ 检查是否是同一门课且同一教室
        val isSameCourseAndClassroom = inputData.getBoolean("isSameCourseAndClassroom", false)
        
        // ✅ 构建通知内容文本
        val classroomText = if (course.classroom.isNotEmpty()) {
            course.classroom
        } else {
            "教室未设置"
        }
        
        val teacherText = if (course.teacher.isNotEmpty()) {
            course.teacher
        } else {
            "教师未设置"
        }
        
        val dayOfWeekName = DateUtils.getDayOfWeekName(course.dayOfWeek)
        val sectionText = if (course.sectionCount > 1) {
            "第${course.startSection}-${course.startSection + course.sectionCount - 1}节"
        } else {
            "第${course.startSection}节"
        }
        
        // ✅ 构建通知内容
        // 短文本：突出显示上课地点
        val shortText = if (course.classroom.isNotEmpty()) {
            "地点：${course.classroom}"
        } else {
            "地点：${classroomText}"
        }
        
        // 长文本：完整的课程信息
        val longText = buildString {
            append("上课地点：${classroomText}\n")
            if (course.teacher.isNotEmpty()) {
                append("任课教师：${teacherText}\n")
            }
            append("上课时间：${dayOfWeekName} ${sectionText}\n")
            // 添加更多详细信息
            append("课程编号：${course.id}\n")
            if (course.weeks.isNotEmpty()) {
                append("上课周次：第${course.weeks.sorted().joinToString(",")}周\n")
            }
        }
        
        // ✅ 检查通知权限
        val notificationManager = NotificationManagerCompat.from(applicationContext)
        if (!notificationManager.areNotificationsEnabled()) {
            Log.w(TAG, "通知权限未授予，无法显示通知")
            return Result.failure()
        }
        
        // ✅ 创建并检查通知渠道
        createNotificationChannel()
        
        // ✅ 检查通知渠道是否被禁用
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) {
                Log.w(TAG, "通知渠道已被用户禁用")
                return Result.failure()
            }
        }
        
        // ✅ 生成唯一的通知ID（下节课提醒使用不同的ID）
        // ⚠️ 重要：每次通知都使用包含时间戳的ID，确保被视为新通知而不是更新
        // 这样可以确保即使旧通知还在通知栏，新通知也会弹窗
        val baseNotificationId = if (isNextCourseReminder) {
            // 下节课提醒使用特殊ID，避免与正常提醒冲突
            generateNotificationId(courseId, weekNumber) + 1000000
        } else {
            generateNotificationId(courseId, weekNumber)
        }
        // ✅ 使用毫秒级时间戳生成唯一ID，确保每次都是新通知
        // 这样可以避免系统将后续通知视为更新，从而确保每次都会弹窗
        val notificationId = (baseNotificationId.toString() + System.currentTimeMillis()).hashCode().and(0x7FFFFFFF)
        
        // ✅ 创建打开应用的 Intent
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("courseId", courseId)
        }
        
        // ✅ 使用唯一的 requestCode（避免 PendingIntent 冲突）
        val requestCode = (courseId.toString() + weekNumber.toString()).hashCode()
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // ✅ 读取弹窗通知设置
        // 使用 SharedPreferences 作为临时方案（更简单可靠）
        val headsUpEnabled = try {
            val prefs = applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            prefs.getBoolean("heads_up_notification_enabled", true)  // 默认开启
        } catch (e: Exception) {
            Log.w(TAG, "读取弹窗设置失败，使用默认值: ${e.message}")
            true  // 默认开启
        }
        
        // ✅ 如果启用弹窗，创建全屏意图（确保弹窗效果）
        val fullScreenIntent = if (headsUpEnabled) {
            PendingIntent.getActivity(
                applicationContext,
                requestCode + 10000,  // 使用不同的 requestCode
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            null
        }
        
        // ✅ 构建通知（使用专门的通知图标）
        // 根据是否是下节课提醒，显示不同的标题和内容
        val notificationTitle = if (isNextCourseReminder) {
            if (isSameCourseAndClassroom) {
                "时课 课程继续：${course.courseName}"
            } else {
                "时课 下节课提醒：${course.courseName}"
            }
        } else {
            "时课 ${course.courseName}"
        }
        
        // 下节课提醒的短文本
        val nextCourseShortText = if (isNextCourseReminder) {
            if (isSameCourseAndClassroom) {
                // 同一门课且同一教室：提示继续在当前教室上课
                if (course.classroom.isNotEmpty()) {
                    "继续在 ${course.classroom} 上课，无需更换教室"
                } else {
                    "课程继续进行，无需更换教室"
                }
            } else {
                // 不同课或不同教室：正常提示地点
                "地点：${course.classroom.ifEmpty { classroomText }}" + if (currentCourseName.isNotEmpty()) {
                    "（${currentCourseName}即将下课）"
                } else {
                    ""
                }
            }
        } else {
            shortText
        }
        
        // 下节课提醒的长文本
        val nextCourseLongText = if (isNextCourseReminder) {
            buildString {
                if (isSameCourseAndClassroom) {
                    // 同一门课且同一教室
                    append("课程继续提醒\n\n")
                    append("上课地点：${classroomText}（继续在当前教室）\n")
                    if (course.teacher.isNotEmpty()) {
                        append("任课教师：${teacherText}\n")
                    }
                    append("时间：${dayOfWeekName} ${sectionText}\n\n")
                    append("提示：当前课程将继续进行，无需更换教室")
                } else {
                    // 不同课或不同教室
                    append("下节课提醒\n\n")
                    append("上课地点：${classroomText}\n")
                    if (course.teacher.isNotEmpty()) {
                        append("任课教师：${teacherText}\n")
                    }
                    append("时间：${dayOfWeekName} ${sectionText}\n")
                    if (currentCourseName.isNotEmpty()) {
                        append("\n提示：当前课程「${currentCourseName}」即将下课")
                    }
                }
            }
        } else {
            longText
        }
        
        // ✅ 每次通知都使用当前时间戳，确保被视为新通知
        val currentTime = System.currentTimeMillis()
        
        // 通知内容：突出显示上课地点和课程信息
        val notificationBuilder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)  // ✅ 使用专门的通知图标
            .setContentTitle(notificationTitle)  // 课程名称或下节课提醒
            .setContentText(if (isNextCourseReminder) nextCourseShortText else shortText)  // 短文本
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(if (isNextCourseReminder) nextCourseLongText else longText))  // 长文本
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)  // ✅ 添加默认声音和震动
            .setShowWhen(true)  // ✅ 显示时间
            .setWhen(currentTime)  // ✅ 使用当前时间戳，确保每次都是新通知
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // ✅ 锁屏可见
            .setOnlyAlertOnce(false)  // ✅ 重要：确保每次通知都会弹窗，不被视为更新
        
        // ✅ 如果启用弹窗，添加 FullScreenIntent（类似QQ微信的弹窗效果）
        if (headsUpEnabled && fullScreenIntent != null) {
            notificationBuilder
                .setFullScreenIntent(fullScreenIntent, true)  // ✅ 确保弹窗显示
                .setPriority(NotificationCompat.PRIORITY_MAX)  // ✅ 使用最高优先级，强制弹窗
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)  // ✅ 使用 MESSAGE 分类，更容易弹窗
            Log.d(TAG, "弹窗通知已启用，将显示悬浮弹窗（优先级：MAX，时间戳：$currentTime）")
        } else {
            // 不弹窗时使用 REMINDER 分类
            notificationBuilder.setCategory(NotificationCompat.CATEGORY_REMINDER)
            Log.d(TAG, "弹窗通知已禁用，仅显示通知栏通知")
        }
        
        val notification = notificationBuilder.build()
        
        // ✅ 显示通知
        try {
            // ✅ 由于每次通知都使用包含时间戳的唯一ID，不需要取消旧通知
            // 系统会自动识别为新通知，从而确保每次都会弹窗
            notificationManager.notify(notificationId, notification)
            Log.d(TAG, "通知已显示：${course.courseName}, ID: $notificationId, 时间戳：$currentTime")
            return Result.success()
        } catch (e: SecurityException) {
            Log.e(TAG, "显示通知失败：${e.message}", e)
            return Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "显示通知时发生未知错误：${e.message}", e)
            return Result.failure()
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
            
            if (existingChannel == null) {
                // ✅ 创建新渠道
                val name = applicationContext.getString(R.string.reminder_channel_name)
                val descriptionText = applicationContext.getString(R.string.reminder_channel_description)
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 250, 250)  // ✅ 震动模式
                    enableLights(true)
                    lightColor = android.graphics.Color.BLUE
                    // ✅ 设置通知声音
                    setSound(
                        Settings.System.DEFAULT_NOTIFICATION_URI,
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build()
                    )
                    // ✅ 设置可以绕过勿扰模式（更容易弹窗）
                    setBypassDnd(false)  // 需要系统权限，先设为false
                    // ✅ 锁定屏幕可见性
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "通知渠道已创建")
            } else if (existingChannel.importance == NotificationManager.IMPORTANCE_NONE) {
                // ✅ 渠道被禁用，记录日志
                Log.w(TAG, "通知渠道已被用户禁用")
            }
        }
    }
}



