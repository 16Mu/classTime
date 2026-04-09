package com.wind.ggbond.classtime.util

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import com.wind.ggbond.classtime.data.local.database.CourseDatabase
import androidx.room.withTransaction
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.util.extractor.*
import com.wind.ggbond.classtime.util.parser.WeekParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.isActive
import kotlin.coroutines.resume

class AutoUpdateManager(
    private val context: Context,
    private val database: CourseDatabase,
    private val extractorFactory: SchoolExtractorFactory? = null
) {
    companion object {
        private const val TAG = "AutoUpdateManager"
        private const val PREFS_NAME = "auto_update_prefs"
        private const val MAX_LOGS = 50
        private const val WEBVIEW_TIMEOUT_MS = 30_000L
        val INTERVAL_OPTIONS = listOf(6, 12, 24, 48, 72)
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cookieManager = SecureCookieManager(context)
    private val gson = Gson()

    fun getConfig(): AutoUpdateConfig = AutoUpdateConfig(
        enabled = prefs.getBoolean("enabled", false),
        minIntervalHours = prefs.getInt("min_interval_hours", 6),
        lastUpdateTime = prefs.getLong("last_update_time", 0L),
        totalAttempts = prefs.getInt("total_attempts", 0),
        successCount = prefs.getInt("success_count", 0),
        failureCount = prefs.getInt("failure_count", 0),
        skipCount = prefs.getInt("skip_count", 0),
        totalChangesDetected = prefs.getInt("total_changes_detected", 0)
    )

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

    suspend fun checkAndUpdateIfNeeded(schoolId: String): Boolean {
        val config = getConfig()
        if (!config.enabled) return false

        val timeSinceLast = System.currentTimeMillis() - config.lastUpdateTime
        val minIntervalMs = config.minIntervalHours * 3600_000L
        if (timeSinceLast < minIntervalMs) {
            incrementSkipCount()
            addUpdateLog(UpdateStatus.SKIPPED, "距离上次更新不足${config.minIntervalHours}小时")
            return false
        }
        return performBackgroundUpdate(schoolId)
    }

    suspend fun updateNow(schoolId: String): Boolean = performBackgroundUpdate(schoolId)

    // ==================== 核心更新逻辑 ====================

    private suspend fun performBackgroundUpdate(schoolId: String): Boolean {
        val startTime = System.currentTimeMillis()
        return try {
            val school = withContext(Dispatchers.IO) {
                database.schoolDao().getSchoolById(schoolId)?.let { s ->
                    Triple(s, extractDomain(s.loginUrl), cookieManager.getCookies(extractDomain(s.loginUrl)))
                }
            } ?: run { recordFailure("学校信息不存在", elapsed(startTime)); return false }

            val (savedSchool, domain, savedCookie) = school
            if (savedCookie == null) { recordFailure("Cookie已过期，请重新导入课表", elapsed(startTime)); return false }

            val courses = fetchCoursesViaWebView(savedSchool, domain, savedCookie, schoolId)
            if (courses.isEmpty()) { recordFailure("未能提取到课程数据", elapsed(startTime)); return false }

            val currentSchedule = withContext(Dispatchers.IO) { database.scheduleDao().getCurrentSchedule() }
                ?: run { recordFailure("未找到当前课表", elapsed(startTime)); return false }

            val scheduleId = currentSchedule.id
            val localCourses = withContext(Dispatchers.IO) { database.courseDao().getAllCoursesBySchedule(scheduleId).first() }

            val courseColorMap = localCourses.associateBy({ "${it.courseName}_${it.teacher}" }, { it.color })
            val courseSettingsMap = localCourses.associateBy({ "${it.courseName}_${it.teacher}" })
            val remoteCourses = courses.map { parsed ->
                val key = "${parsed.courseName}_${parsed.teacher}"
                val existing = courseSettingsMap[key]
                Course(id = 0, scheduleId = scheduleId, courseName = parsed.courseName, teacher = parsed.teacher,
                    classroom = parsed.classroom, dayOfWeek = parsed.dayOfWeek, startSection = parsed.startSection,
                    sectionCount = parsed.sectionCount, weeks = parsed.weeks,
                    weekExpression = if (parsed.weekExpression.isNotEmpty()) parsed.weekExpression else WeekParser.formatWeekList(parsed.weeks),
                    credit = parsed.credit, courseCode = parsed.courseCode.ifEmpty { existing?.courseCode ?: "" },
                    color = courseColorMap[key] ?: CourseColorPalette.getColorForCourse(parsed.courseName),
                    note = existing?.note ?: "", reminderEnabled = existing?.reminderEnabled ?: false,
                    reminderMinutes = existing?.reminderMinutes ?: 10)
            }

            val changeResult = CourseChangeDetector.detectChanges(localCourses, remoteCourses)
            if (!changeResult.hasChanges()) { recordNoChange(elapsed(startTime)); return true }

            val summary = changeResult.getSummary()
            withContext(Dispatchers.IO) {
                database.withTransaction {
                    database.courseDao().deleteAllCoursesBySchedule(scheduleId)
                    database.courseDao().insertCourses(remoteCourses)
                }
            }
            CourseChangeNotificationHelper.sendChangeNotification(context, changeResult)
            recordSuccessWithChanges(summary, changeResult, elapsed(startTime))
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "自动更新异常", e)
            recordFailure("更新异常: ${e.message}", elapsed(startTime))
            false
        }
    }

    // ==================== WebView 数据获取 ====================

    private suspend fun fetchCoursesViaWebView(
        school: com.wind.ggbond.classtime.data.local.entity.SchoolEntity,
        domain: String, cookie: String, schoolId: String
    ): List<ParsedCourse> = suspendCancellableCoroutine { cont ->
        val webViewContext = MutableContextWrapper(context.applicationContext)
        val wv = WebView(webViewContext).apply {
            layoutParams = ViewGroup.LayoutParams(1, 1)
            visibility = View.GONE
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
        }
        CookieManager.getInstance().setCookie(domain, cookie)

        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val factory = extractorFactory
                if (factory == null) { resumeEmpty(wv, cont); return }
                val extractor = factory.getExtractor(schoolId)
                if (extractor == null) { resumeEmpty(wv, cont); return }
                val script = extractor.generateExtractionScript()
                view?.evaluateJavascript(script) { result ->
                    try {
                        val parsed = extractor.parseCourses(result)
                        destroyWebView(wv)
                        if (cont.isActive) cont.resume(parsed)
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "解析失败", e); destroyWebView(wv); if (cont.isActive) cont.resume(emptyList())
                    }
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                AppLogger.e(TAG, "WebView错误: ${error?.description}")
                resumeEmpty(wv, cont)
            }
        }

        cont.invokeOnCancellation { destroyWebView(wv) }
        wv.postDelayed({ if (cont.isActive) { destroyWebView(wv); cont.resume(emptyList()) } }, WEBVIEW_TIMEOUT_MS)
        wv.loadUrl(school.loginUrl)
    }

    private fun destroyWebView(wv: WebView) {
        (wv.context as? MutableContextWrapper)?.setBaseContext(null)
        wv.destroy()
    }

    private fun resumeEmpty(wv: WebView, cont: CancellableContinuation<List<ParsedCourse>>) {
        destroyWebView(wv)
        if (cont.isActive) cont.resume(emptyList())
    }

    // ==================== 统计记录（统一模式） ====================

    private inline fun updateStats(block: AutoUpdateConfig.() -> AutoUpdateConfig) {
        updateConfig(getConfig().block())
    }

    private fun recordSuccessWithChanges(changeSummary: String, result: CourseChangeDetector.CourseChangeResult, duration: Long) {
        val changesCount = result.addedCourses.size + result.removedCourses.size + result.adjustedCourses.size
        updateStats { copy(lastUpdateTime = System.currentTimeMillis(), totalAttempts = totalAttempts + 1, successCount = successCount + 1, totalChangesDetected = totalChangesDetected + changesCount) }
        addUpdateLog(UpdateStatus.SUCCESS, changeSummary, duration)
    }

    private fun recordNoChange(duration: Long) {
        updateStats { copy(lastUpdateTime = System.currentTimeMillis(), totalAttempts = totalAttempts + 1, successCount = successCount + 1) }
        addUpdateLog(UpdateStatus.SUCCESS, "无课程更新", duration)
    }

    private fun recordFailure(message: String, duration: Long) {
        updateStats { copy(totalAttempts = totalAttempts + 1, failureCount = failureCount + 1) }
        addUpdateLog(UpdateStatus.FAILURE, message, duration)
    }

    private fun incrementSkipCount() {
        updateStats { copy(totalAttempts = totalAttempts + 1, skipCount = skipCount + 1) }
    }

    // ==================== 日志管理 ====================

    private fun addUpdateLog(status: UpdateStatus, message: String, duration: Long = 0L) {
        val logs = getUpdateLogs().toMutableList().apply {
            add(0, UpdateLogEntry(System.currentTimeMillis(), status, message, duration))
            if (size > MAX_LOGS) subList(MAX_LOGS, size).clear()
        }
        prefs.edit().putString("update_logs", gson.toJson(logs)).apply()
    }

    fun getUpdateLogs(): List<UpdateLogEntry> = try {
        gson.fromJson(prefs.getString("update_logs", "[]") ?: "[]", Array<UpdateLogEntry>::class.java).toList()
    } catch (_: Exception) { emptyList() }

    fun clearLogs() = prefs.edit().putString("update_logs", "[]").apply()

    fun clearStatistics() = updateStats {
        copy(totalAttempts = 0, successCount = 0, failureCount = 0, skipCount = 0, totalChangesDetected = 0)
    }

    private fun extractDomain(url: String): String = try { java.net.URI(url).host ?: "" } catch (_: Exception) { "" }
    private fun elapsed(start: Long): Long = System.currentTimeMillis() - start
}

data class AutoUpdateConfig(
    val enabled: Boolean = false,
    val minIntervalHours: Int = 6,
    val lastUpdateTime: Long = 0L,
    val totalAttempts: Int = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val skipCount: Int = 0,
    val totalChangesDetected: Int = 0
)

data class UpdateLogEntry(val timestamp: Long, val status: UpdateStatus, val message: String, val duration: Long = 0L)

enum class UpdateStatus { SUCCESS, FAILURE, SKIPPED }
