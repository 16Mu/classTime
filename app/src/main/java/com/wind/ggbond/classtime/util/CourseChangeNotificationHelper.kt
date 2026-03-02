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

/**
 * 课程变更通知助手
 * 
 * 功能：当检测到课表变更时，发送通知提醒用户
 * 
 * @author AI Assistant
 * @since 2025-11-05
 */
object CourseChangeNotificationHelper {
    
    private const val CHANNEL_ID = "course_change_notification"
    private const val CHANNEL_NAME = "课表变更通知"
    private const val NOTIFICATION_ID = 3001
    
    /**
     * 创建通知渠道（Android 8.0+）
     */
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 检查渠道是否已存在
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
    
    /**
     * 发送课表变更通知
     * 
     * @param context 上下文
     * @param changeResult 变更检测结果
     */
    fun sendChangeNotification(context: Context, changeResult: CourseChangeResult) {
        // 确保通知渠道存在
        createNotificationChannel(context)
        
        // 生成通知内容
        val title = "时课 课表有更新"
        val summary = with(CourseChangeDetector) { changeResult.getSummary() }
        val detailedMessage = with(CourseChangeDetector) { changeResult.getDetailedMessage() }
        
        // 创建点击通知的Intent（跳转到主页面）
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 构建通知
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(buildNotificationContent(changeResult))
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        
        // 发送通知
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * 发送"无课程更新"通知（可选，一般不需要）
     */
    fun sendNoChangeNotification(context: Context) {
        createNotificationChannel(context)
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
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
    
    /**
     * 发送更新失败通知
     */
    fun sendFailureNotification(context: Context, errorMessage: String) {
        createNotificationChannel(context)
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
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
    
    /**
     * 构建通知内容
     */
    private fun buildNotificationContent(changeResult: CourseChangeResult): String {
        val sb = StringBuilder()
        
        if (changeResult.addedCourses.isNotEmpty()) {
            sb.append("新增 ${changeResult.addedCourses.size} 门课程\n")
            changeResult.addedCourses.take(3).forEach { course ->
                sb.append("   · ${course.courseName}\n")
                // 添加更多详细信息
                course.classroom.takeIf { it.isNotEmpty() }?.let { classroom ->
                    sb.append("     地点：$classroom\n")
                }
                course.teacher.takeIf { it.isNotEmpty() }?.let { teacher ->
                    sb.append("     教师：$teacher\n")
                }
                val dayNames = mapOf(
                    1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四",
                    5 to "周五", 6 to "周六", 7 to "周日"
                )
                val dayName = dayNames[course.dayOfWeek] ?: "周${course.dayOfWeek}"
                val sectionText = if (course.sectionCount > 1) {
                    "第${course.startSection}-${course.startSection + course.sectionCount - 1}节"
                } else {
                    "第${course.startSection}节"
                }
                sb.append("     时间：$dayName $sectionText\n")
                sb.append("\n")
            }
            if (changeResult.addedCourses.size > 3) {
                sb.append("   ...等 ${changeResult.addedCourses.size - 3} 门\n")
            }
            sb.append("\n")
        }
        
        if (changeResult.removedCourses.isNotEmpty()) {
            sb.append("删除 ${changeResult.removedCourses.size} 门课程\n")
            changeResult.removedCourses.take(3).forEach { course ->
                sb.append("   · ${course.courseName}\n")
                // 添加更多详细信息
                course.classroom.takeIf { it.isNotEmpty() }?.let { classroom ->
                    sb.append("     地点：$classroom\n")
                }
                course.teacher.takeIf { it.isNotEmpty() }?.let { teacher ->
                    sb.append("     教师：$teacher\n")
                }
                val dayNames = mapOf(
                    1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四",
                    5 to "周五", 6 to "周六", 7 to "周日"
                )
                val dayName = dayNames[course.dayOfWeek] ?: "周${course.dayOfWeek}"
                val sectionText = if (course.sectionCount > 1) {
                    "第${course.startSection}-${course.startSection + course.sectionCount - 1}节"
                } else {
                    "第${course.startSection}节"
                }
                sb.append("     时间：$dayName $sectionText\n")
                sb.append("\n")
            }
            if (changeResult.removedCourses.size > 3) {
                sb.append("   ...等 ${changeResult.removedCourses.size - 3} 门\n")
            }
            sb.append("\n")
        }
        
        if (changeResult.adjustedCourses.isNotEmpty()) {
            sb.append("调课 ${changeResult.adjustedCourses.size} 门课程\n")
            changeResult.adjustedCourses.take(3).forEach { adj ->
                sb.append("   · ${adj.courseName}\n")
                // 根据变更类型显示详细信息
                val changeDesc = when (adj.changeType) {
                    CourseChangeType.TIME_ONLY -> {
                        val dayNames = mapOf(
                            1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四",
                            5 to "周五", 6 to "周六", 7 to "周日"
                        )
                        val oldDay = dayNames[adj.oldTime.dayOfWeek] ?: "周${adj.oldTime.dayOfWeek}"
                        val newDay = dayNames[adj.newTime.dayOfWeek] ?: "周${adj.newTime.dayOfWeek}"
                        val oldSection = if (adj.oldTime.sectionCount > 1) {
                            "第${adj.oldTime.startSection}-${adj.oldTime.startSection + adj.oldTime.sectionCount - 1}节"
                        } else {
                            "第${adj.oldTime.startSection}节"
                        }
                        val newSection = if (adj.newTime.sectionCount > 1) {
                            "第${adj.newTime.startSection}-${adj.newTime.startSection + adj.newTime.sectionCount - 1}节"
                        } else {
                            "第${adj.newTime.startSection}节"
                        }
                        "     时间：$oldDay $oldSection → $newDay $newSection"
                    }
                    CourseChangeType.LOCATION_ONLY -> {
                        "     地点：${adj.oldTime.classroom} → ${adj.newTime.classroom}"
                    }
                    CourseChangeType.TIME_AND_LOCATION -> {
                        val dayNames = mapOf(
                            1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四",
                            5 to "周五", 6 to "周六", 7 to "周日"
                        )
                        val oldDay = dayNames[adj.oldTime.dayOfWeek] ?: "周${adj.oldTime.dayOfWeek}"
                        val newDay = dayNames[adj.newTime.dayOfWeek] ?: "周${adj.newTime.dayOfWeek}"
                        val oldSection = if (adj.oldTime.sectionCount > 1) {
                            "第${adj.oldTime.startSection}-${adj.oldTime.startSection + adj.oldTime.sectionCount - 1}节"
                        } else {
                            "第${adj.oldTime.startSection}节"
                        }
                        val newSection = if (adj.newTime.sectionCount > 1) {
                            "第${adj.newTime.startSection}-${adj.newTime.startSection + adj.newTime.sectionCount - 1}节"
                        } else {
                            "第${adj.newTime.startSection}节"
                        }
                        "     时间+地点：$oldDay $oldSection ${adj.oldTime.classroom} → $newDay $newSection ${adj.newTime.classroom}"
                    }
                }
                sb.append("$changeDesc\n")
                // 添加教师信息
                adj.teacher.takeIf { it.isNotEmpty() }?.let { teacher ->
                    sb.append("     教师：$teacher\n")
                }
                sb.append("\n")
            }
            if (changeResult.adjustedCourses.size > 3) {
                sb.append("   ...等 ${changeResult.adjustedCourses.size - 3} 门\n")
            }
        }
        
        sb.append("\n点击查看详情")
        
        return sb.toString().trim()
    }
}





