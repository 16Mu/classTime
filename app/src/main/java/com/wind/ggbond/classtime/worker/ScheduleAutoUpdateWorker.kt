package com.wind.ggbond.classtime.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wind.ggbond.classtime.R
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.CourseAdjustment
import com.wind.ggbond.classtime.data.model.SchoolConfig
import com.wind.ggbond.classtime.data.repository.CourseAdjustmentRepository
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.data.repository.SchoolRepository
import com.wind.ggbond.classtime.service.BackgroundWebViewFetchService
import com.wind.ggbond.classtime.service.UnifiedScheduleFetchService
import com.wind.ggbond.classtime.service.contract.IAlarmScheduler
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.util.SecureCookieManager
import com.wind.ggbond.classtime.util.WeekParser
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

@HiltWorker
class ScheduleAutoUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val scheduleFetchService: UnifiedScheduleFetchService,
    private val backgroundWebViewFetchService: BackgroundWebViewFetchService,
    private val courseRepository: CourseRepository,
    private val scheduleRepository: ScheduleRepository,
    private val schoolRepository: SchoolRepository,
    private val courseAdjustmentRepository: CourseAdjustmentRepository,
    private val reminderScheduler: IAlarmScheduler,
    private val secureCookieManager: SecureCookieManager,
    private val courseDatabase: com.wind.ggbond.classtime.data.local.database.CourseDatabase
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "ScheduleAutoUpdateWorker"
        const val CHANNEL_ID = "schedule_auto_update"
        const val NOTIFICATION_ID = 2001
        const val WORK_TAG = "schedule_auto_update_work"
        const val UNIQUE_WORK_NAME = "schedule_auto_update"
        private const val MAX_COURSES = 1000
    }

    override suspend fun doWork(): Result {
        val startTime = System.currentTimeMillis()
        AppLogger.d(TAG, "开始执行课表自动更新任务")

        try {
            val currentSchedule = scheduleRepository.getCurrentSchedule()
            if (currentSchedule == null) {
                AppLogger.w(TAG, "未找到当前课表，跳过更新")
                return Result.success()
            }

            val schoolName = currentSchedule.schoolName
            if (schoolName.isEmpty()) {
                AppLogger.w(TAG, "课表未关联学校，跳过更新")
                return Result.success()
            }

            val school = schoolRepository.getSchoolById(schoolName)
            if (school == null) {
                AppLogger.e(TAG, "未找到学校配置: $schoolName")
                return Result.failure()
            }

            val dataFormat = try {
                com.wind.ggbond.classtime.data.model.DataFormat.valueOf(school.dataFormat.uppercase())
            } catch (_: Exception) {
                com.wind.ggbond.classtime.data.model.DataFormat.HTML
            }
            val schoolConfig = SchoolConfig(
                id = school.id,
                name = school.name,
                loginUrl = school.loginUrl,
                scheduleUrl = school.scheduleUrl,
                scheduleMethod = school.scheduleMethod,
                scheduleParams = school.scheduleParams,
                dataFormat = dataFormat,
                jsonPaths = school.jsonMapping,
                needCsrfToken = school.needCsrfToken,
                csrfTokenName = school.csrfTokenName
            )

            val fetchedCourses: List<com.wind.ggbond.classtime.data.model.ParsedCourse>

            val cookies = secureCookieManager.getCookies(schoolName)
            if (cookies.isNullOrEmpty()) {
                AppLogger.w(TAG, "未找到Cookie")
                sendNotification("课表自动更新失败", "登录凭证不存在，请重新登录教务系统导入课表", isError = true)
                return Result.failure()
            }

            AppLogger.d(TAG, "使用保存的Cookie尝试更新课表")
            val cookieResult = try {
                backgroundWebViewFetchService.fetchScheduleWithWebView(schoolConfig, cookies)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "使用Cookie抓取失败: ${e.message}")
                sendNotification("课表自动更新失败", "登录已过期，请重新登录教务系统导入课表", isError = true)
                return Result.failure()
            }

            if (cookieResult.isSuccess && cookieResult.getOrNull()?.isNotEmpty() == true) {
                AppLogger.d(TAG, "Cookie有效，成功抓取课表")
                fetchedCourses = cookieResult.getOrThrow()
            } else {
                AppLogger.w(TAG, "Cookie已失效")
                sendNotification("课表自动更新失败", "登录已过期，请重新登录教务系统导入课表", isError = true)
                return Result.failure()
            }

            if (fetchedCourses.isEmpty()) {
                AppLogger.w(TAG, "未解析到课程数据")
                AppLogger.w(TAG, "学校: ${school.name}")
                AppLogger.w(TAG, "课表URL: ${school.scheduleUrl}")
                sendNotification("课表检查完成", "未检测到课程数据，请尝试手动重新登录教务系统", isError = true)
                return Result.retry()
            }

            if (fetchedCourses.count() > MAX_COURSES) {
                AppLogger.e(TAG, "课程数量超过限制: ${fetchedCourses.count()} > $MAX_COURSES")
                sendNotification("课表自动更新失败", "课程数量异常，请联系开发者", isError = true)
                return Result.failure()
            }

            AppLogger.d(TAG, "成功抓取 ${fetchedCourses.count()} 门课程")

            val localCourses = courseRepository.getAllCoursesBySchedule(currentSchedule.id)
                .firstOrNull() ?: emptyList()

            AppLogger.d(TAG, "本地课程: ${localCourses.count()}, 远程课程: ${fetchedCourses.count()}")

            val updateResult = updateCourses(
                localCourses = localCourses,
                remoteCourses = fetchedCourses,
                scheduleId = currentSchedule.id
            )

            if (updateResult.hasChanges) {
                val message = buildUpdateMessage(updateResult)
                sendNotification(
                    if (updateResult.adjustmentCount > 0) "检测到调课信息" else "课表已更新",
                    message,
                    isError = false,
                    hasAdjustments = updateResult.adjustmentCount > 0,
                    result = updateResult
                )
            } else {
                val isManualTrigger = inputData.getBoolean("isManualTrigger", false)
                if (isManualTrigger) {
                    sendNotification("课表检查完成", "课表已是最新状态，无需更新", isError = false, hasAdjustments = false)
                } else {
                    AppLogger.d(TAG, "课表无变化，不发送通知")
                }
            }

            val elapsed = System.currentTimeMillis() - startTime
            AppLogger.d(TAG, "课表自动更新完成: $updateResult, 耗时: ${elapsed}ms")
            return Result.success()

        } catch (e: CancellationException) {
            AppLogger.w(TAG, "Worker 被取消")
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "课表自动更新失败", e)
            sendNotification("课表自动更新失败", "发生错误: ${e.message ?: "未知错误"}", isError = true)
            return Result.retry()
        }
    }

    private suspend fun updateCourses(
        localCourses: List<Course>,
        remoteCourses: List<com.wind.ggbond.classtime.data.model.ParsedCourse>,
        scheduleId: Long
    ): UpdateResult {
        var addedCount = 0
        var updatedCount = 0
        var deletedCount = 0
        var adjustmentCount = 0
        val adjustmentDetails = mutableListOf<String>()

        val coursesToAdd = mutableListOf<Course>()
        val coursesToUpdate = mutableListOf<Course>()
        val coursesToDelete = mutableListOf<Course>()
        val adjustmentsToAdd = mutableListOf<CourseAdjustment>()

        val localCourseMap = mutableMapOf<String, Course>()
        for (course in localCourses) {
            val key = "${course.courseName}_${course.teacher}"
            localCourseMap[key] = course
        }

        for (remoteCourse in remoteCourses) {
            if (!currentCoroutineContext().isActive) break

            val key = "${remoteCourse.courseName}_${remoteCourse.teacher}"
            val localCourse = localCourseMap[key]

            if (localCourse == null) {
                val weeks = WeekParser.parseWeekExpression(remoteCourse.weekExpression)
                val newCourse = Course(
                    courseName = remoteCourse.courseName,
                    teacher = remoteCourse.teacher,
                    classroom = remoteCourse.classroom,
                    dayOfWeek = remoteCourse.dayOfWeek,
                    startSection = remoteCourse.startSection,
                    sectionCount = remoteCourse.sectionCount,
                    weeks = weeks,
                    weekExpression = remoteCourse.weekExpression,
                    scheduleId = scheduleId,
                    reminderEnabled = false
                )
                coursesToAdd.add(newCourse)
                addedCount++
                AppLogger.d(TAG, "准备新增课程: ${remoteCourse.courseName}")
            } else {
                val weeks = WeekParser.parseWeekExpression(remoteCourse.weekExpression)

                val timeChanged = localCourse.dayOfWeek != remoteCourse.dayOfWeek ||
                        localCourse.startSection != remoteCourse.startSection ||
                        localCourse.sectionCount != remoteCourse.sectionCount

                val infoChanged = localCourse.classroom != remoteCourse.classroom ||
                        localCourse.weeks != weeks ||
                        localCourse.weekExpression != remoteCourse.weekExpression

                if (timeChanged || infoChanged) {
                    if (timeChanged) {
                        val affectedWeeks = localCourse.weeks.intersect(weeks.toSet())

                        if (affectedWeeks.isNotEmpty()) {
                            for (week in affectedWeeks) {
                                val adjustment = CourseAdjustment(
                                    originalCourseId = localCourse.id,
                                    scheduleId = scheduleId,
                                    originalWeekNumber = week,
                                    originalDayOfWeek = localCourse.dayOfWeek,
                                    originalStartSection = localCourse.startSection,
                                    originalSectionCount = localCourse.sectionCount,
                                    newWeekNumber = week,
                                    newDayOfWeek = remoteCourse.dayOfWeek,
                                    newStartSection = remoteCourse.startSection,
                                    newSectionCount = remoteCourse.sectionCount,
                                    reason = "教务系统自动更新"
                                )
                                adjustmentsToAdd.add(adjustment)
                            }
                            adjustmentCount++
                            adjustmentDetails.add("${localCourse.courseName}(${affectedWeeks.size}周)")
                            AppLogger.d(TAG, "检测到调课: ${localCourse.courseName}, 影响周次: $affectedWeeks")
                        }
                    }

                    val updatedCourse = localCourse.copy(
                        classroom = remoteCourse.classroom,
                        dayOfWeek = remoteCourse.dayOfWeek,
                        startSection = remoteCourse.startSection,
                        sectionCount = remoteCourse.sectionCount,
                        weeks = weeks,
                        weekExpression = remoteCourse.weekExpression
                    )
                    coursesToUpdate.add(updatedCourse)
                    updatedCount++
                    AppLogger.d(TAG, "准备更新课程: ${remoteCourse.courseName}")
                }

                localCourseMap.remove(key)
            }
        }

        if (!currentCoroutineContext().isActive) {
            return UpdateResult(0, 0, 0, 0, emptyList())
        }

        for ((_, course) in localCourseMap) {
            coursesToDelete.add(course)
            deletedCount++
            AppLogger.d(TAG, "准备删除课程: ${course.courseName}")
        }

        try {
            courseDatabase.withTransaction {
                if (!currentCoroutineContext().isActive) {
                    throw CancellationException("任务被取消")
                }

                if (coursesToAdd.isNotEmpty()) {
                    AppLogger.d(TAG, "批量新增 ${coursesToAdd.size} 门课程")
                    courseRepository.insertCourses(coursesToAdd)
                }

                if (!currentCoroutineContext().isActive) {
                    throw CancellationException("任务被取消")
                }

                if (coursesToUpdate.isNotEmpty()) {
                    AppLogger.d(TAG, "批量更新 ${coursesToUpdate.size} 门课程")
                    courseDatabase.courseDao().updateCourses(coursesToUpdate)
                }

                if (!currentCoroutineContext().isActive) {
                    throw CancellationException("任务被取消")
                }

                if (adjustmentsToAdd.isNotEmpty()) {
                    AppLogger.d(TAG, "批量添加 ${adjustmentsToAdd.size} 条调课记录")
                    adjustmentsToAdd.forEach { courseAdjustmentRepository.saveAdjustment(it) }
                }

                if (!currentCoroutineContext().isActive) {
                    throw CancellationException("任务被取消")
                }

                if (coursesToDelete.isNotEmpty()) {
                    AppLogger.d(TAG, "批量删除 ${coursesToDelete.size} 门课程")
                    val deleteIds = coursesToDelete.map { it.id }
                    courseDatabase.courseDao().deleteCoursesByIds(deleteIds)
                }

                AppLogger.d(TAG, "数据库事务提交成功")
            }
        } catch (e: CancellationException) {
            AppLogger.w(TAG, "数据库操作被取消，事务已自动回滚")
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "批量数据库操作失败，事务已自动回滚", e)
            throw e
        }

        if (coursesToAdd.isNotEmpty()) {
            try {
                val settingsDataStore = com.wind.ggbond.classtime.data.datastore.DataStoreManager.getSettingsDataStore(applicationContext)
                val reminderEnabled = settingsDataStore.data.first()[com.wind.ggbond.classtime.data.datastore.DataStoreManager.SettingsKeys.REMINDER_ENABLED_KEY] ?: false

                if (reminderEnabled) {
                    val defaultMinutes = settingsDataStore.data.first()[com.wind.ggbond.classtime.data.datastore.DataStoreManager.SettingsKeys.DEFAULT_REMINDER_MINUTES_KEY] ?: 10
                    val updatedCount = courseRepository.enableAllCoursesReminder(scheduleId, defaultMinutes)
                    AppLogger.d(TAG, "自动更新后，已为 $updatedCount 门课程同步提醒状态")
                    reminderScheduler.scheduleAllCourseReminders(scheduleId)
                } else {
                    AppLogger.d(TAG, "全局提醒未开启，跳过提醒同步")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "同步提醒状态失败：${e.message}", e)
            }
        }

        return UpdateResult(
            addedCount = addedCount,
            updatedCount = updatedCount,
            deletedCount = deletedCount,
            adjustmentCount = adjustmentCount,
            adjustmentDetails = adjustmentDetails
        )
    }

    private fun buildUpdateMessage(result: UpdateResult): String {
        val parts = mutableListOf<String>()

        if (result.addedCount > 0) {
            parts.add("新增 ${result.addedCount} 门课程")
        }

        if (result.updatedCount > 0) {
            parts.add("更新 ${result.updatedCount} 门课程信息")
        }

        if (result.deletedCount > 0) {
            parts.add("删除 ${result.deletedCount} 门课程")
        }

        if (result.adjustmentCount > 0) {
            parts.add("检测到 ${result.adjustmentCount} 门课程调课")
            if (result.adjustmentDetails.isNotEmpty()) {
                val displayDetails = result.adjustmentDetails.take(5)
                parts.add("调课课程: ${displayDetails.joinToString("、")}")
                if (result.adjustmentDetails.size > 5) {
                    parts.add("...等 ${result.adjustmentDetails.size - 5} 门课程")
                }
            }
        }

        return parts.joinToString("\n")
    }

    private fun sendNotification(title: String, content: String, isError: Boolean, hasAdjustments: Boolean = false, result: UpdateResult? = null) {
        try {
            createNotificationChannel()

            val notificationManager = androidx.core.app.NotificationManagerCompat.from(applicationContext)
            if (!notificationManager.areNotificationsEnabled()) {
                AppLogger.w(TAG, "通知权限未授予，无法显示通知")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = notificationManager.getNotificationChannel(CHANNEL_ID)
                if (channel?.importance == NotificationManager.IMPORTANCE_NONE) {
                    AppLogger.w(TAG, "通知渠道已被用户禁用")
                    return
                }
            }

            val intent = if (hasAdjustments) {
                android.content.Intent(applicationContext, com.wind.ggbond.classtime.MainActivity::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("openAdjustmentManagement", true)
                }
            } else {
                android.content.Intent(applicationContext, com.wind.ggbond.classtime.MainActivity::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            }

            val pendingIntent = android.app.PendingIntent.getActivity(
                applicationContext,
                NOTIFICATION_ID,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val currentTime = System.currentTimeMillis()

            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("时课 $title")
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(result?.let { buildDetailedContent(content, it) } ?: content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setShowWhen(true)
                .setWhen(currentTime)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(false)
                .setCategory(if (hasAdjustments) NotificationCompat.CATEGORY_EVENT else NotificationCompat.CATEGORY_STATUS)
                .build()

            val systemNotificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
            systemNotificationManager.notify(NOTIFICATION_ID, notification)

            AppLogger.d(TAG, "通知已发送: $title (优先级: HIGH, 时间戳: $currentTime)")
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "显示通知失败：${e.message}", e)
        } catch (e: Exception) {
            AppLogger.e(TAG, "显示通知时发生未知错误：${e.message}", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
            val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)

            if (existingChannel == null) {
                val name = applicationContext.getString(com.wind.ggbond.classtime.R.string.schedule_update_channel_name)
                val descriptionText = applicationContext.getString(com.wind.ggbond.classtime.R.string.schedule_update_channel_description)
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 250, 250)
                    setSound(
                        android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                        android.media.AudioAttributes.Builder()
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                            .build()
                    )
                    enableLights(true)
                    lightColor = android.graphics.Color.BLUE
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
                AppLogger.d(TAG, "通知渠道已创建（高优先级）")
            } else if (existingChannel.importance == NotificationManager.IMPORTANCE_NONE) {
                AppLogger.w(TAG, "通知渠道已被用户禁用")
            }
        }
    }

    private fun buildDetailedContent(content: String, result: UpdateResult): String {
        val sb = StringBuilder()
        sb.append(content)
        sb.append("\n\n详细信息：\n")

        if (result.addedCount > 0) {
            sb.append("• 新增课程：${result.addedCount} 门\n")
        }
        if (result.updatedCount > 0) {
            sb.append("• 更新课程：${result.updatedCount} 门\n")
        }
        if (result.deletedCount > 0) {
            sb.append("• 删除课程：${result.deletedCount} 门\n")
        }
        if (result.adjustmentCount > 0) {
            sb.append("• 调课课程：${result.adjustmentCount} 门\n")
            if (result.adjustmentDetails.isNotEmpty()) {
                val displayDetails = result.adjustmentDetails.take(3)
                displayDetails.forEach { detail ->
                    sb.append("  - $detail\n")
                }
                if (result.adjustmentDetails.size > 3) {
                    sb.append("  - ...等 ${result.adjustmentDetails.size - 3} 门\n")
                }
            }
        }

        sb.append("\n更新时间：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
        sb.append("\n\n点击查看详情")

        return sb.toString()
    }

    data class UpdateResult(
        val addedCount: Int,
        val updatedCount: Int,
        val deletedCount: Int,
        val adjustmentCount: Int = 0,
        val adjustmentDetails: List<String> = emptyList()
    ) {
        val hasChanges: Boolean
            get() = addedCount > 0 || updatedCount > 0 || deletedCount > 0 || adjustmentCount > 0
    }
}
