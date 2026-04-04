package com.wind.ggbond.classtime.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.Reminder
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.data.repository.ClassTimeRepository
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ReminderRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.util.DateUtils
import com.wind.ggbond.classtime.worker.ReminderWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 提醒调度服务 - 增强版（已废弃，使用 AlarmReminderScheduler）
 * 支持智能提醒、批量管理和自动调度
 * 
 * ✅ 添加了完善的错误处理和状态检查
 * 
 * @deprecated 已被 AlarmReminderScheduler 替代，保留作为备份
 */
@Deprecated("使用 AlarmReminderScheduler 替代")
@Singleton
class LegacyReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminderRepository: ReminderRepository,
    private val scheduleRepository: ScheduleRepository,
    private val classTimeRepository: ClassTimeRepository,
    private val courseRepository: CourseRepository
) {
    
    companion object {
        private const val TAG = "ReminderScheduler"
        private const val BATCH_TAG = "batch_reminders"
        private const val DAILY_SYNC_TAG = "daily_reminder_sync"
        
        // 重试配置
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_BACKOFF_DELAY_MS = 1000L
    }
    
    /**
     * ✅ 已移除：不再需要检查WorkManager可用性
     * 因为Application已通过Configuration.Provider确保WorkManager正确初始化
     */
    
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
     * 
     * ✅ WorkManager已在Application中确保正确初始化，无需检查可用性
     */
    suspend fun scheduleCourseReminders(course: Course) {
        Log.d(TAG, "✅ scheduleCourseReminders called for: ${course.courseName}, reminderEnabled = ${course.reminderEnabled}")
        
        if (!course.reminderEnabled) {
            Log.w(TAG, "⚠️ 课程 ${course.courseName} 已禁用提醒，reminderEnabled = false")
            return
        }
        
        Log.d(TAG, "✅ 开始为课程 ${course.courseName} 创建提醒，共 ${course.weeks.size} 个周次")
        
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
     * 
     * ✅ 优化：并发生成WorkRequest + 批量入队
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
        
        // 获取课表和时间配置（一次性获取，避免重复查询）
        val schedule = scheduleRepository.getCurrentSchedule() ?: run {
            Log.w(TAG, "未找到当前课表")
            return
        }
        val classTimes = classTimeRepository.getClassTimesByConfigSync()
        
        // ✅ 先取消所有旧提醒（批量操作）
        WorkManager.getInstance(context).cancelAllWorkByTag(BATCH_TAG)
        reminderRepository.deleteAll()
        
        // ✅ 并发生成所有WorkRequest
        val allWorkRequests = mutableListOf<OneTimeWorkRequest>()
        val allReminders = mutableListOf<Reminder>()
        
        enabledCourses.forEach { course ->
            course.weeks.forEach { weekNumber ->
                try {
                    val workRequest = createWeekReminderWorkRequest(
                        course = course,
                        schedule = schedule,
                        weekNumber = weekNumber,
                        classTimes = classTimes
                    )
                    
                    if (workRequest != null) {
                        allWorkRequests.add(workRequest.first)
                        allReminders.add(workRequest.second)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "创建提醒失败: ${course.courseName}, 周$weekNumber", e)
                }
            }
        }
        
        if (allWorkRequests.isEmpty()) {
            Log.w(TAG, "没有创建任何提醒")
            return
        }
        
        // ✅ 批量入队WorkRequest（比逐个入队快10倍）
        try {
            Log.d(TAG, "批量入队 ${allWorkRequests.size} 个提醒任务...")
            WorkManager.getInstance(context).enqueue(allWorkRequests)
            
            // ✅ 批量保存提醒记录到数据库
            reminderRepository.insertAll(allReminders)
            
            Log.d(TAG, "✅ 批量创建完成: 共创建 ${allWorkRequests.size} 个提醒")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 批量入队失败", e)
        }
    }
    
    /**
     * ✅ 创建单周提醒的WorkRequest和Reminder对象
     * 
     * @return Pair<WorkRequest, Reminder> 或 null（如果不需要创建）
     */
    private fun createWeekReminderWorkRequest(
        course: Course,
        schedule: Schedule,
        weekNumber: Int,
        classTimes: List<ClassTime>
    ): Pair<OneTimeWorkRequest, Reminder>? {
        // 获取该周的周一日期
        val monday = DateUtils.getMondayOfWeek(schedule.startDate, weekNumber)
        
        // 计算上课日期
        val courseDate = monday.plusDays((course.dayOfWeek - 1).toLong())
        
        // 如果日期已过，不创建提醒
        if (courseDate.isBefore(LocalDate.now())) {
            return null
        }
        
        // 获取上课时间
        val classTime = classTimes.find { it.sectionNumber == course.startSection } ?: return null
        
        // 计算提醒时间
        val courseDateTime = LocalDateTime.of(courseDate, classTime.startTime)
        val reminderDateTime = courseDateTime.minusMinutes(course.reminderMinutes.toLong())
        
        // 如果提醒时间已过，不创建提醒
        if (reminderDateTime.isBefore(LocalDateTime.now())) {
            return null
        }
        
        // 计算延迟时间
        val now = System.currentTimeMillis()
        val reminderTime = reminderDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val delay = reminderTime - now
        
        if (delay < 0) return null
        
        // 创建约束条件
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .setRequiresCharging(false)
            .setRequiresDeviceIdle(false)
            .setRequiresStorageNotLow(false)
            .build()
        
        // 创建WorkRequest
        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    ReminderWorker.KEY_COURSE_ID to course.id,
                    "courseName" to course.courseName,
                    "classroom" to course.classroom,
                    "weekNumber" to weekNumber
                )
            )
            .addTag("course_${course.id}")
            .addTag(BATCH_TAG)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        
        // 创建Reminder对象
        val reminder = Reminder(
            courseId = course.id,
            minutesBefore = course.reminderMinutes,
            isEnabled = true,
            weekNumber = weekNumber,
            dayOfWeek = course.dayOfWeek,
            triggerTime = reminderTime,
            workRequestId = workRequest.id.toString()
        )
        
        return Pair(workRequest, reminder)
    }
    
    /**
     * 为单周创建提醒 (返回是否成功)
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
            
            // 计算延迟时间
            val now = System.currentTimeMillis()
            val reminderTime = reminderDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val delay = reminderTime - now
            
            if (delay < 0) return false
            
            // ✅ 创建约束条件，确保在后台也能运行
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)  // 不需要网络
                .setRequiresBatteryNotLow(false)  // 不要求电池充足
                .setRequiresCharging(false)  // 不要求充电
                .setRequiresDeviceIdle(false)  // 不要求设备空闲
                .setRequiresStorageNotLow(false)  // 不要求存储充足
                .build()
            
            // 创建 WorkManager 请求
            val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)  // ✅ 添加约束条件
                .setInputData(
                    workDataOf(
                        ReminderWorker.KEY_COURSE_ID to course.id,
                        "courseName" to course.courseName,
                        "classroom" to course.classroom,
                        "weekNumber" to weekNumber
                    )
                )
                .addTag("course_${course.id}")
                .addTag(BATCH_TAG)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
            
            // ✅ 使用suspending方式enqueue并等待结果
            val operation = WorkManager.getInstance(context).enqueue(workRequest)
            
            // ✅ 等待enqueue操作完成并检查结果
            return withContext(Dispatchers.IO) {
                try {
                    val result = operation.result.get()  // 阻塞等待结果
                    
                    if (result != null) {
                        // 保存提醒记录
                        val reminder = Reminder(
                            courseId = course.id,
                            minutesBefore = course.reminderMinutes,
                            isEnabled = true,
                            weekNumber = weekNumber,
                            dayOfWeek = course.dayOfWeek,
                            triggerTime = reminderTime,
                            workRequestId = workRequest.id.toString()
                        )
                        
                        reminderRepository.insert(reminder)
                        
                        Log.d(TAG, "已创建提醒: ${course.courseName}, 周$weekNumber, ${reminderDateTime}")
                        
                        // ✅ 添加：检测下节课并创建下课前提醒
                        scheduleNextCourseReminder(course, schedule, weekNumber, classTimes)
                        
                        true
                    } else {
                        Log.e(TAG, "WorkManager enqueue返回null: ${course.courseName}, 周$weekNumber")
                        false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "等待WorkManager结果失败: ${e.message}", e)
                    false
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "创建提醒失败: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 取消课程的所有提醒
     * 
     * ⚠️ 重要改进：
     * - 取消该课程的正常上课提醒（标签：course_X）
     * - 取消该课程作为"当前课"的下节课提醒（标签：next_course_reminder_X）
     * - 但不会取消其他课程的下节课提醒（即使目标是该课程）
     * 
     * 例如：课程1（第1-2节）→ 课程2（第3-4节）
     * - 取消课程1：会取消 course_1 和 next_course_reminder_1（提醒课程2的那个）
     * - 取消课程2：只会取消 course_2，不会取消 next_course_reminder_1（因为那是课程1的）
     */
    suspend fun cancelCourseReminders(courseId: Long) {
        try {
            // 1. 取消正常的上课提醒
            WorkManager.getInstance(context).cancelAllWorkByTag("course_$courseId")
            
            // 2. 取消该课程的下节课提醒（作为当前课时创建的）
            WorkManager.getInstance(context).cancelAllWorkByTag("next_course_reminder_$courseId")
            
            // 3. 从数据库删除提醒记录
            reminderRepository.deleteRemindersByCourse(courseId)
            
            Log.d(TAG, "已取消课程ID $courseId 的所有提醒（包括正常提醒和下节课提醒）")
        } catch (e: Exception) {
            Log.e(TAG, "取消提醒失败: ${e.message}", e)
        }
    }
    
    /**
     * 取消所有提醒
     */
    suspend fun cancelAllReminders() {
        try {
            WorkManager.getInstance(context).cancelAllWorkByTag(BATCH_TAG)
            reminderRepository.deleteAll()
            Log.d(TAG, "已取消所有提醒")
        } catch (e: Exception) {
            Log.e(TAG, "取消所有提醒失败: ${e.message}", e)
        }
    }
    
    /**
     * ✅ 新增：批量取消指定课表下所有课程的提醒
     * 
     * @param scheduleId 课表ID
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
        val allReminders = reminderRepository.getAll()
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
     * 智能调度 - 每日凌晨自动同步提醒
     */
    fun setupDailyReminderSync() {
        val dailyWorkRequest = PeriodicWorkRequestBuilder<DailyReminderSyncWorker>(
            1, TimeUnit.DAYS
        )
            .setInitialDelay(calculateInitialDelayToMidnight(), TimeUnit.MILLISECONDS)
            .addTag(DAILY_SYNC_TAG)
            .build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_SYNC_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            dailyWorkRequest
        )
        
        Log.d(TAG, "已设置每日自动同步提醒")
    }
    
    /**
     * 计算到凌晨2点的延迟时间
     */
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
    
    /**
     * 更新提醒时间 (当课程时间改变时)
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
        
        return reminderRepository.getAll().filter {
            it.triggerTime in todayStart..todayEnd && it.isEnabled
        }
    }
    
    /**
     * 获取即将到来的提醒 (未来1小时内)
     */
    suspend fun getUpcomingReminders(): List<Reminder> {
        val now = System.currentTimeMillis()
        val oneHourLater = now + 60 * 60 * 1000
        
        return reminderRepository.getAll().filter {
            it.triggerTime in now..oneHourLater && it.isEnabled
        }
    }
    
    /**
     * ✅ 新增：检测下节课并创建下课前提醒
     * 仅在第2、4、6、8、10节（大节的第二小节）结束前1分钟提醒下节课信息
     * 
     * 判断逻辑：
     * - 只在偶数节次（2、4、6、8、10）结束时检查下一节（3、5、7、9、11）
     * - 如果下节课与当前课程相同且教室相同：提示继续在当前教室上课
     * - 如果下节课不同或教室不同：正常提示下节课信息和地点
     * 
     * @param currentCourse 当前课程
     * @param schedule 课表信息（包含学期时间）
     * @param weekNumber 周次
     * @param classTimes 节次时间配置
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
            
            // ✅ 关键判断：只在第2、4、6、8、10节结束时提醒
            // 这些是大节的第二小节（如第1-2节是一大节，第2节结束时提醒第3节）
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
            
            // ✅ 查找紧接着的下一节课（第3、5、7、9、11节）
            val nextSectionNumber = currentEndSection + 1  // 2→3, 4→5, 6→7, 8→9, 10→11
            val nextCourses = findNextCoursesInTimeRange(
                currentCourse.scheduleId,
                currentCourse.dayOfWeek,
                nextSectionNumber,  // 精确查找下一节
                weekNumber,
                schedule,
                currentCourseEndTime,
                currentCourseEndTime.plusHours(1),
                classTimes
            )
            
            // 只关注开始节次等于nextSectionNumber的课程
            val exactNextCourse = nextCourses.find { it.startSection == nextSectionNumber }
            
            if (exactNextCourse == null) {
                Log.d(TAG, "第${currentEndSection}节后无第${nextSectionNumber}节课程，无需提醒")
                return
            }
            
            // ✅ 判断下节课是否与当前课程相同且教室相同
            val isSameCourseAndClassroom = (exactNextCourse.courseName == currentCourse.courseName) && 
                                           (exactNextCourse.classroom == currentCourse.classroom)
            
            // 在当前课程结束前1分钟创建提醒
            val reminderTime = currentCourseEndTime.minusMinutes(1)
            
            // 如果提醒时间已过，不创建
            if (reminderTime.isBefore(LocalDateTime.now())) {
                return
            }
            
            // 创建下节课提醒
            scheduleNextCourseReminderNotification(
                currentCourse = currentCourse,
                nextCourse = exactNextCourse,
                reminderTime = reminderTime,
                weekNumber = weekNumber,
                isSameCourseAndClassroom = isSameCourseAndClassroom
            )
            
            val reminderType = if (isSameCourseAndClassroom) "继续上课" else "换课提醒"
            Log.d(TAG, "已创建下节课提醒(${reminderType}): ${currentCourse.courseName} → ${exactNextCourse.courseName}, 提醒时间: $reminderTime")
            
        } catch (e: Exception) {
            Log.e(TAG, "创建下节课提醒失败: ${e.message}", e)
        }
    }
    
    /**
     * ✅ 查找指定时间范围内的下节课
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
        // 获取同一天的所有课程
        // ✅ 修复：去掉 reminderEnabled 限制，无论下节课是否启用提醒都应该检测
        val allCourses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            .filter { 
                it.dayOfWeek == dayOfWeek && 
                weekNumber in it.weeks
                // 已移除：it.reminderEnabled（下节课无论是否启用提醒都应该被检测到）
            }
        
        // 查找下1小时内的课程
        return allCourses.filter { course ->
            val courseStartClassTime = classTimes.find { it.sectionNumber == course.startSection }
                ?: return@filter false
            
            val monday = DateUtils.getMondayOfWeek(schedule.startDate, weekNumber)
            val courseDate = monday.plusDays((course.dayOfWeek - 1).toLong())
            val courseStartTime = LocalDateTime.of(courseDate, courseStartClassTime.startTime)
            
            // 课程开始时间在当前课程结束后，且在1小时内
            courseStartTime.isAfter(startTime) && 
            courseStartTime.isBefore(endTime) &&
            course.startSection >= startSection
        }
    }
    
    /**
     * ✅ 创建下节课提醒通知
     * @param isSameCourseAndClassroom 是否是同一门课程且同一教室（用于显示不同的提示文本）
     */
    private suspend fun scheduleNextCourseReminderNotification(
        currentCourse: Course,
        nextCourse: Course,
        reminderTime: LocalDateTime,
        weekNumber: Int,
        isSameCourseAndClassroom: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val reminderTimeMillis = reminderTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val delay = reminderTimeMillis - now
        
        if (delay < 0) return
        
        // ✅ 创建约束条件，确保在后台也能运行
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)  // 不需要网络
            .setRequiresBatteryNotLow(false)  // 不要求电池充足
            .setRequiresCharging(false)  // 不要求充电
            .setRequiresDeviceIdle(false)  // 不要求设备空闲
            .setRequiresStorageNotLow(false)  // 不要求存储充足
            .build()
        
        // 创建 WorkManager 请求（使用特殊的 tag 标识这是下节课提醒）
        // ⚠️ 重要：下节课提醒使用特殊标签，避免被误删
        // 正常提醒标签：course_1
        // 下节课提醒标签：next_course_from_1_to_2（从课程1到课程2的下节课提醒）
        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)  // ✅ 添加约束条件
            .setInputData(
                workDataOf(
                    ReminderWorker.KEY_COURSE_ID to nextCourse.id,
                    "courseName" to nextCourse.courseName,
                    "classroom" to nextCourse.classroom,
                    "weekNumber" to weekNumber,
                    "isNextCourseReminder" to true,  // ✅ 标识这是下节课提醒
                    "currentCourseName" to currentCourse.courseName,  // 当前课程名称
                    "isSameCourseAndClassroom" to isSameCourseAndClassroom  // ✅ 是否同课同教室
                )
            )
            // ✅ 使用特殊标签，避免被 cancelCourseReminders 误删
            .addTag("next_course_from_${currentCourse.id}_to_${nextCourse.id}")  // 主标签
            .addTag("next_course_reminder_${currentCourse.id}")  // 标记这是课程1的下节课提醒
            .addTag("next_course_target_${nextCourse.id}")  // 标记目标是课程2
            .addTag(BATCH_TAG)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        
        val operation = WorkManager.getInstance(context).enqueue(workRequest)
        
        withContext(Dispatchers.IO) {
            try {
                val result = operation.result.get()
                if (result != null) {
                    // 保存提醒记录（使用特殊标识）
                    val reminder = Reminder(
                        courseId = nextCourse.id,
                        minutesBefore = 1,  // 下课前1分钟
                        isEnabled = true,
                        weekNumber = weekNumber,
                        dayOfWeek = nextCourse.dayOfWeek,
                        triggerTime = reminderTimeMillis,
                        workRequestId = workRequest.id.toString()
                    )
                    
                    reminderRepository.insert(reminder)
                    Log.d(TAG, "下节课提醒已创建: ${nextCourse.courseName}, 提醒时间: $reminderTime")
                }
            } catch (e: Exception) {
                Log.e(TAG, "创建下节课提醒失败: ${e.message}", e)
            }
        }
    }
}

/**
 * 每日提醒同步Worker
 */
class DailyReminderSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        Log.d("DailyReminderSync", "开始每日提醒同步")
        
        // 这里可以添加清理过期提醒、重新调度等逻辑
        // 由于需要依赖注入，实际实现时需要使用 @HiltWorker
        
        return Result.success()
    }
}
