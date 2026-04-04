package com.wind.ggbond.classtime.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
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
import com.wind.ggbond.classtime.service.ScheduleFetchService
import com.wind.ggbond.classtime.service.contract.IAlarmScheduler
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
    private val scheduleFetchService: ScheduleFetchService,
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
        
        // Worker 标签
        const val WORK_TAG = "schedule_auto_update_work"
        const val UNIQUE_WORK_NAME = "schedule_auto_update"
        
        // 最大课程数量限制（防止 OOM）
        private const val MAX_COURSES = 1000
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "开始执行课表自动更新任务")
        
        try {
            // ✅ 检查是否被取消
            if (!currentCoroutineContext().isActive) return@withContext Result.success()
            
            // 1. 获取当前课表
            val currentSchedule = scheduleRepository.getCurrentSchedule()
            if (currentSchedule == null) {
                Log.w(TAG, "未找到当前课表，跳过更新")
                return@withContext Result.success()
            }
            
            // 2. 获取学校配置
            val schoolId = currentSchedule.schoolName
            if (schoolId.isEmpty()) {
                Log.w(TAG, "课表未关联学校，跳过更新")
                return@withContext Result.success()
            }
            
            if (!currentCoroutineContext().isActive) return@withContext Result.success()
            
            val school = schoolRepository.getSchoolById(schoolId)
            if (school == null) {
                Log.e(TAG, "未找到学校配置: $schoolId")
                return@withContext Result.failure()
            }
            
            // 3. 构建学校配置
            val schoolConfig = SchoolConfig(
                id = school.id,
                name = school.name,
                loginUrl = school.loginUrl,
                scheduleUrl = school.scheduleUrl,
                scheduleMethod = school.scheduleMethod,
                scheduleParams = school.scheduleParams,
                dataFormat = com.wind.ggbond.classtime.data.model.DataFormat.valueOf(
                    school.dataFormat.uppercase()
                ),
                jsonPaths = school.jsonMapping,
                needCsrfToken = school.needCsrfToken,
                csrfTokenName = school.csrfTokenName
            )
            
            // 4. 使用Cookie获取课表数据
            val fetchedCourses: List<com.wind.ggbond.classtime.data.model.ParsedCourse>
            var newCookies: String? = null
            
            // 尝试使用Cookie
            val cookies = secureCookieManager.getCookies(schoolId)
            if (cookies.isNullOrEmpty()) {
                Log.w(TAG, "❌ 未找到Cookie")
                sendNotification(
                    "课表自动更新失败",
                    "登录凭证不存在，请重新登录教务系统导入课表",
                    isError = true
                )
                return@withContext Result.retry()
            }
            
            Log.d(TAG, "✅ 使用保存的Cookie尝试更新课表")
            val cookieResult = try {
                backgroundWebViewFetchService.fetchScheduleWithWebView(
                    schoolConfig,
                    cookies
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "使用Cookie抓取失败: ${e.message}")
                sendNotification(
                    "课表自动更新失败",
                    "登录已过期，请重新登录教务系统导入课表",
                    isError = true
                )
                return@withContext Result.retry()
            }
            
            if (cookieResult.isSuccess && cookieResult.getOrNull()?.isNotEmpty() == true) {
                // Cookie有效，使用抓取的数据
                Log.d(TAG, "✅ Cookie有效，成功抓取课表")
                fetchedCourses = cookieResult.getOrThrow()
            } else {
                // Cookie失效
                Log.w(TAG, "⚠️ Cookie已失效")
                sendNotification(
                    "课表自动更新失败",
                    "登录已过期，请重新登录教务系统导入课表",
                    isError = true
                )
                return@withContext Result.retry()
            }
            
            // 5. 验证抓取的课程数据
            if (fetchedCourses.isEmpty()) {
                Log.w(TAG, "⚠️ 未解析到课程数据")
                Log.w(TAG, "学校: ${school.name}")
                Log.w(TAG, "课表URL: ${school.scheduleUrl}")
                
                sendNotification(
                    "课表检查完成",
                    "⚠️ 未检测到课程数据，请尝试手动重新登录教务系统",
                    isError = true
                )
                return@withContext Result.retry()
            }
            
            // ✅ 检查课程数量限制
            if (fetchedCourses.count() > MAX_COURSES) {
                Log.e(TAG, "❌ 课程数量超过限制: ${fetchedCourses.count()} > $MAX_COURSES")
                sendNotification(
                    "课表自动更新失败",
                    "课程数量异常，请联系开发者",
                    isError = true
                )
                return@withContext Result.failure()
            }
            
            Log.d(TAG, "✅ 成功抓取 ${fetchedCourses.count()} 门课程")
            
            if (!currentCoroutineContext().isActive) return@withContext Result.success()
            
            if (!currentCoroutineContext().isActive) return@withContext Result.success()
            
            // 6. 获取本地课表
            val localCourses = courseRepository.getAllCoursesBySchedule(currentSchedule.id)
                .firstOrNull() ?: emptyList()
            
            Log.d(TAG, "本地课程: ${localCourses.count()}, 远程课程: ${fetchedCourses.count()}")
            
            if (!currentCoroutineContext().isActive) return@withContext Result.success()
            
            // 7. 对比并更新课程
            val updateResult = updateCourses(
                localCourses = localCourses,
                remoteCourses = fetchedCourses,
                scheduleId = currentSchedule.id
            )
            
            if (!currentCoroutineContext().isActive) return@withContext Result.success()
            
            // 8. 发送更新通知
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
                // 当手动触发更新时，即使无变化也发送通知
                val isManualTrigger = inputData.getBoolean("isManualTrigger", false)
                if (isManualTrigger) {
                    sendNotification(
                        "课表检查完成",
                        "课表已是最新状态，无需更新",
                        isError = false,
                        hasAdjustments = false
                    )
                } else {
                    Log.d(TAG, "课表无变化，不发送通知")
                }
            }
            
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "课表自动更新完成: $updateResult, 耗时: ${elapsed}ms")
            return@withContext Result.success()
            
        } catch (e: CancellationException) {
            Log.w(TAG, "Worker 被取消")
            throw e  // ✅ 必须重新抛出 CancellationException
        } catch (e: Exception) {
            Log.e(TAG, "课表自动更新失败", e)
            sendNotification(
                "课表自动更新失败",
                "发生错误: ${e.message ?: "未知错误"}",
                isError = true
            )
            // ✅ 未知错误也尝试重试
            return@withContext Result.retry()
        }
    }
    
    /**
     * 更新课程数据
     * 
     * 策略：
     * 1. 新增：远程有、本地无 -> 添加
     * 2. 更新：远程有、本地有但信息不同 -> 更新，如果时间变化则创建调课记录
     * 3. 删除：本地有、远程无 -> 删除
     * 
     * ✅ 优化：批量操作以提高性能
     */
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
        
        // ✅ 批量操作的列表
        val coursesToAdd = mutableListOf<Course>()
        val coursesToUpdate = mutableListOf<Course>()
        val coursesToDelete = mutableListOf<Course>()
        val adjustmentsToAdd = mutableListOf<CourseAdjustment>()
        
        // 构建本地课程映射（用于快速查找）
        // Key: 课程名称_教师（不包含时间，因为时间可能变化）
        val localCourseMap = mutableMapOf<String, Course>()
        for (course in localCourses) {
            val key = "${course.courseName}_${course.teacher}"
            localCourseMap[key] = course
        }
        
        // 处理远程课程
        for (remoteCourse in remoteCourses) {
            if (!currentCoroutineContext().isActive) break
            
            val key = "${remoteCourse.courseName}_${remoteCourse.teacher}"
            val localCourse = localCourseMap[key]
            
            if (localCourse == null) {
                // 新增课程
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
                    reminderEnabled = false  // ✅ 自动更新的新课程默认关闭提醒，等待全局设置控制
                )
                coursesToAdd.add(newCourse)
                addedCount++
                Log.d(TAG, "准备新增课程: ${remoteCourse.courseName}")
            } else {
                // 检查是否需要更新
                val weeks = WeekParser.parseWeekExpression(remoteCourse.weekExpression)
                
                // 检查时间是否变化（星期或节次）
                val timeChanged = localCourse.dayOfWeek != remoteCourse.dayOfWeek ||
                        localCourse.startSection != remoteCourse.startSection ||
                        localCourse.sectionCount != remoteCourse.sectionCount
                
                // 检查其他信息是否变化
                val infoChanged = localCourse.classroom != remoteCourse.classroom ||
                        localCourse.weeks != weeks ||
                        localCourse.weekExpression != remoteCourse.weekExpression
                
                if (timeChanged || infoChanged) {
                    // 如果时间变化，创建调课记录
                    if (timeChanged) {
                        // 找出时间变化影响的周次（本地和远程都有的周次）
                        val affectedWeeks = localCourse.weeks.intersect(weeks.toSet())
                        
                        if (affectedWeeks.isNotEmpty()) {
                            // 为每个受影响的周次创建调课记录
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
                            Log.d(TAG, "检测到调课: ${localCourse.courseName}, 影响周次: $affectedWeeks")
                        }
                    }
                    
                    // 更新课程信息
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
                    Log.d(TAG, "准备更新课程: ${remoteCourse.courseName}")
                }
                
                // 从映射中移除（剩下的就是需要删除的）
                localCourseMap.remove(key)
            }
        }
        
        if (!currentCoroutineContext().isActive) {
            return UpdateResult(0, 0, 0, 0, emptyList())
        }
        
        // 删除本地存在但远程不存在的课程
        for ((_, course) in localCourseMap) {
            coursesToDelete.add(course)
            deletedCount++
            Log.d(TAG, "准备删除课程: ${course.courseName}")
        }
        
        // ✅ 使用数据库事务包裹所有操作，确保原子性
        try {
            courseDatabase.withTransaction {
                // 检查是否被取消
                if (!currentCoroutineContext().isActive) {
                    throw CancellationException("任务被取消")
                }
                
                // 新增课程
                if (coursesToAdd.isNotEmpty()) {
                    Log.d(TAG, "批量新增 ${coursesToAdd.size} 门课程")
                    coursesToAdd.forEach { courseRepository.insertCourse(it) }
                }
                
                if (!currentCoroutineContext().isActive) {
                    throw CancellationException("任务被取消")
                }
                
                // 更新课程
                if (coursesToUpdate.isNotEmpty()) {
                    Log.d(TAG, "批量更新 ${coursesToUpdate.size} 门课程")
                    coursesToUpdate.forEach { courseRepository.updateCourse(it) }
                }
                
                if (!currentCoroutineContext().isActive) {
                    throw CancellationException("任务被取消")
                }
                
                // 添加调课记录
                if (adjustmentsToAdd.isNotEmpty()) {
                    Log.d(TAG, "批量添加 ${adjustmentsToAdd.size} 条调课记录")
                    adjustmentsToAdd.forEach { courseAdjustmentRepository.saveAdjustment(it) }
                }
                
                if (!currentCoroutineContext().isActive) {
                    throw CancellationException("任务被取消")
                }
                
                // 删除课程及其调课记录
                if (coursesToDelete.isNotEmpty()) {
                    Log.d(TAG, "批量删除 ${coursesToDelete.size} 门课程")
                    for (course in coursesToDelete) {
                        courseAdjustmentRepository.deleteAdjustmentsByCourse(course.id)
                        courseRepository.deleteCourse(course)
                    }
                }
                
                Log.d(TAG, "✅ 数据库事务提交成功")
            }
        } catch (e: CancellationException) {
            Log.w(TAG, "数据库操作被取消，事务已自动回滚")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ 批量数据库操作失败，事务已自动回滚", e)
            throw e  // ✅ 重新抛出，让外层处理
        }
        
        // ✅ 同步提醒状态：如果全局提醒开启，为新增的课程开启提醒
        if (coursesToAdd.isNotEmpty()) {
            try {
                val settingsDataStore = com.wind.ggbond.classtime.data.datastore.DataStoreManager.getSettingsDataStore(applicationContext)
                val reminderEnabled = settingsDataStore.data.first()[com.wind.ggbond.classtime.data.datastore.DataStoreManager.SettingsKeys.REMINDER_ENABLED_KEY] ?: false
                
                if (reminderEnabled) {
                    val defaultMinutes = settingsDataStore.data.first()[com.wind.ggbond.classtime.data.datastore.DataStoreManager.SettingsKeys.DEFAULT_REMINDER_MINUTES_KEY] ?: 10
                    
                    // 为新增的课程开启提醒
                    val updatedCount = courseRepository.enableAllCoursesReminder(scheduleId, defaultMinutes)
                    
                    Log.d(TAG, "✅ 自动更新后，已为 $updatedCount 门课程同步提醒状态")
                    
                    // 重新创建所有提醒任务
                    reminderScheduler.scheduleAllCourseReminders(scheduleId)
                } else {
                    Log.d(TAG, "全局提醒未开启，跳过提醒同步")
                }
            } catch (e: Exception) {
                Log.e(TAG, "同步提醒状态失败：${e.message}", e)
                // 提醒同步失败不影响更新结果
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
    
    /**
     * 构建更新消息
     */
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
            // 限制显示的调课详情数量
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
    
    /**
     * 发送更新通知
     */
    private fun sendNotification(title: String, content: String, isError: Boolean, hasAdjustments: Boolean = false, result: UpdateResult? = null) {
        try {
            createNotificationChannel()
            
            // ✅ 检查通知权限
            val notificationManager = androidx.core.app.NotificationManagerCompat.from(applicationContext)
            if (!notificationManager.areNotificationsEnabled()) {
                Log.w(TAG, "通知权限未授予，无法显示通知")
                return
            }
            
            // ✅ 检查通知渠道是否被禁用
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = notificationManager.getNotificationChannel(CHANNEL_ID)
                if (channel?.importance == NotificationManager.IMPORTANCE_NONE) {
                    Log.w(TAG, "通知渠道已被用户禁用")
                    return
                }
            }
            
            // 创建打开调课记录的 Intent（如果有调课）
            val intent = if (hasAdjustments) {
                android.content.Intent(applicationContext, com.wind.ggbond.classtime.MainActivity::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("openAdjustmentManagement", true)  // 标记打开调课记录
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
            
            // ✅ 每次通知都使用当前时间戳，确保被视为新通知
            val currentTime = System.currentTimeMillis()
            
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("时课 $title")
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(result?.let { buildDetailedContent(content, it) } ?: content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)  // 统一使用高优先级
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL)  // ✅ 添加默认声音和震动
                .setShowWhen(true)  // ✅ 显示时间
                .setWhen(currentTime)  // ✅ 使用当前时间戳
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // ✅ 锁屏可见
                .setOnlyAlertOnce(false)  // ✅ 确保每次通知都会提醒
                .setCategory(if (hasAdjustments) NotificationCompat.CATEGORY_EVENT else NotificationCompat.CATEGORY_STATUS)
                .build()
            
            val systemNotificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) 
                as NotificationManager
            systemNotificationManager.notify(NOTIFICATION_ID, notification)
            
            Log.d(TAG, "通知已发送: $title (优先级: HIGH, 时间戳: $currentTime)")
        } catch (e: SecurityException) {
            Log.e(TAG, "显示通知失败：${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "显示通知时发生未知错误：${e.message}", e)
        }
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) 
                as NotificationManager
            val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
            
            if (existingChannel == null) {
                // ✅ 创建新渠道 - 使用高优先级确保通知能够显示
                val name = "课表自动更新"
                val descriptionText = "课表自动更新通知"
                val importance = NotificationManager.IMPORTANCE_HIGH  // ✅ 改为高优先级
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    // ✅ 添加震动
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 250, 250)
                    // ✅ 添加声音
                    setSound(
                        android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                        android.media.AudioAttributes.Builder()
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                            .build()
                    )
                    // ✅ 添加指示灯
                    enableLights(true)
                    lightColor = android.graphics.Color.BLUE
                    // ✅ 锁定屏幕可见性
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "通知渠道已创建（高优先级）")
            } else if (existingChannel.importance == NotificationManager.IMPORTANCE_NONE) {
                // ✅ 渠道被禁用，记录日志
                Log.w(TAG, "通知渠道已被用户禁用")
            }
        }
    }
    
    /**
     * 构建详细的通知内容
     */
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
    
    /**
     * 更新结果
     */
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
