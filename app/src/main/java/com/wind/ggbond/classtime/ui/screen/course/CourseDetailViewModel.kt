package com.wind.ggbond.classtime.ui.screen.course

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.MainActivity
import com.wind.ggbond.classtime.R
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.Exam
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ReminderRepository
import com.wind.ggbond.classtime.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 课程详情 ViewModel
 */
@HiltViewModel
class CourseDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val courseRepository: CourseRepository,
    private val reminderRepository: ReminderRepository,
    private val scheduleRepository: com.wind.ggbond.classtime.data.repository.ScheduleRepository,
    private val examRepository: com.wind.ggbond.classtime.data.repository.ExamRepository
) : ViewModel() {
    
    companion object {
        private const val TAG = "CourseDetailViewModel"
        private const val CHANNEL_ID = "course_reminder"
        private const val BACKGROUND_TEST_CHANNEL_ID = "background_test_notification"
    }
    
    private val _course = MutableStateFlow<Course?>(null)
    val course: StateFlow<Course?> = _course.asStateFlow()
    
    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()
    
    private val _showAdjustmentDialog = MutableStateFlow(false)
    val showAdjustmentDialog: StateFlow<Boolean> = _showAdjustmentDialog.asStateFlow()
    
    private val _currentSchedule = MutableStateFlow<com.wind.ggbond.classtime.data.local.entity.Schedule?>(null)
    val currentSchedule: StateFlow<com.wind.ggbond.classtime.data.local.entity.Schedule?> = _currentSchedule.asStateFlow()
    
    private val _currentWeekNumber = MutableStateFlow(1)
    val currentWeekNumber: StateFlow<Int> = _currentWeekNumber.asStateFlow()
    
    // 当前课程的考试列表
    private val _exams = MutableStateFlow<List<com.wind.ggbond.classtime.data.local.entity.Exam>>(emptyList())
    val exams: StateFlow<List<com.wind.ggbond.classtime.data.local.entity.Exam>> = _exams.asStateFlow()
    
    // 显示添加考试对话框
    private val _showAddExamDialog = MutableStateFlow(false)
    val showAddExamDialog: StateFlow<Boolean> = _showAddExamDialog.asStateFlow()
    
    fun loadCourse(courseId: Long) {
        viewModelScope.launch {
            courseRepository.getCourseByIdFlow(courseId).collect {
                _course.value = it
                it?.let { course ->
                    Log.d(TAG, "✅ 课程已加载：${course.courseName}, reminderEnabled = ${course.reminderEnabled}, reminderMinutes = ${course.reminderMinutes}")
                }
            }
        }
        
        // 从课表加载学期时间信息和当前周次
        viewModelScope.launch {
            scheduleRepository.getCurrentScheduleFlow().collect { schedule ->
                _currentSchedule.value = schedule
                schedule?.let {
                    _currentWeekNumber.value = DateUtils.calculateWeekNumber(it.startDate)
                }
            }
        }
        
        // 加载该课程的考试列表
        viewModelScope.launch {
            try {
                examRepository.getExamsByCourse(courseId)
                    .catch { e ->
                        android.util.Log.e("CourseDetailViewModel", "Error loading exams for course $courseId", e)
                        emit(emptyList())
                    }
                    .collect { examList ->
                        _exams.value = examList
                    }
            } catch (e: Exception) {
                android.util.Log.e("CourseDetailViewModel", "Error in exam collection", e)
                _exams.value = emptyList()
            }
        }
    }
    
    fun showAddExamDialog() {
        _showAddExamDialog.value = true
    }
    
    fun hideAddExamDialog() {
        _showAddExamDialog.value = false
    }
    
    fun deleteExam(examId: Long) {
        viewModelScope.launch {
            examRepository.deleteExamById(examId)
        }
    }
    
    fun saveExam(exam: Exam) {
        viewModelScope.launch {
            examRepository.insertExam(exam)
        }
    }
    
    fun showDeleteDialog() {
        _showDeleteDialog.value = true
    }
    
    fun hideDeleteDialog() {
        _showDeleteDialog.value = false
    }
    
    fun showAdjustmentDialog() {
        _showAdjustmentDialog.value = true
    }
    
    fun hideAdjustmentDialog() {
        _showAdjustmentDialog.value = false
    }
    
    fun deleteCourse() {
        viewModelScope.launch {
            _course.value?.let { course ->
                // 删除关联的提醒
                reminderRepository.deleteRemindersByCourse(course.id)
                // 删除课程
                courseRepository.deleteCourse(course)
            }
        }
    }
    
    /**
     * 切换课程的通知状态
     */
    fun toggleReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _course.value?.let { course ->
                val updatedCourse = course.copy(
                    reminderEnabled = enabled,
                    updatedAt = System.currentTimeMillis()
                )
                courseRepository.updateCourse(updatedCourse)
                Log.d(TAG, "课程通知状态已更新：${course.courseName}, reminderEnabled = $enabled")
            }
        }
    }
    
    /**
     * 更新提醒提前时间
     */
    fun updateReminderMinutes(minutes: Int) {
        viewModelScope.launch {
            _course.value?.let { course ->
                val updatedCourse = course.copy(
                    reminderMinutes = minutes,
                    updatedAt = System.currentTimeMillis()
                )
                courseRepository.updateCourse(updatedCourse)
                Log.d(TAG, "课程提醒时间已更新：${course.courseName}, reminderMinutes = $minutes")
            }
        }
    }
    
    /**
     * 更新课程信息（全字段保存）
     * @return 是否保存成功
     */
    fun updateCourse(
        courseName: String,
        teacher: String,
        classroom: String,
        note: String,
        dayOfWeek: Int,
        startSection: Int,
        sectionCount: Int,
        weeks: List<Int>,
        color: String,
        credit: Float,
        courseCode: String,
        reminderEnabled: Boolean,
        reminderMinutes: Int
    ): Boolean {
        val currentCourse = _course.value ?: return false
        
        // 验证必填字段
        if (courseName.isBlank()) {
            Log.w(TAG, "⚠️ 课程名称不能为空")
            return false
        }
        // 验证时间范围
        if (dayOfWeek !in 1..7) {
            Log.w(TAG, "⚠️ 星期必须在1-7之间")
            return false
        }
        if (startSection < 1) {
            Log.w(TAG, "⚠️ 开始节次必须大于等于1")
            return false
        }
        if (sectionCount < 1) {
            Log.w(TAG, "⚠️ 持续节数必须大于等于1")
            return false
        }
        if (startSection > 14 || (startSection + sectionCount - 1) > 14) {
            Log.w(TAG, "⚠️ 节次范围超出限制（最大14节）")
            return false
        }
        if (weeks.isEmpty()) {
            Log.w(TAG, "⚠️ 周次不能为空")
            return false
        }
        
        // 冲突检测（异步执行，不阻塞保存）
        viewModelScope.launch {
            val hasConflict = checkTimeConflict(
                courseId = currentCourse.id,
                dayOfWeek = dayOfWeek,
                startSection = startSection,
                sectionCount = sectionCount,
                weeks = weeks
            )
            if (hasConflict) {
                Log.w(TAG, "⚠️ 检测到时间冲突，但仍允许保存")
            }
        }
        
        // 创建更新后的课程对象（全字段）
        val updatedCourse = currentCourse.copy(
            courseName = courseName,
            teacher = teacher,
            classroom = classroom,
            note = note,
            dayOfWeek = dayOfWeek,
            startSection = startSection,
            sectionCount = sectionCount,
            weeks = weeks,
            color = color,
            credit = credit,
            courseCode = courseCode,
            reminderEnabled = reminderEnabled,
            reminderMinutes = reminderMinutes,
            updatedAt = System.currentTimeMillis()
        )
        
        // 保存到数据库
        viewModelScope.launch {
            try {
                courseRepository.updateCourse(updatedCourse)
                Log.d(TAG, "✅ 课程全字段更新成功：${updatedCourse.courseName}")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 课程更新失败", e)
            }
        }
        
        return true
    }
    
    /**
     * 检查时间冲突
     * @return 是否有冲突
     */
    private suspend fun checkTimeConflict(
        courseId: Long,
        dayOfWeek: Int,
        startSection: Int,
        sectionCount: Int,
        weeks: List<Int>
    ): Boolean {
        return try {
            // 获取当前课程信息以获取scheduleId
            val currentCourse = _course.value ?: return false
            
            // 使用CourseRepository的detectConflictWithWeeks方法检测冲突
            val conflicts = courseRepository.detectConflictWithWeeks(
                scheduleId = currentCourse.scheduleId,
                dayOfWeek = dayOfWeek,
                startSection = startSection,
                sectionCount = sectionCount,
                weeks = weeks,
                excludeCourseId = courseId
            )
            
            if (conflicts.isNotEmpty()) {
                Log.w(TAG, "⚠️ 检测到时间冲突，与以下课程冲突：")
                conflicts.forEach { conflictCourse ->
                    Log.w(TAG, "  - ${conflictCourse.courseName} (星期${dayOfWeek}, 第${conflictCourse.startSection}-${conflictCourse.startSection + conflictCourse.sectionCount - 1}节)")
                }
                true
            } else {
                Log.d(TAG, "✅ 未检测到时间冲突")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 冲突检测失败", e)
            false
        }
    }
    
    /**
     * 延迟发送测试通知（后台测试用）
     * 使用 AlarmManager 实现真正的后台定时通知
     */
    fun sendBackgroundTestNotification(course: Course, delaySeconds: Int = 10) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            // 创建用于触发通知的Intent
            val intent = Intent(context, BackgroundTestReceiver::class.java).apply {
                putExtra("courseId", course.id)
                putExtra("courseName", course.courseName)
                putExtra("classroom", course.classroom)
                putExtra("teacher", course.teacher)
                putExtra("dayOfWeek", course.dayOfWeek)
                putExtra("startSection", course.startSection)
                putExtra("sectionCount", course.sectionCount)
            }
            
            // 使用时间戳确保每次都是唯一的PendingIntent
            val requestCode = System.currentTimeMillis().toInt()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            // 计算触发时间
            val triggerTime = System.currentTimeMillis() + (delaySeconds * 1000L)
            
            // 使用精确的闹钟（即使在Doze模式下也会触发）
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
            
            Log.d(TAG, "已使用 AlarmManager 设置后台测试通知，将在 ${delaySeconds} 秒后触发")
            
        } catch (e: Exception) {
            Log.e(TAG, "设置后台测试通知失败", e)
        }
    }
    
    /**
     * 发送测试通知（Debug功能）
     */
    fun sendTestNotification(course: Course) {
        viewModelScope.launch {
            sendNotification(course, isBackgroundTest = false)
        }
    }
    
    /**
     * 实际发送通知的函数
     */
    private fun sendNotification(course: Course, isBackgroundTest: Boolean) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 根据是否是后台测试，选择不同的通知渠道
            val channelId = if (isBackgroundTest) {
                createBackgroundTestChannel(notificationManager)
                BACKGROUND_TEST_CHANNEL_ID
            } else {
                createNotificationChannel(notificationManager)
                CHANNEL_ID
            }
            
            // 构建通知内容
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
            
            // 短文本
            val shortText = if (course.classroom.isNotEmpty()) {
                "📍 地点：${course.classroom}"
            } else {
                "📍 地点：${classroomText}"
            }
            
            // 长文本
            val longText = buildString {
                if (isBackgroundTest) {
                    append("🧪 后台通知测试\n\n")
                }
                append("📍 上课地点：${classroomText}\n")
                if (course.teacher.isNotEmpty()) {
                    append("👤 任课教师：${teacherText}\n")
                }
                append("📅 时间：${dayOfWeekName} ${sectionText}")
            }
            
            // 创建打开应用的Intent
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            
            // 使用时间戳作为 requestCode，确保每次都是新的通知
            val requestCode = System.currentTimeMillis().toInt()
            val pendingIntent = PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            // 标题
            val title = if (isBackgroundTest) {
                "🧪 后台测试 - ${course.courseName}"
            } else {
                "📚 ${course.courseName}"
            }
            
            // 构建通知
            val notificationBuilder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(shortText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(longText))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setOnlyAlertOnce(false)  // 确保每次都会提醒
            
            // 根据是否是后台测试，设置不同的通知样式
            if (isBackgroundTest) {
                // 后台测试：使用 Heads-Up 样式（会自动收回，像QQ/微信那样）
                notificationBuilder
                    .setPriority(NotificationCompat.PRIORITY_HIGH)  // 使用 HIGH 优先级
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)  // 使用 MESSAGE 分类
                    .setTimeoutAfter(5000)  // 5秒后自动收回到通知栏
                Log.d(TAG, "后台测试通知：使用 Heads-Up 样式，5秒后自动收回")
            } else {
                // 普通测试通知
                notificationBuilder
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
            }
            
            val notification = notificationBuilder.build()
            
            // 使用唯一ID发送通知
            val notificationId = (course.id.toString() + System.currentTimeMillis()).hashCode().and(0x7FFFFFFF)
            notificationManager.notify(notificationId, notification)
            
            Log.d(TAG, "测试通知已发送: ${course.courseName}, 后台测试: $isBackgroundTest")
            
        } catch (e: Exception) {
            Log.e(TAG, "发送测试通知失败", e)
        }
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existingChannel == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.reminder_channel_description)
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
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
    
    /**
     * 创建后台测试专用通知渠道（使用更高的重要性级别）
     */
    private fun createBackgroundTestChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 每次都删除旧渠道并重新创建，确保设置生效
            try {
                notificationManager.deleteNotificationChannel(BACKGROUND_TEST_CHANNEL_ID)
            } catch (e: Exception) {
                Log.w(TAG, "删除旧的测试渠道失败: ${e.message}")
            }
            
            val channel = NotificationChannel(
                BACKGROUND_TEST_CHANNEL_ID,
                "后台通知测试（开发专用）",
                NotificationManager.IMPORTANCE_HIGH  // 使用最高重要性
            ).apply {
                description = "用于测试应用在后台时能否正常弹出通知，仅开发调试使用"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)  // 更明显的震动
                enableLights(true)
                lightColor = android.graphics.Color.RED
                setSound(
                    Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)  // 使用ALARM类型，更容易弹出
                        .build()
                )
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(false)  // 不绕过勿扰模式
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "后台测试通知渠道已创建，重要性级别：${channel.importance}")
        }
    }
}

