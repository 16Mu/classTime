package com.wind.ggbond.classtime.receiver

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.wind.ggbond.classtime.MainActivity
import com.wind.ggbond.classtime.R
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.util.DateUtils
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AlarmManager 课程提醒接收器
 * 替代 ReminderWorker，使用 AlarmManager 实现精确的后台提醒
 */
class AlarmReminderReceiver : BroadcastReceiver() {
    
    /**
     * Hilt EntryPoint 接口，用于在 BroadcastReceiver 中获取 Hilt 管理的依赖
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AlarmReminderEntryPoint {
        fun courseRepository(): CourseRepository
    }
    
    companion object {
        const val CHANNEL_ID = "course_reminder"
        const val ACTION_COURSE_REMINDER = "com.wind.ggbond.classtime.COURSE_REMINDER"
        const val EXTRA_COURSE_ID = "courseId"
        const val EXTRA_WEEK_NUMBER = "weekNumber"
        const val EXTRA_IS_NEXT_COURSE = "isNextCourse"
        const val EXTRA_CURRENT_COURSE_NAME = "currentCourseName"
        const val EXTRA_IS_SAME_COURSE_CLASSROOM = "isSameCourseClassroom"
        private const val TAG = "AlarmReminderReceiver"
        
        /**
         * 生成唯一的通知ID
         */
        fun generateNotificationId(courseId: Long, weekNumber: Int, isNextCourse: Boolean = false): Int {
            val baseId = "${courseId}_${weekNumber}".hashCode().and(0x7FFFFFFF)
            return if (isNextCourse) baseId + 1000000 else baseId
        }
        
        /**
         * 生成唯一的 AlarmManager requestCode
         */
        fun generateRequestCode(courseId: Long, weekNumber: Int, isNextCourse: Boolean = false): Int {
            val baseCode = "${courseId}_${weekNumber}".hashCode().and(0x7FFFFFFF)
            return if (isNextCourse) baseCode + 2000000 else baseCode
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COURSE_REMINDER) return
        
        Log.d(TAG, "收到课程提醒 AlarmManager 触发")
        
        val courseId = intent.getLongExtra(EXTRA_COURSE_ID, 0L)
        val weekNumber = intent.getIntExtra(EXTRA_WEEK_NUMBER, 0)
        val isNextCourse = intent.getBooleanExtra(EXTRA_IS_NEXT_COURSE, false)
        val currentCourseName = intent.getStringExtra(EXTRA_CURRENT_COURSE_NAME) ?: ""
        val isSameCourseClassroom = intent.getBooleanExtra(EXTRA_IS_SAME_COURSE_CLASSROOM, false)
        
        if (courseId == 0L) {
            Log.e(TAG, "courseId 为 0，无法处理提醒")
            return
        }
        
        // 使用 goAsync() 延长 BroadcastReceiver 生命周期，防止协程未完成时进程被杀
        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 手动获取 CourseRepository
                val courseRepository = getCourseRepository(context)
                if (courseRepository == null) {
                    Log.e(TAG, "无法获取 CourseRepository")
                    return@launch
                }
                
                val course = courseRepository.getCourseById(courseId)
                if (course == null) {
                    Log.e(TAG, "未找到课程 ID: $courseId")
                    return@launch
                }
                
