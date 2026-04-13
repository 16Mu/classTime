package com.wind.ggbond.classtime.service

import android.content.Context
import com.wind.ggbond.classtime.data.local.database.CourseDatabase
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.data.model.SchoolConfig
import com.wind.ggbond.classtime.data.repository.AutoUpdateLogRepository
import com.wind.ggbond.classtime.data.repository.CourseAdjustmentRepository
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.data.repository.SchoolRepository
import com.wind.ggbond.classtime.service.contract.CookieExpiredException
import com.wind.ggbond.classtime.service.contract.CookieExpiredReason
import com.wind.ggbond.classtime.service.contract.IScheduleFetcher
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.util.UrlUtils
import com.wind.ggbond.classtime.util.AutoLoginManager
import com.wind.ggbond.classtime.util.AutoLoginResultCode
import com.wind.ggbond.classtime.util.CourseChangeDetector
import com.wind.ggbond.classtime.util.CourseChangeNotificationHelper
import com.wind.ggbond.classtime.util.CourseColorPalette
import com.wind.ggbond.classtime.util.SecureCookieManager
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnifiedScheduleUpdateService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduleFetcher: IScheduleFetcher,
    private val courseRepository: CourseRepository,
    private val autoUpdateLogRepository: AutoUpdateLogRepository,
    private val secureCookieManager: SecureCookieManager,
    private val schoolRepository: SchoolRepository,
    private val scheduleRepository: ScheduleRepository,
    private val courseAdjustmentRepository: CourseAdjustmentRepository,
    private val courseDatabase: CourseDatabase,
    private val autoLoginService: AutoLoginService,
    private val autoLoginManager: AutoLoginManager
) {

    companion object {
        private const val TAG = "UnifiedScheduleUpdate"
    }

    data class ScheduleUpdateResult(
        val success: Boolean,
        val courseCount: Int = 0,
        val message: String,
        val hasChanges: Boolean = true,
        val changeDetails: String = ""
    )

    suspend fun performUpdate(request: ScheduleUpdateRequest): Result<ScheduleUpdateResult> =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val schoolConfig = request.schoolConfig
            val scheduleId = request.scheduleId
            val showWebView = request.showWebView

            try {
                val fetchResult = scheduleFetcher.fetchSchedule(schoolConfig, showWebView)
                if (fetchResult.isFailure) {
                    return@withContext handleFetchFailure(
                        fetchResult, request, schoolConfig, scheduleId, showWebView, startTime
                    )
                }
                return@withContext processFetchResult(
                    fetchResult.getOrThrow(), schoolConfig, scheduleId, startTime
                )
            } catch (e: Exception) {
                logUpdateSafe(
                    result = com.wind.ggbond.classtime.data.local.entity.UpdateResult.FAILED,
                    failureReason = e.message ?: "未知错误",
                    scheduleId = scheduleId,
                    durationMs = elapsed(startTime)
                )
                Result.failure(e)
            }
        }

    private val ScheduleUpdateRequest.schoolConfig: SchoolConfig
        get() = when (this) {
            is ScheduleUpdateRequest.Manual -> schoolConfig
            is ScheduleUpdateRequest.Auto -> schoolConfig
            is ScheduleUpdateRequest.CookieRefresh -> schoolConfig
        }

    private val ScheduleUpdateRequest.scheduleId: Long
        get() = when (this) {
            is ScheduleUpdateRequest.Manual -> scheduleId
            is ScheduleUpdateRequest.Auto -> scheduleId
            is ScheduleUpdateRequest.CookieRefresh -> scheduleId
        }

    private val ScheduleUpdateRequest.showWebView: Boolean
        get() = when (this) {
            is ScheduleUpdateRequest.Manual -> showWebView
            else -> false
        }

    private suspend fun handleFetchFailure(
        fetchResult: Result<Pair<List<ParsedCourse>, String>>,
        request: ScheduleUpdateRequest,
        schoolConfig: SchoolConfig,
        scheduleId: Long,
        showWebView: Boolean,
        startTime: Long
    ): Result<ScheduleUpdateResult> {
        val exception = fetchResult.exceptionOrNull() ?: Exception("获取课表失败")
        val errorMsg = exception.message ?: "未知错误"
        val isCookieExpired = detectCookieExpired(exception, errorMsg, schoolConfig)

        if (isCookieExpired && shouldAttemptReLogin(request)) {
            return handleExpiredCookie(schoolConfig, scheduleId, showWebView, startTime)
        }

        logUpdate(
            result = com.wind.ggbond.classtime.data.local.entity.UpdateResult.FAILED,
            failureReason = errorMsg,
            scheduleId = scheduleId,
            durationMs = elapsed(startTime)
        )
        return Result.failure(exception)
    }

    private suspend fun detectCookieExpired(
        exception: Throwable,
        errorMsg: String,
        schoolConfig: SchoolConfig
    ): Boolean {
        if (exception is CookieExpiredException) {
            AppLogger.d(TAG, "Cookie过期检测: 类型匹配 -> ${exception.reason}")
            return true
        }

        val httpStatusCode = extractHttpStatusCode(exception)
        if (httpStatusCode != null && (httpStatusCode == 401 || httpStatusCode == 302)) {
            AppLogger.d(TAG, "Cookie过期检测: HTTP状态码匹配 -> $httpStatusCode")
            return true
        }

        val isCookieExpiredByMessage = errorMsg.contains("Cookie已失效") ||
                errorMsg.contains("登录已过期") ||
                errorMsg.contains("登录凭证不存在") ||
                errorMsg.contains("unauthorized", ignoreCase = true) ||
                errorMsg.contains("401")
        if (isCookieExpiredByMessage) {
            AppLogger.d(TAG, "Cookie过期检测: 字符串匹配兜底")
            return true
        }

        if (isPotentiallyCookieRelated(errorMsg) && proactiveCookieValidation(schoolConfig)) {
            AppLogger.d(TAG, "Cookie过期检测: 主动网络验证确认Cookie已失效")
            return true
        }

        return false
    }

    private fun isPotentiallyCookieRelated(errorMsg: String): Boolean {
        val lowerMsg = errorMsg.lowercase()
        return lowerMsg.contains("cookie") ||
                lowerMsg.contains("expired") ||
                lowerMsg.contains("登录") ||
                lowerMsg.contains("session") ||
                lowerMsg.contains("认证") ||
                lowerMsg.contains("authenticate") ||
                lowerMsg.contains("forbidden")
    }

    private fun extractHttpStatusCode(exception: Throwable): Int? {
        val current = exception
        if (current is CookieExpiredException) return current.httpStatusCode
        var cause = current.cause
        while (cause != null) {
            if (cause is CookieExpiredException) return cause.httpStatusCode
            cause = cause.cause
        }
        return null
    }

    private suspend fun proactiveCookieValidation(schoolConfig: SchoolConfig): Boolean {
        return try {
            val domain = extractDomain(schoolConfig.loginUrl)
            val cookies = secureCookieManager.getCookies(domain)
            if (cookies.isNullOrEmpty()) {
                return true
            }
            val validateUrl = schoolConfig.scheduleUrl.ifEmpty { schoolConfig.loginUrl }
            !secureCookieManager.validateCookies(domain, validateUrl)
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun handleExpiredCookie(
        schoolConfig: SchoolConfig,
        scheduleId: Long,
        showWebView: Boolean,
        startTime: Long
    ): Result<ScheduleUpdateResult> {
        AppLogger.d(TAG, "Cookie已过期，尝试自动重新登录...")
        val reLoginResult = tryReLoginWithCredentials(schoolConfig.id)

        if (reLoginResult.success) {
            AppLogger.d(TAG, "自动重新登录成功，重试获取课表...")
            val retryResult = scheduleFetcher.fetchSchedule(schoolConfig, showWebView)
            if (retryResult.isFailure) {
                logUpdate(
                    result = com.wind.ggbond.classtime.data.local.entity.UpdateResult.FAILED,
                    failureReason = retryResult.exceptionOrNull()?.message ?: "重试获取课表失败",
                    scheduleId = scheduleId,
                    durationMs = elapsed(startTime)
                )
                return Result.failure(retryResult.exceptionOrNull() ?: Exception("重试获取课表失败"))
            }
            return processFetchResult(retryResult.getOrThrow(), schoolConfig, scheduleId, startTime)
        }

        val failMsg = "Cookie过期且自动重新登录失败: ${reLoginResult.message}"
        autoLoginManager.saveLastUpdateResult(reLoginResult.resultCode, reLoginResult.message)
        logUpdate(
            result = com.wind.ggbond.classtime.data.local.entity.UpdateResult.FAILED,
            failureReason = failMsg,
            scheduleId = scheduleId,
            durationMs = elapsed(startTime)
        )
        return Result.failure(Exception(failMsg))
    }

    private suspend fun logUpdateSafe(
        result: com.wind.ggbond.classtime.data.local.entity.UpdateResult,
        successMessage: String? = null,
        failureReason: String? = null,
        scheduleId: Long,
        durationMs: Long
    ) {
        try {
            logUpdate(result, successMessage, failureReason, scheduleId, durationMs)
        } catch (_: Exception) {
        }
    }

    suspend fun hasSavedCookie(domain: String): Boolean = withContext(Dispatchers.IO) {
        try {
            secureCookieManager.hasCookies(domain)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun shouldUpdate(): Boolean = withContext(Dispatchers.IO) {
        try {
            val schedule = scheduleRepository.getCurrentSchedule() ?: return@withContext false
            val school = schoolRepository.getSchoolById(
                schedule.schoolName ?: return@withContext false
            ) ?: return@withContext false
            hasSavedCookie(extractDomain(school.loginUrl))
        } catch (_: Exception) {
            false
        }
    }

    suspend fun performSimpleUpdate(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val schedule = scheduleRepository.getCurrentSchedule()
                ?: return@withContext Pair(false, "未找到当前课表")
            val schoolId = schedule.schoolName
                ?: return@withContext Pair(false, "课表缺少学校配置")
            val schoolEntity = schoolRepository.getSchoolById(schoolId)
                ?: return@withContext Pair(false, "未找到学校配置: $schoolId")
            val config = toSchoolConfig(schoolEntity)
            val request = ScheduleUpdateRequest.Auto(
                schoolConfig = config,
                scheduleId = schedule.id
            )
            val result = performUpdate(request)
            if (result.isSuccess) {
                val r = result.getOrNull() ?: return@withContext Pair(false, "更新结果为空")
                Pair(true, r.message)
            } else {
                Pair(false, result.exceptionOrNull()?.message ?: "更新失败")
            }
        } catch (e: Exception) {
            Pair(false, e.message ?: "更新失败")
        }
    }

    private fun shouldAttemptReLogin(request: ScheduleUpdateRequest): Boolean {
        return when (request) {
            is ScheduleUpdateRequest.Auto -> autoLoginManager.isAutoLoginEnabled() && autoLoginManager.hasCredentials()
            is ScheduleUpdateRequest.CookieRefresh -> autoLoginManager.isAutoLoginEnabled() && autoLoginManager.hasCredentials()
            is ScheduleUpdateRequest.Manual -> false
        }
    }

    private suspend fun tryReLoginWithCredentials(schoolId: String): AutoLoginResult {
        val username = autoLoginManager.getUsername()
            ?: return AutoLoginResult(false, AutoLoginResultCode.NO_CREDENTIAL, "未保存账号密码")
        val password = autoLoginManager.getPassword()
            ?: return AutoLoginResult(false, AutoLoginResultCode.NO_CREDENTIAL, "未保存账号密码")
        if (username.isBlank() || password.isBlank())
            return AutoLoginResult(false, AutoLoginResultCode.NO_CREDENTIAL, "账号密码为空")
        return autoLoginService.performAutoLogin(schoolId, username, password)
    }

    private suspend fun processFetchResult(
        fetchData: Pair<List<ParsedCourse>, String>,
        schoolConfig: SchoolConfig,
        scheduleId: Long,
        startTime: Long
    ): Result<ScheduleUpdateResult> = withContext(Dispatchers.IO) {
        val (parsedCourses, cookie) = fetchData
        if (cookie.isNotEmpty()) {
            secureCookieManager.saveCookies(extractDomain(schoolConfig.loginUrl), cookie)
        }

        val localCourses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
        val colorMap = localCourses.associateBy(
            { "${it.courseName}_${it.teacher}" },
            { it.color }
        )
        val remoteCourses = parsedCourses.map { p ->
            Course(
                id = 0,
                scheduleId = scheduleId,
                courseName = p.courseName,
                teacher = p.teacher,
                classroom = p.classroom,
                dayOfWeek = p.dayOfWeek,
                startSection = p.startSection,
                sectionCount = p.sectionCount,
                weeks = p.weeks,
                credit = p.credit,
                color = colorMap["${p.courseName}_${p.teacher}"]
                    ?: CourseColorPalette.getColorForCourse(p.courseName)
            )
        }

        val changeResult = CourseChangeDetector.detectChanges(localCourses, remoteCourses)
        if (!changeResult.hasChanges()) {
            logUpdate(
                result = com.wind.ggbond.classtime.data.local.entity.UpdateResult.SUCCESS,
                successMessage = "无课程更新",
                scheduleId = scheduleId,
                durationMs = elapsed(startTime)
            )
            return@withContext Result.success(
                ScheduleUpdateResult(true, 0, "无课程更新", false)
            )
        }

        courseDatabase.withTransaction {
            courseRepository.deleteAllCoursesBySchedule(scheduleId)
            try {
                courseRepository.insertCourses(remoteCourses)
            } catch (e: Exception) {
                AppLogger.e(TAG, "插入更新后的课程失败", e)
                throw e
            }
        }

        if (changeResult.adjustedCourses.isNotEmpty()) {
            generateAdjustments(changeResult, scheduleId)
        }

        CourseChangeNotificationHelper.sendChangeNotification(context, changeResult)

        logUpdate(
            result = com.wind.ggbond.classtime.data.local.entity.UpdateResult.SUCCESS,
            successMessage = changeResult.getSummary(),
            scheduleId = scheduleId,
            durationMs = elapsed(startTime)
        )
        Result.success(
            ScheduleUpdateResult(
                true,
                remoteCourses.size,
                changeResult.getSummary(),
                true,
                changeResult.getDetailedMessage()
            )
        )
    }

    private suspend fun generateAdjustments(
        changeResult: CourseChangeDetector.CourseChangeResult,
        scheduleId: Long
    ) {
        try {
            val newCourses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            changeResult.adjustedCourses.forEach { adj ->
                val newCourse = newCourses.find { c ->
                    c.courseName == adj.courseName && c.teacher == adj.teacher &&
                            c.dayOfWeek == adj.newTime.dayOfWeek &&
                            c.startSection == adj.newTime.startSection &&
                            c.sectionCount == adj.newTime.sectionCount &&
                            adj.newTime.weeks.all { it in c.weeks }
                } ?: return@forEach
                adj.newTime.weeks.forEach { week ->
                    courseAdjustmentRepository.saveAdjustment(
                        com.wind.ggbond.classtime.data.local.entity.CourseAdjustment(
                            originalCourseId = newCourse.id,
                            scheduleId = scheduleId,
                            originalWeekNumber = week,
                            originalDayOfWeek = adj.oldTime.dayOfWeek,
                            originalStartSection = adj.oldTime.startSection,
                            originalSectionCount = adj.oldTime.sectionCount,
                            newWeekNumber = week,
                            newDayOfWeek = adj.newTime.dayOfWeek,
                            newStartSection = adj.newTime.startSection,
                            newSectionCount = adj.newTime.sectionCount,
                            reason = "自动更新检测到调课"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "生成调课记录失败", e)
        }
    }

        private fun extractDomain(url: String): String = UrlUtils.extractDomain(url)

    private fun toSchoolConfig(entity: com.wind.ggbond.classtime.data.local.entity.SchoolEntity): SchoolConfig =
        SchoolConfig(
            id = entity.id,
            name = entity.name,
            loginUrl = entity.loginUrl,
            scheduleUrl = entity.scheduleUrl,
            scheduleMethod = entity.scheduleMethod,
            scheduleParams = entity.scheduleParams,
            dataFormat = when (entity.dataFormat.lowercase()) {
                "json" -> com.wind.ggbond.classtime.data.model.DataFormat.JSON
                "xml" -> com.wind.ggbond.classtime.data.model.DataFormat.XML
                else -> com.wind.ggbond.classtime.data.model.DataFormat.HTML
            },
            jsonPaths = entity.jsonMapping,
            needCsrfToken = entity.needCsrfToken,
            csrfTokenName = entity.csrfTokenName
        )

    private suspend fun logUpdate(
        result: com.wind.ggbond.classtime.data.local.entity.UpdateResult,
        successMessage: String? = null,
        failureReason: String? = null,
        scheduleId: Long,
        durationMs: Long
    ) = autoUpdateLogRepository.logUpdate(
        triggerEvent = "自动更新",
        result = result,
        successMessage = successMessage,
        failureReason = failureReason,
        scheduleId = scheduleId,
        durationMs = durationMs
    )

    private fun elapsed(start: Long): Long = System.currentTimeMillis() - start
}