/**
 * 后台测试通知接收器
 * 用于接收 AlarmManager 触发的后台测试通知
 */
class BackgroundTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BackgroundTestReceiver", "收到后台测试通知触发，开始发送通知")
        
        try {
            val courseId = intent.getLongExtra("courseId", 0L)
            val courseName = intent.getStringExtra("courseName") ?: "测试课程"
            val classroom = intent.getStringExtra("classroom") ?: ""
            val teacher = intent.getStringExtra("teacher") ?: ""
            val dayOfWeek = intent.getIntExtra("dayOfWeek", 1)
            val startSection = intent.getIntExtra("startSection", 1)
            val sectionCount = intent.getIntExtra("sectionCount", 2)
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 创建通知渠道
            createBackgroundTestChannel(context, notificationManager)
            
            // 构建通知内容
            val classroomText = classroom.ifEmpty { "教室未设置" }
            val teacherText = teacher.ifEmpty { "教师未设置" }
            
            val dayOfWeekName = when (dayOfWeek) {
                1 -> "周一"
                2 -> "周二"
                3 -> "周三"
                4 -> "周四"
                5 -> "周五"
                6 -> "周六"
                7 -> "周日"
                else -> "未知"
            }
            
            val sectionText = if (sectionCount > 1) {
                "第${startSection}-${startSection + sectionCount - 1}节"
            } else {
                "第${startSection}节"
            }
            
            // 短文本
            val shortText = "地点：${classroomText}"
            
            // 长文本
            val longText = buildString {
                append("后台通知测试成功！\n\n")
                append("上课地点：${classroomText}\n")
                if (teacher.isNotEmpty()) {
                    append("任课教师：${teacherText}\n")
                }
                append("时间：${dayOfWeekName} ${sectionText}\n\n")
                append("如果您看到此通知，说明后台通知功能正常")
            }
            
            // 创建打开应用的Intent
            val appIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            
            val requestCode = System.currentTimeMillis().toInt()
            val pendingIntent = PendingIntent.getActivity(
                context,
                requestCode,
                appIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            // 构建通知 - 使用 Heads-Up 样式（会自动收回）
            val notification = NotificationCompat.Builder(context, "background_test_notification")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("🧪 后台测试 - ${courseName}")
                .setContentText(shortText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(longText))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)  // 使用 HIGH 而不是 MAX
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)  // 使用 MESSAGE 分类，会自动收回
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setOnlyAlertOnce(false)
                .setTimeoutAfter(5000)  // 5秒后自动收回到通知栏
                .build()
            
            // 发送通知
            val notificationId = (courseId.toString() + System.currentTimeMillis()).hashCode().and(0x7FFFFFFF)
            notificationManager.notify(notificationId, notification)
            
            Log.d("BackgroundTestReceiver", "后台测试通知已成功发送")
            
        } catch (e: Exception) {
            Log.e("BackgroundTestReceiver", "发送后台测试通知失败", e)
        }
    }
    
    private fun createBackgroundTestChannel(context: Context, notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 删除旧渠道并重新创建
            try {
                notificationManager.deleteNotificationChannel("background_test_notification")
            } catch (e: Exception) {
                Log.w("BackgroundTestReceiver", "删除旧渠道失败: ${e.message}")
            }
            
            val channel = NotificationChannel(
                "background_test_notification",
                "后台通知测试（开发专用）",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "用于测试应用在后台时能否正常弹出通知"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)
                enableLights(true)
                lightColor = android.graphics.Color.RED
                setSound(
                    Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}