                // 发送通知
                sendNotification(
                    context = context,
                    course = course,
                    weekNumber = weekNumber,
                    isNextCourse = isNextCourse,
                    currentCourseName = currentCourseName,
                    isSameCourseClassroom = isSameCourseClassroom
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "处理课程提醒失败", e)
            } finally {
                // 通知系统 BroadcastReceiver 处理完毕，可以安全回收
                pendingResult.finish()
            }
        }
    }
    
    /**
     * 通过 Hilt EntryPointAccessors 获取 CourseRepository（使用 DI 管理的单例）
     */
    private fun getCourseRepository(context: Context): CourseRepository? {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                AlarmReminderEntryPoint::class.java
            )
            entryPoint.courseRepository()
        } catch (e: Exception) {
            Log.e(TAG, "获取 CourseRepository 失败", e)
            null
        }
    }
    
    private fun sendNotification(
        context: Context,
        course: Course,
        weekNumber: Int,
        isNextCourse: Boolean,
        currentCourseName: String,
        isSameCourseClassroom: Boolean
    ) {
        try {
            // 检查通知权限
            val notificationManager = NotificationManagerCompat.from(context)
            if (!notificationManager.areNotificationsEnabled()) {
                Log.w(TAG, "通知权限未授予，无法显示通知")
                return
            }
            
            // 创建通知渠道
            createNotificationChannel(context)
            
            // 检查通知渠道是否被禁用
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = systemNotificationManager.getNotificationChannel(CHANNEL_ID)
                if (channel?.importance == NotificationManager.IMPORTANCE_NONE) {
                    Log.w(TAG, "通知渠道已被用户禁用")
                    return
                }
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
            
            // 根据是否是下节课提醒，构建不同的通知内容
            val (title, shortText, longText) = if (isNextCourse) {
                buildNextCourseNotification(
                    course, classroomText, teacherText, dayOfWeekName, 
                    sectionText, currentCourseName, isSameCourseClassroom
                )
            } else {
                buildNormalNotification(course, classroomText, teacherText, dayOfWeekName, sectionText)
            }
            
            // 创建打开应用的Intent
            val appIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("courseId", course.id)
            }
            
            val requestCode = generateRequestCode(course.id, weekNumber, isNextCourse)
            val pendingIntent = PendingIntent.getActivity(
                context,
                requestCode,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // 读取弹窗通知设置
            val headsUpEnabled = try {
                val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                prefs.getBoolean("heads_up_notification_enabled", true)
            } catch (e: Exception) {
                Log.w(TAG, "读取弹窗设置失败，使用默认值: ${e.message}")
                true
            }
            
            // 创建全屏意图（弹窗效果）
            val fullScreenIntent = if (headsUpEnabled) {
                PendingIntent.getActivity(
                    context,
                    requestCode + 10000,
                    appIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else null
            
            // 构建通知
            val currentTime = System.currentTimeMillis()
            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(shortText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(longText))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setShowWhen(true)
                .setWhen(currentTime)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(false) // 确保每次都会提醒
            
            // 如果启用弹窗，添加全屏意图
            if (headsUpEnabled && fullScreenIntent != null) {
                notificationBuilder
                    .setFullScreenIntent(fullScreenIntent, true)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                Log.d(TAG, "弹窗通知已启用，将显示悬浮弹窗")
            } else {
                notificationBuilder.setCategory(NotificationCompat.CATEGORY_REMINDER)
                Log.d(TAG, "弹窗通知已禁用，仅显示通知栏通知")
            }
            
            // 发送通知
            val notificationId = generateNotificationId(course.id, weekNumber, isNextCourse)
            val finalNotificationId = (notificationId.toString() + currentTime).hashCode().and(0x7FFFFFFF)
            
            notificationManager.notify(finalNotificationId, notificationBuilder.build())
            
            Log.d(TAG, "通知已发送: ${course.courseName}, ID: $finalNotificationId, 下节课: $isNextCourse")
            
        } catch (e: Exception) {
            Log.e(TAG, "发送通知失败", e)
        }
    }
    
    private fun buildNormalNotification(
        course: Course,
        classroomText: String,
        teacherText: String,
        dayOfWeekName: String,
        sectionText: String
    ): Triple<String, String, String> {
        val title = "时课 ${course.courseName}"
        val shortText = "地点：${course.classroom.ifEmpty { classroomText }}"
        val longText = buildString {
            append("上课地点：${classroomText}\n")
            if (course.teacher.isNotEmpty()) {
                append("任课教师：${teacherText}\n")
            }
            append("上课时间：${dayOfWeekName} ${sectionText}\n")
            if (course.courseCode.isNotEmpty()) {
                append("课程代码：${course.courseCode}\n")
            }
            if (course.weeks.isNotEmpty()) {
                append("上课周次：第${course.weeks.sorted().joinToString(",")}周\n")
            }
        }
        return Triple(title, shortText, longText)
    }
    
    private fun buildNextCourseNotification(
        course: Course,
        classroomText: String,
        teacherText: String,
        dayOfWeekName: String,
        sectionText: String,
        currentCourseName: String,
        isSameCourseClassroom: Boolean
    ): Triple<String, String, String> {
        val title = if (isSameCourseClassroom) {
            "时课 课程继续：${course.courseName}"
        } else {
            "时课 下节课提醒：${course.courseName}"
        }
        
        val shortText = if (isSameCourseClassroom) {
            if (course.classroom.isNotEmpty()) {
                "继续在 ${course.classroom} 上课，无需更换教室"
            } else {
                "课程继续进行，无需更换教室"
            }
        } else {
            "地点：${course.classroom.ifEmpty { classroomText }}" + if (currentCourseName.isNotEmpty()) {
                "（${currentCourseName}即将下课）"
            } else ""
        }
        
        val longText = buildString {
            if (isSameCourseClassroom) {
                append("课程继续提醒\n\n")
                append("上课地点：${classroomText}（继续在当前教室）\n")
                if (course.teacher.isNotEmpty()) {
                    append("任课教师：${teacherText}\n")
                }
                append("上课时间：${dayOfWeekName} ${sectionText}\n")
                if (course.courseCode.isNotEmpty()) {
                    append("课程代码：${course.courseCode}\n")
                }
                if (course.weeks.isNotEmpty()) {
                    append("上课周次：第${course.weeks.sorted().joinToString(",")}周\n")
                }
                append("\n提示：当前课程将继续进行，无需更换教室")
            } else {
                append("下节课提醒\n\n")
                append("上课地点：${classroomText}\n")
                if (course.teacher.isNotEmpty()) {
                    append("任课教师：${teacherText}\n")
                }
                append("上课时间：${dayOfWeekName} ${sectionText}\n")
                if (course.courseCode.isNotEmpty()) {
                    append("课程代码：${course.courseCode}\n")
                }
                if (course.weeks.isNotEmpty()) {
                    append("上课周次：第${course.weeks.sorted().joinToString(",")}周\n")
                }
                if (currentCourseName.isNotEmpty()) {
                    append("\n提示：当前课程「${currentCourseName}」即将下课")
                }
            }
        }
        
        return Triple(title, shortText, longText)
    }
    
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
            
            if (existingChannel == null) {
                val name = context.getString(R.string.reminder_channel_name)
                val descriptionText = context.getString(R.string.reminder_channel_description)
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
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "通知渠道已创建")
            }
        }
    }
}
