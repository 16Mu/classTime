package com.wind.ggbond.classtime.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import com.wind.ggbond.classtime.data.local.database.CourseDatabase
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.util.extractor.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlin.coroutines.resume

/**
 * 自动更新管理器
 * 
 * 功能：
 * 1. 在App启动时检查是否需要更新
 * 2. 根据最小间隔判断
 * 3. 后台静默更新
 * 4. 记录统计和日志
 */
class AutoUpdateManager(
    private val context: Context,
    private val database: CourseDatabase,
    private val extractorFactory: com.wind.ggbond.classtime.util.extractor.SchoolExtractorFactory? = null
) {
    
    companion object {
        private const val TAG = "AutoUpdateManager"
        private const val PREFS_NAME = "auto_update_prefs"
        
        // 可选的更新间隔（小时）
        val INTERVAL_OPTIONS = listOf(6, 12, 24, 48, 72)
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cookieManager = SecureCookieManager(context)
    private val gson = Gson()
    
    /**
     * 获取当前配置
     */
    fun getConfig(): AutoUpdateConfig {
        return AutoUpdateConfig(
            enabled = prefs.getBoolean("enabled", false),
            minIntervalHours = prefs.getInt("min_interval_hours", 6),
            lastUpdateTime = prefs.getLong("last_update_time", 0L),
            totalAttempts = prefs.getInt("total_attempts", 0),
            successCount = prefs.getInt("success_count", 0),
            failureCount = prefs.getInt("failure_count", 0),
            skipCount = prefs.getInt("skip_count", 0),
            totalChangesDetected = prefs.getInt("total_changes_detected", 0)
        )
    }
    
    /**
     * 更新配置
     */
    fun updateConfig(config: AutoUpdateConfig) {
        prefs.edit()
            .putBoolean("enabled", config.enabled)
            .putInt("min_interval_hours", config.minIntervalHours)
            .putLong("last_update_time", config.lastUpdateTime)
            .putInt("total_attempts", config.totalAttempts)
            .putInt("success_count", config.successCount)
            .putInt("failure_count", config.failureCount)
            .putInt("skip_count", config.skipCount)
            .putInt("total_changes_detected", config.totalChangesDetected)
            .apply()
    }
    
    /**
     * ⭐ 核心方法：在APP启动时调用
     * 
     * 检查是否需要更新，如果需要则后台执行
     */
    suspend fun checkAndUpdateIfNeeded(schoolId: String): Boolean {
        val config = getConfig()
        
        // 1. 检查是否启用
        if (!config.enabled) {
            Log.d(TAG, "自动更新未启用")
            return false
        }
        
        // 2. 检查时间间隔
        val currentTime = System.currentTimeMillis()
        val timeSinceLastUpdate = currentTime - config.lastUpdateTime
        val minIntervalMs = config.minIntervalHours * 60 * 60 * 1000L
        
        if (timeSinceLastUpdate < minIntervalMs) {
            val minutesPassed = timeSinceLastUpdate / 1000 / 60
            Log.d(TAG, "距离上次更新时间不足，跳过更新")
            Log.d(TAG, "已过: ${minutesPassed}分钟 / 需要: ${config.minIntervalHours}小时")
            
            // 记录跳过
            incrementSkipCount()
            addUpdateLog(
                UpdateStatus.SKIPPED, 
                "距离上次更新不足${config.minIntervalHours}小时"
            )
            
            return false
        }
        
        // 3. 执行后台更新
        Log.d(TAG, "✅ 满足更新条件，开始后台更新...")
        return performBackgroundUpdate(schoolId)
    }
    
    /**
     * 执行后台更新（完全静默）
     */
    private suspend fun performBackgroundUpdate(schoolId: String): Boolean = withContext(Dispatchers.Main) {
        val startTime = System.currentTimeMillis()
        
        try {
            Log.d(TAG, "🔄 开始后台更新课表...")
            
            // 1. 获取学校信息
            val school = database.schoolDao().getSchoolById(schoolId) ?: run {
                Log.e(TAG, "学校信息不存在")
                recordFailure("学校信息不存在", System.currentTimeMillis() - startTime)
                return@withContext false
            }
            
            val domain = extractDomain(school.loginUrl)
            
            // 2. 检查Cookie
            val savedCookie = cookieManager.getCookies(domain)
            if (savedCookie == null) {
                Log.w(TAG, "Cookie不存在，需要重新登录")
                recordFailure("Cookie已过期，请重新导入课表", System.currentTimeMillis() - startTime)
                return@withContext false
            }
            
            // 3. 创建完全隐藏的WebView
            val hiddenWebView = WebView(context).apply {
                // 设置1x1像素，完全不可见
                layoutParams = ViewGroup.LayoutParams(1, 1)
                visibility = View.GONE
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                }
                
                // 恢复Cookie
                CookieManager.getInstance().setCookie(domain, savedCookie)
            }
            
            // 4. 等待提取完成
            val courses = suspendCancellableCoroutine<List<ParsedCourse>> { continuation ->
                hiddenWebView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        
                        Log.d(TAG, "页面加载完成，开始提取数据...")
                        
                        // 使用注入的提取器工厂（如果可用）
                        val factory = extractorFactory
                        if (factory == null) {
                            Log.e(TAG, "提取器工厂未注入，无法执行自动更新")
                            view?.destroy()
                            if (continuation.isActive) {
                                continuation.resume(emptyList())
                            }
                            return
                        }
                        
                        val extractor = factory.getExtractor(schoolId)
                        
                        if (extractor == null) {
                            Log.e(TAG, "未找到对应的提取器: $schoolId")
                            view?.destroy()
                            if (continuation.isActive) {
                                continuation.resume(emptyList())
                            }
                            return
                        }
                        
                        val extractScript = extractor.generateExtractionScript()
                        
                        view?.evaluateJavascript(extractScript) { result ->
                            try {
                                Log.d(TAG, "JavaScript执行完成，开始解析...")
                                val parsedCourses = extractor.parseCourses(result)
                                Log.d(TAG, "✅ 成功解析 ${parsedCourses.size} 门课程")
                                
                                // 清理WebView
                                view.destroy()
                                
                                if (continuation.isActive) {
                                    continuation.resume(parsedCourses)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "解析课程数据失败", e)
                                view?.destroy()
                                if (continuation.isActive) {
                                    continuation.resume(emptyList())
                                }
                            }
                        }
                    }
                    
                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        Log.e(TAG, "WebView加载错误: ${error?.description}")
                        view?.destroy()
                        if (continuation.isActive) {
                            continuation.resume(emptyList())
                        }
                    }
                }
                
                // 取消时清理
                continuation.invokeOnCancellation {
                    hiddenWebView.destroy()
                }
                
                // 设置超时（30秒），防止页面永远不加载完成导致 WebView 泄漏
                hiddenWebView.postDelayed({
                    if (continuation.isActive) {
                        Log.w(TAG, "WebView 加载超时，放弃更新")
                        hiddenWebView.destroy()
                        continuation.resume(emptyList())
                    }
                }, 30000L)
                
                // 加载页面
                hiddenWebView.loadUrl(school.loginUrl)
            }
            
            // 5. 对比并更新数据库
            if (courses.isEmpty()) {
                Log.w(TAG, "❌ 未提取到课程数据")
                recordFailure("未能提取到课程数据", System.currentTimeMillis() - startTime)
                return@withContext false
            }
            
            Log.d(TAG, "✅ 成功提取远程课表：${courses.size} 门课程")
            
            // 5.1 获取当前课表ID
            val currentSchedule = database.scheduleDao().getCurrentSchedule()
            if (currentSchedule == null) {
                Log.e(TAG, "未找到当前课表")
                recordFailure("未找到当前课表", System.currentTimeMillis() - startTime)
                return@withContext false
            }
            
            val scheduleId = currentSchedule.id
            
            // 5.2 获取本地现有课程（用于对比和保留颜色）
            val localCourses = database.courseDao().getAllCoursesBySchedule(scheduleId).first()
            Log.d(TAG, "本地现有课程: ${localCourses.size} 门")
            
            // 5.3 建立课程颜色映射（保留原课程颜色）
            val courseColorMap = localCourses.associateBy(
                { "${it.courseName}_${it.teacher}" },
                { it.color }
            )
            
            // 5.4 转换为Course实体，保留原颜色
            val remoteCourses = courses.map { parsed ->
                val key = "${parsed.courseName}_${parsed.teacher}"
                val existingColor = courseColorMap[key] // 查找是否有原颜色
                
                Course(
                    id = 0,
                    scheduleId = scheduleId,
                    courseName = parsed.courseName,
                    teacher = parsed.teacher,
                    classroom = parsed.classroom,
                    dayOfWeek = parsed.dayOfWeek,
                    startSection = parsed.startSection,
                    sectionCount = parsed.sectionCount,
                    weeks = parsed.weeks,
                    credit = parsed.credit,
                    color = existingColor ?: CourseColorPalette.getColorForCourse(parsed.courseName)
                )
            }
            
            Log.d(TAG, "远程课程数: ${remoteCourses.size} 门")
            
            // 5.5 检测变更
            val changeResult = CourseChangeDetector.detectChanges(localCourses, remoteCourses)
            val hasChanges = with(CourseChangeDetector) { changeResult.hasChanges() }
            
            if (!hasChanges) {
                // 无变更
                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "✅ 课表无变化，无需更新，耗时: ${duration}ms")
                recordNoChange(duration)
                return@withContext true
            }
            
            // 5.6 有变更：更新数据库
            val changeSummary = with(CourseChangeDetector) { changeResult.getSummary() }
            Log.d(TAG, "检测到变更：$changeSummary")
            
            database.courseDao().deleteAllCoursesBySchedule(scheduleId)
            database.courseDao().insertCourses(remoteCourses)
            
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "✅ 课表已更新，$changeSummary，耗时: ${duration}ms")
            
            // 5.7 发送变更通知
            CourseChangeNotificationHelper.sendChangeNotification(context, changeResult)
            Log.d(TAG, "✓ 变更通知已发送")
            
            // 5.8 记录成功（包含变更信息）
            recordSuccessWithChanges(changeSummary, changeResult, duration)
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "自动更新异常", e)
            recordFailure("更新异常: ${e.message}", System.currentTimeMillis() - startTime)
            false
        }
    }
    
    /**
     * 立即更新（用户手动触发）
     */
    suspend fun updateNow(schoolId: String): Boolean {
        Log.d(TAG, "用户手动触发更新")
        return performBackgroundUpdate(schoolId)
    }
    
    /**
     * 记录成功
     */
    private fun recordSuccess(courseCount: Int, duration: Long) {
        val config = getConfig()
        updateConfig(
            config.copy(
                lastUpdateTime = System.currentTimeMillis(),
                totalAttempts = config.totalAttempts + 1,
                successCount = config.successCount + 1
            )
        )
        
        addUpdateLog(
            UpdateStatus.SUCCESS,
            "成功更新 $courseCount 门课程",
            duration
        )
    }
    
    /**
     * 记录成功（包含变更信息）
     */
    private fun recordSuccessWithChanges(
        changeSummary: String, 
        changeResult: CourseChangeResult,
        duration: Long
    ) {
        // 计算总变更数量（新增 + 删除 + 调课）
        val changesCount = changeResult.addedCourses.size + 
                          changeResult.removedCourses.size + 
                          changeResult.adjustedCourses.size
        
        val config = getConfig()
        updateConfig(
            config.copy(
                lastUpdateTime = System.currentTimeMillis(),
                totalAttempts = config.totalAttempts + 1,
                successCount = config.successCount + 1,
                totalChangesDetected = config.totalChangesDetected + changesCount
            )
        )
        
        addUpdateLog(
            UpdateStatus.SUCCESS,
            changeSummary,
            duration
        )
    }
    
    /**
     * 记录无变更
     */
    private fun recordNoChange(duration: Long) {
        val config = getConfig()
        updateConfig(
            config.copy(
                lastUpdateTime = System.currentTimeMillis(),
                totalAttempts = config.totalAttempts + 1,
                successCount = config.successCount + 1
            )
        )
        
        addUpdateLog(
            UpdateStatus.SUCCESS,
            "无课程更新",
            duration
        )
    }
    
    /**
     * 记录失败
     */
    private fun recordFailure(message: String, duration: Long) {
        val config = getConfig()
        updateConfig(
            config.copy(
                totalAttempts = config.totalAttempts + 1,
                failureCount = config.failureCount + 1
            )
        )
        
        addUpdateLog(UpdateStatus.FAILURE, message, duration)
    }
    
    /**
     * 增加跳过计数
     */
    private fun incrementSkipCount() {
        val config = getConfig()
        updateConfig(
            config.copy(
                totalAttempts = config.totalAttempts + 1,  // ✅ 修复：增加总次数
                skipCount = config.skipCount + 1
            )
        )
    }
    
    /**
     * 添加更新日志
     */
    private fun addUpdateLog(status: UpdateStatus, message: String, duration: Long = 0L) {
        val log = UpdateLogEntry(
            timestamp = System.currentTimeMillis(),
            status = status,
            message = message,
            duration = duration
        )
        
        // 获取现有日志
        val logs = getUpdateLogs().toMutableList()
        logs.add(0, log)  // 最新的放前面
        
        // 只保留最近50条
        if (logs.size > 50) {
            logs.subList(50, logs.size).clear()
        }
        
        // 序列化保存
        val logsJson = gson.toJson(logs)
        prefs.edit().putString("update_logs", logsJson).apply()
    }
    
    /**
     * 获取更新日志
     */
    fun getUpdateLogs(): List<UpdateLogEntry> {
        val logsJson = prefs.getString("update_logs", "[]") ?: "[]"
        return try {
            gson.fromJson(logsJson, Array<UpdateLogEntry>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 清空更新日志
     */
    fun clearLogs() {
        prefs.edit().putString("update_logs", "[]").apply()
    }
    
    /**
     * 清空统计数据
     */
    fun clearStatistics() {
        val config = getConfig()
        updateConfig(
            config.copy(
                totalAttempts = 0,
                successCount = 0,
                failureCount = 0,
                skipCount = 0,
                totalChangesDetected = 0
            )
        )
    }
    
    private fun extractDomain(url: String): String {
        return try {
            java.net.URI(url).host ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}

/**
 * 自动更新配置
 */
data class AutoUpdateConfig(
    val enabled: Boolean = false,              // 是否启用
    val minIntervalHours: Int = 6,            // 最小更新间隔（小时）
    val lastUpdateTime: Long = 0L,            // 上次更新时间戳
    val totalAttempts: Int = 0,               // 总次数
    val successCount: Int = 0,                // 成功次数
    val failureCount: Int = 0,                // 失败次数
    val skipCount: Int = 0,                   // 跳过次数
    val totalChangesDetected: Int = 0         // 累计发现的课程更新数量（新增+删除+调课）
)

/**
 * 更新日志条目
 */
data class UpdateLogEntry(
    val timestamp: Long,
    val status: UpdateStatus,
    val message: String,
    val duration: Long = 0L  // 耗时（毫秒）
)

enum class UpdateStatus {
    SUCCESS,    // 成功
    FAILURE,    // 失败
    SKIPPED     // 跳过
}








