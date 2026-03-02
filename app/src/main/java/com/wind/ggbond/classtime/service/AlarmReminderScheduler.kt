package com.wind.ggbond.classtime.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.Reminder
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.data.repository.ClassTimeRepository
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ReminderRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.receiver.AlarmReminderReceiver
import com.wind.ggbond.classtime.util.DateUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AlarmManager 课程提醒调度器
 * 替代 WorkManager，使用 AlarmManager 实现精确的后台提醒调度
 */
@Singleton
class AlarmReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminderRepository: ReminderRepository,
    private val scheduleRepository: ScheduleRepository,
    private val classTimeRepository: ClassTimeRepository,
    private val courseRepository: CourseRepository
) {
    
    companion object {
        private const val TAG = "AlarmReminderScheduler"
    }
    
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    /**
     * 提醒统计信息
     */
    data class ReminderStats(
        val totalReminders: Int,
        val activeReminders: Int,
        val todayReminders: Int,
        val upcomingReminders: Int
    )
    
    /**
     * 为课程创建提醒
     */
    suspend fun scheduleCourseReminders(course: Course) {
        Log.d(TAG, "scheduleCourseReminders called for: ${course.courseName}, reminderEnabled = ${course.reminderEnabled}")
        
        if (!course.reminderEnabled) {
            Log.w(TAG, "课程 ${course.courseName} 已禁用提醒，reminderEnabled = false")
            return
        }
        
        Log.d(TAG, "开始为课程 ${course.courseName} 创建提醒，共 ${course.weeks.size} 个周次")
        
        val schedule = scheduleRepository.getCurrentSchedule() ?: run {
            Log.w(TAG, "未找到当前课表")
            return
        }
        
        val classTimes = classTimeRepository.getClassTimesByConfigSync()
        
        // 先取消该课程的所有旧提醒
        cancelCourseReminders(course.id)
        
        var scheduledCount = 0
        var failedCount = 0
        
        // 为每个上课周创建提醒
        course.weeks.forEach { weekNumber ->
            if (scheduleWeekReminder(course, schedule, weekNumber, classTimes)) {
                scheduledCount++
            } else {
                failedCount++
            }
        }
        
        if (failedCount > 0) {
            Log.w(TAG, "课程 ${course.courseName} 有 $failedCount 个提醒创建失败")
        }
        
        Log.d(TAG, "为课程 ${course.courseName} 创建了 $scheduledCount 个提醒（失败：$failedCount）")
    }
    
    /**
     * 批量为所有启用提醒的课程创建提醒
     */
    suspend fun scheduleAllCourseReminders(scheduleId: Long) {
        Log.d(TAG, "开始批量创建提醒，课表ID: $scheduleId")
        
        val courses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
        val enabledCourses = courses.filter { it.reminderEnabled }
        
        if (enabledCourses.isEmpty()) {
            Log.d(TAG, "没有启用提醒的课程")
            return
        }
        
        Log.d(TAG, "找到 ${enabledCourses.size} 门启用提醒的课程")
        
        // 获取课表和时间配置
        val schedule = scheduleRepository.getCurrentSchedule() ?: run {
            Log.w(TAG, "未找到当前课表")
            return
        }
        val classTimes = classTimeRepository.getClassTimesByConfigSync()
        
        // 先取消所有旧提醒
        cancelAllReminders()
        
        var totalScheduled = 0
        var totalFailed = 0
        
        // 为每门课程创建提醒
        enabledCourses.forEach { course ->
            course.weeks.forEach { weekNumber ->
                if (scheduleWeekReminder(course, schedule, weekNumber, classTimes)) {
                    totalScheduled++
                } else {
                    totalFailed++
                }
            }
        }
        
        Log.d(TAG, "批量创建完成: 共创建 $totalScheduled 个提醒（失败：$totalFailed）")
    }
    
    /**
     * 为单周创建提醒
     */
    private suspend fun scheduleWeekReminder(
        course: Course,
        schedule: Schedule,
        weekNumber: Int,
        classTimes: List<ClassTime>
    ): Boolean {
        try {
            // 获取该周的周一日期
            val monday = DateUtils.getMondayOfWeek(schedule.startDate, weekNumber)
            
            // 计算上课日期
            val courseDate = monday.plusDays((course.dayOfWeek - 1).toLong())
            
            // 如果日期已过，不创建提醒
            if (courseDate.isBefore(LocalDate.now())) {
                return false
            }
            
            // 获取上课时间
            val classTime = classTimes.find { it.sectionNumber == course.startSection } 
                ?: run {
                    Log.w(TAG, "未找到节次 ${course.startSection} 的时间配置")
                    return false
                }
            
            // 计算提醒时间
            val courseDateTime = LocalDateTime.of(courseDate, classTime.startTime)
            val reminderDateTime = courseDateTime.minusMinutes(course.reminderMinutes.toLong())
            
            // 如果提醒时间已过，不创建提醒
            if (reminderDateTime.isBefore(LocalDateTime.now())) {
                return false
            }
            
            val reminderTime = reminderDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            
            // 创建 AlarmManager 提醒
            val success = scheduleAlarm(
                courseId = course.id,
                weekNumber = weekNumber,
                triggerTime = reminderTime,
                isNextCourse = false
            )
            
            if (success) {
                // 保存提醒记录到数据库
                val reminder = Reminder(
                    courseId = course.id,
                    minutesBefore = course.reminderMinutes,
                    isEnabled = true,
                    weekNumber = weekNumber,
                    dayOfWeek = course.dayOfWeek,
                    triggerTime = reminderTime,
                    workRequestId = "alarm_${course.id}_${weekNumber}" // 改为 alarmRequestId 的标识
                )
                
                reminderRepository.insertReminder(reminder)
                
                Log.d(TAG, "已创建提醒: ${course.courseName}, 周$weekNumber, ${reminderDateTime}")
                
                // 检测下节课并创建下课前提醒
                scheduleNextCourseReminder(course, schedule, weekNumber, classTimes)
                
                return true
            }
            
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "创建提醒失败: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 使用 AlarmManager 调度提醒
     * 
     * Android 12+ 需要 SCHEDULE_EXACT_ALARM 权限才能使用精确闹钟
     * 如果没有精确闹钟权限，降级使用非精确闹钟（setAndAllowWhileIdle）
     */
    private fun scheduleAlarm(
        courseId: Long,
        weekNumber: Int,
        triggerTime: Long,
        isNextCourse: Boolean = false,
        currentCourseName: String = "",
        isSameCourseClassroom: Boolean = false
    ): Boolean {
        try {
            val intent = Intent(context, AlarmReminderReceiver::class.java).apply {
                action = AlarmReminderReceiver.ACTION_COURSE_REMINDER
                putExtra(AlarmReminderReceiver.EXTRA_COURSE_ID, courseId)
                putExtra(AlarmReminderReceiver.EXTRA_WEEK_NUMBER, weekNumber)
                putExtra(AlarmReminderReceiver.EXTRA_IS_NEXT_COURSE, isNextCourse)
                putExtra(AlarmReminderReceiver.EXTRA_CURRENT_COURSE_NAME, currentCourseName)
                putExtra(AlarmReminderReceiver.EXTRA_IS_SAME_COURSE_CLASSROOM, isSameCourseClassroom)
            }
            
            val requestCode = AlarmReminderReceiver.generateRequestCode(courseId, weekNumber, isNextCourse)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Android 12+ 需要检查精确闹钟权限
            val canUseExactAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
            
            if (canUseExactAlarm) {
                // 有精确闹钟权限：使用精确调度
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
                Log.d(TAG, "已设置精确闹钟提醒: courseId=$courseId, weekNumber=$weekNumber, 下节课=$isNextCourse, 触发时间=${java.util.Date(triggerTime)}")
            } else {
                // 无精确闹钟权限：降级使用非精确闹钟（仍可在Doze模式触发，但时间可能有几分钟偏差）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
                Log.w(TAG, "无精确闹钟权限，已降级为非精确闹钟: courseId=$courseId, weekNumber=$weekNumber, 触发时间=${java.util.Date(triggerTime)}")
            }

            return true
            
        } catch (e: SecurityException) {
            // Android 14+ 可能会抛出SecurityException
            Log.e(TAG, "设置闹钟权限不足: ${e.message}", e)
            // 尝试降级为非精确闹钟
            try {
                val fallbackIntent = Intent(context, AlarmReminderReceiver::class.java).apply {
                    action = AlarmReminderReceiver.ACTION_COURSE_REMINDER
                    putExtra(AlarmReminderReceiver.EXTRA_COURSE_ID, courseId)
                    putExtra(AlarmReminderReceiver.EXTRA_WEEK_NUMBER, weekNumber)
                    putExtra(AlarmReminderReceiver.EXTRA_IS_NEXT_COURSE, isNextCourse)
                    putExtra(AlarmReminderReceiver.EXTRA_CURRENT_COURSE_NAME, currentCourseName)
                    putExtra(AlarmReminderReceiver.EXTRA_IS_SAME_COURSE_CLASSROOM, isSameCourseClassroom)
                }
                val fallbackRequestCode = AlarmReminderReceiver.generateRequestCode(courseId, weekNumber, isNextCourse)
                val fallbackPendingIntent = PendingIntent.getBroadcast(
                    context,
                    fallbackRequestCode,
                    fallbackIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, fallbackPendingIntent)
                Log.w(TAG, "已降级使用普通闹钟: courseId=$courseId")
                return true
            } catch (fallbackException: Exception) {
                Log.e(TAG, "降级闹钟也失败", fallbackException)
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置 AlarmManager 提醒失败", e)
            return false
        }
    }
    
    /**
     * 测试用的公开调度方法
     * 用于设置界面的提醒测试功能
     */
    fun scheduleTestAlarm(
        courseId: Long,
        weekNumber: Int,
        triggerTime: Long,
        isNextCourse: Boolean = false,
        currentCourseName: String = "",
        isSameCourseClassroom: Boolean = false
    ): Boolean {
        return scheduleAlarm(
            courseId = courseId,
            weekNumber = weekNumber,
            triggerTime = triggerTime,
            isNextCourse = isNextCourse,
            currentCourseName = currentCourseName,
            isSameCourseClassroom = isSameCourseClassroom
        )
    }
    
    /**
     * 取消课程的所有提醒
     */
    suspend fun cancelCourseReminders(courseId: Long) {
        try {
            // 获取该课程的所有提醒记录
            val reminders = reminderRepository.getAllReminders().filter { it.courseId == courseId }
            
            reminders.forEach { reminder ->
                // 取消 AlarmManager 中的提醒
                val intent = Intent(context, AlarmReminderReceiver::class.java).apply {
                    action = AlarmReminderReceiver.ACTION_COURSE_REMINDER
                }
                
                val requestCode = AlarmReminderReceiver.generateRequestCode(
                    reminder.courseId, 
                    reminder.weekNumber, 
                    false
                )
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                alarmManager.cancel(pendingIntent)
                
                // 同时取消可能的下节课提醒
                val nextCourseRequestCode = AlarmReminderReceiver.generateRequestCode(
                    reminder.courseId, 
                    reminder.weekNumber, 
                    true
                )
                val nextCoursePendingIntent = PendingIntent.getBroadcast(
                    context,
                    nextCourseRequestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(nextCoursePendingIntent)
            }
            
            // 从数据库删除提醒记录
            reminderRepository.deleteRemindersByCourse(courseId)
            
            Log.d(TAG, "已取消课程ID $courseId 的所有提醒")
        } catch (e: Exception) {
            Log.e(TAG, "取消提醒失败: ${e.message}", e)
        }
    }
    
    /**
     * 取消所有提醒
     */
    suspend fun cancelAllReminders() {
        try {
            val allReminders = reminderRepository.getAllReminders()
            
            allReminders.forEach { reminder ->
                // 取消正常提醒
                val intent = Intent(context, AlarmReminderReceiver::class.java).apply {
                    action = AlarmReminderReceiver.ACTION_COURSE_REMINDER
                }
                
                val requestCode = AlarmReminderReceiver.generateRequestCode(
                    reminder.courseId, 
                    reminder.weekNumber, 
                    false
                )
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pendingIntent)
                
                // 取消下节课提醒
                val nextCourseRequestCode = AlarmReminderReceiver.generateRequestCode(
                    reminder.courseId, 
                    reminder.weekNumber, 
                    true
                )
                val nextCoursePendingIntent = PendingIntent.getBroadcast(
                    context,
                    nextCourseRequestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(nextCoursePendingIntent)
            }
            
            reminderRepository.deleteAllReminders()
            Log.d(TAG, "已取消所有提醒")
        } catch (e: Exception) {
            Log.e(TAG, "取消所有提醒失败: ${e.message}", e)
        }
    }
    
    /**
     * 批量取消指定课表下所有课程的提醒
     */
    suspend fun cancelAllCourseReminders(scheduleId: Long) {
        try {
            val courses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            
            courses.forEach { course ->
                cancelCourseReminders(course.id)
            }
            
            Log.d(TAG, "已取消课表 $scheduleId 下所有 ${courses.size} 门课程的提醒")
        } catch (e: Exception) {
            Log.e(TAG, "批量取消课程提醒失败: ${e.message}", e)
        }
    }
    
    /**
     * 重新调度所有提醒（用于开机后恢复）
     */
    suspend fun rescheduleAllReminders() {
        try {
            Log.d(TAG, "开始重新调度所有提醒...")
            
            // 获取当前课表
            val currentSchedule = scheduleRepository.getCurrentSchedule()
            if (currentSchedule == null) {
                Log.w(TAG, "未找到当前课表，无法重新调度提醒")
                return
            }
            
            // 重新调度所有启用提醒的课程
            scheduleAllCourseReminders(currentSchedule.id)
            
            Log.d(TAG, "重新调度所有提醒完成")
        } catch (e: Exception) {
            Log.e(TAG, "重新调度提醒失败", e)
        }
    }
    
    /**
     * 清理过期的提醒
     */
    suspend fun cleanExpiredReminders() {
        try {
            val deletedCount = reminderRepository.deleteExpiredReminders()
            Log.d(TAG, "清理了 $deletedCount 个过期提醒")
        } catch (e: Exception) {
            Log.e(TAG, "清理过期提醒失败: ${e.message}", e)
        }
    }
    
    /**
     * 获取提醒统计信息
     */
    suspend fun getReminderStats(): ReminderStats {
        val allReminders = reminderRepository.getAllReminders()
        val now = System.currentTimeMillis()
        val todayStart = LocalDate.now().atStartOfDay()
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayEnd = todayStart + 24 * 60 * 60 * 1000
        
        val activeReminders = allReminders.filter { it.triggerTime > now }
        val todayReminders = activeReminders.filter { 
            it.triggerTime in todayStart..todayEnd 
        }
        val upcomingReminders = activeReminders.filter { 
            it.triggerTime > todayEnd && it.triggerTime < todayEnd + 7 * 24 * 60 * 60 * 1000 
        }
        
        return ReminderStats(
            totalReminders = allReminders.size,
            activeReminders = activeReminders.size,
            todayReminders = todayReminders.size,
            upcomingReminders = upcomingReminders.size
        )
    }
    
    /**
     * 更新提醒时间
     */
    suspend fun updateReminderTime(courseId: Long, newReminderMinutes: Int) {
        val course = courseRepository.getCourseById(courseId) ?: return
        val updatedCourse = course.copy(reminderMinutes = newReminderMinutes)
        
        // 重新创建提醒
        scheduleCourseReminders(updatedCourse)
    }
    
    /**
     * 启用/禁用课程提醒
     */
    suspend fun toggleCourseReminder(courseId: Long, enabled: Boolean) {
        val course = courseRepository.getCourseById(courseId) ?: return
        
        if (enabled) {
            scheduleCourseReminders(course)
        } else {
            cancelCourseReminders(courseId)
        }
    }
    
    /**
     * 获取今天的提醒列表
     */
    suspend fun getTodayReminders(): List<Reminder> {
        val todayStart = LocalDate.now().atStartOfDay()
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayEnd = todayStart + 24 * 60 * 60 * 1000
        
        return reminderRepository.getAllReminders().filter {
            it.triggerTime in todayStart..todayEnd && it.isEnabled
        }
    }
    
    /**
     * 获取即将到来的提醒 (未来1小时内)
     */
    suspend fun getUpcomingReminders(): List<Reminder> {
        val now = System.currentTimeMillis()
        val oneHourLater = now + 60 * 60 * 1000
        
        return reminderRepository.getAllReminders().filter {
            it.triggerTime in now..oneHourLater && it.isEnabled
        }
    }
    
    /**
     * 检测下节课并创建下课前提醒
     */
    private suspend fun scheduleNextCourseReminder(
        currentCourse: Course,
        schedule: Schedule,
        weekNumber: Int,
        classTimes: List<ClassTime>
    ) {
        try {
            // 获取当前课程的结束节次
            val currentEndSection = currentCourse.startSection + currentCourse.sectionCount - 1
            
            // 只在第2、4、6、8、10节结束时提醒
            if (currentEndSection !in listOf(2, 4, 6, 8, 10)) {
                Log.d(TAG, "当前课程结束节次为第${currentEndSection}节，不是偶数节次，无需检查下节课提醒")
                return
            }
            
            val currentEndClassTime = classTimes.find { it.sectionNumber == currentEndSection }
                ?: run {
                    Log.w(TAG, "未找到节次 ${currentEndSection} 的时间配置")
                    return
                }
            
            // 计算当前课程结束时间
            val monday = DateUtils.getMondayOfWeek(schedule.startDate, weekNumber)
            val courseDate = monday.plusDays((currentCourse.dayOfWeek - 1).toLong())
            val currentCourseEndTime = LocalDateTime.of(courseDate, currentEndClassTime.endTime)
            
            // 如果课程已结束，不创建提醒
            if (currentCourseEndTime.isBefore(LocalDateTime.now())) {
                return
            }
            
            // 查找紧接着的下一节课
            val nextSectionNumber = currentEndSection + 1
            val nextCourses = findNextCoursesInTimeRange(
                currentCourse.scheduleId,
                currentCourse.dayOfWeek,
                nextSectionNumber,
                weekNumber,
                schedule,
                currentCourseEndTime,
                currentCourseEndTime.plusHours(1),
                classTimes
            )
            
            val exactNextCourse = nextCourses.find { it.startSection == nextSectionNumber }
            
            if (exactNextCourse == null) {
                Log.d(TAG, "第${currentEndSection}节后无第${nextSectionNumber}节课程，无需提醒")
                return
            }
            
            // 判断下节课是否与当前课程相同且教室相同
            val isSameCourseAndClassroom = (exactNextCourse.courseName == currentCourse.courseName) && 
                                           (exactNextCourse.classroom == currentCourse.classroom)
            
            // 在当前课程结束前1分钟创建提醒
            val reminderTime = currentCourseEndTime.minusMinutes(1)
            
            // 如果提醒时间已过，不创建
            if (reminderTime.isBefore(LocalDateTime.now())) {
                return
            }
            
            val reminderTimeMillis = reminderTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            
            // 创建下节课提醒
            val success = scheduleAlarm(
                courseId = exactNextCourse.id,
                weekNumber = weekNumber,
                triggerTime = reminderTimeMillis,
                isNextCourse = true,
                currentCourseName = currentCourse.courseName,
                isSameCourseClassroom = isSameCourseAndClassroom
            )
            
            if (success) {
                // 保存下节课提醒记录
                val reminder = Reminder(
                    courseId = exactNextCourse.id,
                    minutesBefore = 1, // 下课前1分钟
                    isEnabled = true,
                    weekNumber = weekNumber,
                    dayOfWeek = exactNextCourse.dayOfWeek,
                    triggerTime = reminderTimeMillis,
                    workRequestId = "alarm_next_${currentCourse.id}_${exactNextCourse.id}_${weekNumber}"
                )
                
                reminderRepository.insertReminder(reminder)
                
                val reminderType = if (isSameCourseAndClassroom) "继续上课" else "换课提醒"
                Log.d(TAG, "已创建下节课提醒(${reminderType}): ${currentCourse.courseName} → ${exactNextCourse.courseName}, 提醒时间: $reminderTime")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "创建下节课提醒失败: ${e.message}", e)
        }
    }
    
    /**
     * 查找指定时间范围内的下节课
     */
    private suspend fun findNextCoursesInTimeRange(
        scheduleId: Long,
        dayOfWeek: Int,
        startSection: Int,
        weekNumber: Int,
        schedule: Schedule,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        classTimes: List<ClassTime>
    ): List<Course> {
        val allCourses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            .filter { 
                it.dayOfWeek == dayOfWeek && 
                weekNumber in it.weeks
            }
        
        return allCourses.filter { course ->
            val courseStartClassTime = classTimes.find { it.sectionNumber == course.startSection }
                ?: return@filter false
            
            val monday = DateUtils.getMondayOfWeek(schedule.startDate, weekNumber)
            val courseDate = monday.plusDays((course.dayOfWeek - 1).toLong())
            val courseStartTime = LocalDateTime.of(courseDate, courseStartClassTime.startTime)
            
            courseStartTime.isAfter(startTime) && 
            courseStartTime.isBefore(endTime) &&
            course.startSection >= startSection
        }
    }
}
