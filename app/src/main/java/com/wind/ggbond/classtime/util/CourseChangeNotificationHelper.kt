package com.wind.ggbond.classtime.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.wind.ggbond.classtime.MainActivity
import com.wind.ggbond.classtime.R
import com.wind.ggbond.classtime.util.CourseChangeDetector.CourseChangeResult
import com.wind.ggbond.classtime.util.CourseChangeDetector.CourseChangeType
import com.wind.ggbond.classtime.util.CourseChangeDetector.CourseTimeAdjustment

object CourseChangeNotificationHelper {
    
    private const val CHANNEL_ID = "course_change_notification"
    private const val CHANNEL_NAME = "课表变更通知"
    private const val NOTIFICATION_ID = 3001
    
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "当课表发生变更时接收通知"
                    enableVibration(true)
                    enableLights(true)
                    setShowBadge(true)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
    
    fun sendChangeNotification(context: Context, changeResult: CourseChangeResult) {
        createNotificationChannel(context)
        val title = "时课 课表有更新"
        val summary = changeResult.getSummary()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(buildNotificationContent(changeResult)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    fun sendNoChangeNotification(context: Context) {
        createNotificationChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("时课 课表更新完成")
            .setContentText("课表无变化")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    fun sendFailureNotification(context: Context, errorMessage: String) {
        createNotificationChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("时课 课表更新失败")
            .setContentText(errorMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(errorMessage))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun buildNotificationContent(changeResult: CourseChangeResult): String {
        val sb = StringBuilder()
        val dayNames = mapOf(
            1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四",
            5 to "周五", 6 to "周六", 7 to "周日"
        )
        
        if (changeResult.addedCourses.isNotEmpty()) {
            sb.append("新增 ${changeResult.addedCourses.size} 门课程\n")
            changeResult.addedCourses.take(3).forEach { course ->
                sb.append("   · ${course.courseName}\n")
                course.classroom.takeIf { it.isNotEmpty() }?.let { sb.append("     地点：$it\n") }
                course.teacher.takeIf { it.isNotEmpty() }?.let { sb.append("     教师：$it\n") }
                val dayName = dayNames[course.dayOfWeek] ?: "周${course.dayOfWeek}"
                val sectionText = if (course.sectionCount > 1) "第${course.startSection}-${course.startSection + course.sectionCount - 1}节" else "第${course.startSection}节"
                sb.append("     时间：$dayName $sectionText\n\n")
            }
            if (changeResult.addedCourses.size > 3) sb.append("   ...等 ${changeResult.addedCourses.size - 3} 门\n")
            sb.append("\n")
        }
        
        if (changeResult.removedCourses.isNotEmpty()) {
            sb.append("删除 ${changeResult.removedCourses.size} 门课程\n")
            changeResult.removedCourses.take(3).forEach { course ->
                sb.append("   · ${course.courseName}\n")
                course.classroom.takeIf { it.isNotEmpty() }?.let { sb.append("     地点：$it\n") }
                course.teacher.takeIf { it.isNotEmpty() }?.let { sb.append("     教师：$it\n") }
                val dayName = dayNames[course.dayOfWeek] ?: "周${course.dayOfWeek}"
                val sectionText = if (course.sectionCount > 1) "第${course.startSection}-${course.startSection + course.sectionCount - 1}节" else "第${course.startSection}节"
                sb.append("     时间：$dayName $sectionText\n\n")
            }
            if (changeResult.removedCourses.size > 3) sb.append("   ...等 ${changeResult.removedCourses.size - 3} 门\n")
            sb.append("\n")
        }
        
        if (changeResult.adjustedCourses.isNotEmpty()) {
            sb.append("调课 ${changeResult.adjustedCourses.size} 门课程\n")
            changeResult.adjustedCourses.take(3).forEach { adj: CourseTimeAdjustment ->
                sb.append("   · ${adj.courseName}\n")
                val changeDesc = when (adj.changeType) {
                    CourseChangeType.TIME_ONLY -> {
                        val oldDay = dayNames[adj.oldTime.dayOfWeek] ?: "周${adj.oldTime.dayOfWeek}"
                        val newDay = dayNames[adj.newTime.dayOfWeek] ?: "周${adj.newTime.dayOfWeek}"
                        val oldSection = if (adj.oldTime.sectionCount > 1) "第${adj.oldTime.startSection}-${adj.oldTime.startSection + adj.oldTime.sectionCount - 1}节" else "第${adj.oldTime.startSection}节"
                        val newSection = if (adj.newTime.sectionCount > 1) "第${adj.newTime.startSection}-${adj.newTime.startSection + adj.newTime.sectionCount - 1}节" else "第${adj.newTime.startSection}节"
                        "     时间：$oldDay $oldSection → $newDay $newSection"
                    }
                    CourseChangeType.LOCATION_ONLY -> "     地点：${adj.oldTime.classroom} → ${adj.newTime.classroom}"
                    CourseChangeType.TIME_AND_LOCATION -> {
                        val oldDay = dayNames[adj.oldTime.dayOfWeek] ?: "周${adj.oldTime.dayOfWeek}"
                        val newDay = dayNames[adj.newTime.dayOfWeek] ?: "周${adj.newTime.dayOfWeek}"
                        val oldSection = if (adj.oldTime.sectionCount > 1) "第${adj.oldTime.startSection}-${adj.oldTime.startSection + adj.oldTime.sectionCount - 1}节" else "第${adj.oldTime.startSection}节"
                        val newSection = if (adj.newTime.sectionCount > 1) "第${adj.newTime.startSection}-${adj.newTime.startSection + adj.newTime.sectionCount - 1}节" else "第${adj.newTime.startSection}节"
                        "     时间+地点：$oldDay $oldSection ${adj.oldTime.classroom} → $newDay $newSection ${adj.newTime.classroom}"
                    }
                }
                sb.append("$changeDesc\n")
                adj.teacher.takeIf { it.isNotEmpty() }?.let { sb.append("     教师：$it\n") }
                sb.append("\n")
            }
            if (changeResult.adjustedCourses.size > 3) sb.append("   ...等 ${changeResult.adjustedCourses.size - 3} 门\n")
        }
        
        sb.append("\n点击查看详情")
        return sb.toString().trim()
    }
}
