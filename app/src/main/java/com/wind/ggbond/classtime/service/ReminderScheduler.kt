package com.wind.ggbond.classtime.service

import android.content.Context
import androidx.work.*
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.Reminder
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.data.repository.ClassTimeRepository
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ReminderRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.domain.usecase.ReminderUseCase
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
import com.wind.ggbond.classtime.util.AppLogger
import kotlinx.coroutines.withContext

@Deprecated("使用 UnifiedReminderScheduler 替代")
@Singleton
class LegacyReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminderRepository: ReminderRepository,
    private val scheduleRepository: ScheduleRepository,
    private val classTimeRepository: ClassTimeRepository,
    private val courseRepository: CourseRepository,
    private val reminderUseCase: ReminderUseCase
) {

    companion object {
        private const val TAG = "ReminderScheduler"
        private const val BATCH_TAG = "batch_reminders"
        private const val DAILY_SYNC_TAG = "daily_reminder_sync"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_BACKOFF_DELAY_MS = 1000L
    }

    data class ReminderStats(
        val totalReminders: Int,
        val activeReminders: Int,
        val todayReminders: Int,
        val upcomingReminders: Int
    )

    suspend fun scheduleCourseReminders(course: Course) {
        AppLogger.d(TAG, "scheduleCourseReminders called for: ${course.courseName}, reminderEnabled = ${course.reminderEnabled}")

        if (!course.reminderEnabled) {
            AppLogger.w(TAG, "课程 ${course.courseName} 已禁用提醒")
            return
        }

        val schedule = scheduleRepository.getCurrentSchedule() ?: return
        val classTimes = classTimeRepository.getClassTimesByConfigSync()
        cancelCourseReminders(course.id)

        var scheduledCount = 0
        var failedCount = 0
        course.weeks.forEach { weekNumber ->
            if (scheduleWeekReminder(course, schedule, weekNumber, classTimes)) {
                scheduledCount++
            } else {
                failedCount++
            }
        }

        AppLogger.d(TAG, "为课程 ${course.courseName} 创建了 $scheduledCount 个提醒（失败：$failedCount）")
    }

    suspend fun scheduleAllCourseReminders(scheduleId: Long) {
        val courses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
        val enabledCourses = courses.filter { it.reminderEnabled }
        if (enabledCourses.isEmpty()) return

        val schedule = scheduleRepository.getCurrentSchedule() ?: return
        val classTimes = classTimeRepository.getClassTimesByConfigSync()

        WorkManager.getInstance(context).cancelAllWorkByTag(BATCH_TAG)
        reminderRepository.deleteRemindersBySchedule(scheduleId)

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
                    AppLogger.e(TAG, "创建提醒失败: ${course.courseName}, 周$weekNumber", e)
                }
            }
        }

        if (allWorkRequests.isEmpty()) return

        try {
            WorkManager.getInstance(context).enqueue(allWorkRequests)
            reminderRepository.insertAll(allReminders)
            AppLogger.d(TAG, "批量创建完成: 共创建 ${allWorkRequests.size} 个提醒")
        } catch (e: Exception) {
            AppLogger.e(TAG, "批量入队失败", e)
        }
    }

    private fun createWeekReminderWorkRequest(
        course: Course,
        schedule: Schedule,
        weekNumber: Int,
        classTimes: List<ClassTime>
    ): Pair<OneTimeWorkRequest, Reminder>? {
        val reminderTime = reminderUseCase.calculateReminderTime(course, schedule, weekNumber, classTimes)
            ?: return null

        val now = System.currentTimeMillis()
        val delay = reminderTime - now
        if (delay < 0) return null

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .setRequiresCharging(false)
            .setRequiresDeviceIdle(false)
            .setRequiresStorageNotLow(false)
            .build()

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

    private suspend fun scheduleWeekReminder(
        course: Course,
        schedule: Schedule,
        weekNumber: Int,
        classTimes: List<ClassTime>
    ): Boolean {
        try {
            val reminderTime = reminderUseCase.calculateReminderTime(course, schedule, weekNumber, classTimes)
                ?: return false

            val now = System.currentTimeMillis()
            val delay = reminderTime - now
            if (delay < 0) return false

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(false)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .setRequiresStorageNotLow(false)
                .build()

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

            val operation = WorkManager.getInstance(context).enqueue(workRequest)

            return try {
                val result = operation.result.await()
                if (result != null) {
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
                    scheduleNextCourseReminder(course, schedule, weekNumber, classTimes)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "等待WorkManager结果失败: ${e.message}", e)
                false
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "创建提醒失败: ${e.message}", e)
            return false
        }
    }

    suspend fun cancelCourseReminders(courseId: Long) {
        try {
            WorkManager.getInstance(context).cancelAllWorkByTag("course_$courseId")
            WorkManager.getInstance(context).cancelAllWorkByTag("next_course_reminder_$courseId")
            reminderRepository.deleteRemindersByCourse(courseId)
        } catch (e: Exception) {
            AppLogger.e(TAG, "取消提醒失败: ${e.message}", e)
        }
    }

    suspend fun cancelAllReminders() {
        try {
            WorkManager.getInstance(context).cancelAllWorkByTag(BATCH_TAG)
            reminderRepository.deleteAll()
        } catch (e: Exception) {
            AppLogger.e(TAG, "取消所有提醒失败: ${e.message}", e)
        }
    }

    suspend fun cancelAllCourseReminders(scheduleId: Long) {
        try {
            val courses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            courses.forEach { course -> cancelCourseReminders(course.id) }
        } catch (e: Exception) {
            AppLogger.e(TAG, "批量取消课程提醒失败: ${e.message}", e)
        }
    }

    suspend fun cleanExpiredReminders() {
        try {
            reminderUseCase.cleanExpiredReminders()
        } catch (e: Exception) {
            AppLogger.e(TAG, "清理过期提醒失败: ${e.message}", e)
        }
    }

    suspend fun getReminderStats(): ReminderStats {
        val stats = reminderUseCase.getReminderStats()
        return ReminderStats(
            totalReminders = stats.totalReminders,
            activeReminders = stats.activeReminders,
            todayReminders = stats.todayReminders,
            upcomingReminders = stats.upcomingReminders
        )
    }

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

    suspend fun updateReminderTime(courseId: Long, newReminderMinutes: Int) {
        val course = courseRepository.getCourseById(courseId) ?: return
        scheduleCourseReminders(course.copy(reminderMinutes = newReminderMinutes))
    }

    suspend fun toggleCourseReminder(courseId: Long, enabled: Boolean) {
        val course = courseRepository.getCourseById(courseId) ?: return
        if (enabled) scheduleCourseReminders(course) else cancelCourseReminders(courseId)
    }

    suspend fun getTodayReminders(): List<Reminder> {
        val todayStart = LocalDate.now().atStartOfDay()
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return reminderRepository.getAll().filter {
            it.triggerTime in todayStart..(todayStart + ReminderUseCase.MS_PER_DAY) && it.isEnabled
        }
    }

    suspend fun getUpcomingReminders(): List<Reminder> {
        val now = System.currentTimeMillis()
        return reminderRepository.getAll().filter {
            it.triggerTime in now..(now + 60 * 60 * 1000) && it.isEnabled
        }
    }

    private suspend fun scheduleNextCourseReminder(
        currentCourse: Course,
        schedule: Schedule,
        weekNumber: Int,
        classTimes: List<ClassTime>
    ) {
        try {
            val allCourses = courseRepository.getAllCoursesBySchedule(currentCourse.scheduleId).first()
            val info = reminderUseCase.calculateNextCourseReminderInfo(
                currentCourse, schedule, weekNumber, classTimes, allCourses
            ) ?: return

            scheduleNextCourseReminderNotification(
                currentCourse = currentCourse,
                nextCourse = info.nextCourse,
                reminderTimeMillis = info.triggerTime,
                weekNumber = weekNumber,
                isSameCourseAndClassroom = info.isSameCourseAndClassroom
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "创建下节课提醒失败: ${e.message}", e)
        }
    }

    private suspend fun scheduleNextCourseReminderNotification(
        currentCourse: Course,
        nextCourse: Course,
        reminderTimeMillis: Long,
        weekNumber: Int,
        isSameCourseAndClassroom: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val delay = reminderTimeMillis - now
        if (delay < 0) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .setRequiresCharging(false)
            .setRequiresDeviceIdle(false)
            .setRequiresStorageNotLow(false)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    ReminderWorker.KEY_COURSE_ID to nextCourse.id,
                    "courseName" to nextCourse.courseName,
                    "classroom" to nextCourse.classroom,
                    "weekNumber" to weekNumber,
                    "isNextCourseReminder" to true,
                    "currentCourseName" to currentCourse.courseName,
                    "isSameCourseAndClassroom" to isSameCourseAndClassroom
                )
            )
            .addTag("next_course_from_${currentCourse.id}_to_${nextCourse.id}")
            .addTag("next_course_reminder_${currentCourse.id}")
            .addTag("next_course_target_${nextCourse.id}")
            .addTag(BATCH_TAG)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        val operation = WorkManager.getInstance(context).enqueue(workRequest)

        try {
            val result = operation.result.await()
            if (result != null) {
                val reminder = Reminder(
                    courseId = nextCourse.id,
                    minutesBefore = ReminderUseCase.NEXT_COURSE_REMINDER_MINUTES,
                    isEnabled = true,
                    weekNumber = weekNumber,
                    dayOfWeek = nextCourse.dayOfWeek,
                    triggerTime = reminderTimeMillis,
                    workRequestId = workRequest.id.toString()
                )
                reminderRepository.insert(reminder)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "创建下节课提醒失败: ${e.message}", e)
        }
    }
}

class DailyReminderSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        AppLogger.d("DailyReminderSync", "开始每日提醒同步")
        return Result.success()
    }
}
