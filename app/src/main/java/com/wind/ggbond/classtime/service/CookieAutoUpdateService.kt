package com.wind.ggbond.classtime.service

import android.content.Context
import com.wind.ggbond.classtime.data.local.database.CourseDatabase
import androidx.room.withTransaction
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.UpdateResult
import com.wind.ggbond.classtime.data.model.SchoolConfig
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.AutoUpdateLogRepository
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.util.CourseChangeDetector
import com.wind.ggbond.classtime.util.CourseChangeNotificationHelper
import com.wind.ggbond.classtime.util.CourseColorPalette
import com.wind.ggbond.classtime.util.SecureCookieManager
import com.wind.ggbond.classtime.service.contract.IScheduleFetcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CookieAutoUpdateService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val unifiedScheduleFetchService: IScheduleFetcher,
    private val courseRepository: CourseRepository,
    private val autoUpdateLogRepository: AutoUpdateLogRepository,
    private val secureCookieManager: SecureCookieManager,
    private val schoolRepository: com.wind.ggbond.classtime.data.repository.SchoolRepository,
    private val scheduleRepository: com.wind.ggbond.classtime.data.repository.ScheduleRepository,
    private val courseAdjustmentRepository: com.wind.ggbond.classtime.data.repository.CourseAdjustmentRepository,
    private val courseDatabase: CourseDatabase
) {
    companion object { private const val TAG = "CookieAutoUpdate" }

    suspend fun hasSavedCookie(domain: String): Boolean = withContext(Dispatchers.IO) {
        try { secureCookieManager.hasCookies(domain) } catch (_: Exception) { false }
    }

    suspend fun performAutoUpdate(schoolConfig: SchoolConfig, scheduleId: Long): Result<AutoUpdateResult> =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                val fetchResult = unifiedScheduleFetchService.fetchSchedule(schoolConfig, showWebView = false)
                if (fetchResult.isFailure) {
                    logUpdate(result = UpdateResult.FAILED, failureReason = fetchResult.exceptionOrNull()?.message ?: "未知错误", scheduleId = scheduleId, durationMs = elapsed(startTime))
                    return@withContext Result.failure(fetchResult.exceptionOrNull() ?: Exception("获取课表失败"))
                }

                val (parsedCourses, cookie) = fetchResult.getOrThrow()
                if (cookie.isNotEmpty()) secureCookieManager.saveCookies(extractDomain(schoolConfig.loginUrl), cookie)

                val localCourses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
                val colorMap = localCourses.associateBy({ "${it.courseName}_${it.teacher}" }, { it.color })
                val remoteCourses = parsedCourses.map { p ->
                    Course(id = 0, scheduleId = scheduleId, courseName = p.courseName, teacher = p.teacher,
                        classroom = p.classroom, dayOfWeek = p.dayOfWeek, startSection = p.startSection,
                        sectionCount = p.sectionCount, weeks = p.weeks, credit = p.credit,
                        color = colorMap["${p.courseName}_${p.teacher}"] ?: CourseColorPalette.getColorForCourse(p.courseName))
                }

                val changeResult = CourseChangeDetector.detectChanges(localCourses, remoteCourses)
                if (!changeResult.hasChanges()) {
                    logUpdate(result = UpdateResult.SUCCESS, successMessage = "无课程更新", scheduleId = scheduleId, durationMs = elapsed(startTime))
                    return@withContext Result.success(AutoUpdateResult(true, 0, "无课程更新", false))
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

                if (changeResult.adjustedCourses.isNotEmpty()) generateAdjustments(changeResult, scheduleId)

                CourseChangeNotificationHelper.sendChangeNotification(context, changeResult)

                logUpdate(result = UpdateResult.SUCCESS, successMessage = changeResult.getSummary(), scheduleId = scheduleId, durationMs = elapsed(startTime))
                Result.success(AutoUpdateResult(true, remoteCourses.size, changeResult.getSummary(), true, changeResult.getDetailedMessage()))
            } catch (e: Exception) {
                try { logUpdate(result = UpdateResult.FAILED, failureReason = e.message ?: "未知错误", scheduleId = scheduleId, durationMs = elapsed(startTime)) } catch (_: Exception) {}
                Result.failure(e)
            }
        }

    private suspend fun generateAdjustments(changeResult: CourseChangeDetector.CourseChangeResult, scheduleId: Long) {
        try {
            val newCourses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            changeResult.adjustedCourses.forEach { adj ->
                val newCourse = newCourses.find { c ->
                    c.courseName == adj.courseName && c.teacher == adj.teacher &&
                    c.dayOfWeek == adj.newTime.dayOfWeek && c.startSection == adj.newTime.startSection &&
                    c.sectionCount == adj.newTime.sectionCount && adj.newTime.weeks.all { it in c.weeks }
                } ?: return@forEach
                adj.newTime.weeks.forEach { week ->
                    courseAdjustmentRepository.saveAdjustment(
                        com.wind.ggbond.classtime.data.local.entity.CourseAdjustment(
                            originalCourseId = newCourse.id, scheduleId = scheduleId,
                            originalWeekNumber = week, originalDayOfWeek = adj.oldTime.dayOfWeek,
                            originalStartSection = adj.oldTime.startSection, originalSectionCount = adj.oldTime.sectionCount,
                            newWeekNumber = week, newDayOfWeek = adj.newTime.dayOfWeek,
                            newStartSection = adj.newTime.startSection, newSectionCount = adj.newTime.sectionCount,
                            reason = "自动更新检测到调课"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "生成调课记录失败", e)
        }
    }

    private fun extractDomain(url: String): String = try { java.net.URL(url).host } catch (_: Exception) { url }

    suspend fun shouldUpdate(): Boolean = withContext(Dispatchers.IO) {
        try {
            val schedule = scheduleRepository.getCurrentSchedule() ?: return@withContext false
            val school = schoolRepository.getSchoolById(schedule.schoolName ?: return@withContext false) ?: return@withContext false
            hasSavedCookie(extractDomain(school.loginUrl))
        } catch (_: Exception) { false }
    }

    suspend fun performUpdate(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val schedule = scheduleRepository.getCurrentSchedule() ?: return@withContext Pair(false, "未找到当前课表")
            val schoolId = schedule.schoolName ?: return@withContext Pair(false, "课表缺少学校配置")
            val schoolEntity = schoolRepository.getSchoolById(schoolId) ?: return@withContext Pair(false, "未找到学校配置: $schoolId")
            val config = toSchoolConfig(schoolEntity)
            val result = performAutoUpdate(config, schedule.id)
            if (result.isSuccess) {
                val r = result.getOrNull() ?: return@withContext Pair(false, "更新结果为空")
                Pair(true, r.message)
            } else Pair(false, result.exceptionOrNull()?.message ?: "更新失败")
        } catch (e: Exception) { Pair(false, e.message ?: "更新失败") }
    }

    private fun toSchoolConfig(entity: com.wind.ggbond.classtime.data.local.entity.SchoolEntity): SchoolConfig = SchoolConfig(
        id = entity.id, name = entity.name, loginUrl = entity.loginUrl, scheduleUrl = entity.scheduleUrl,
        scheduleMethod = entity.scheduleMethod, scheduleParams = entity.scheduleParams,
        dataFormat = when (entity.dataFormat.lowercase()) {
            "json" -> com.wind.ggbond.classtime.data.model.DataFormat.JSON
            "xml" -> com.wind.ggbond.classtime.data.model.DataFormat.XML
            else -> com.wind.ggbond.classtime.data.model.DataFormat.HTML
        },
        jsonPaths = entity.jsonMapping, needCsrfToken = entity.needCsrfToken, csrfTokenName = entity.csrfTokenName
    )

    private suspend fun logUpdate(result: UpdateResult, successMessage: String? = null, failureReason: String? = null, scheduleId: Long, durationMs: Long) =
        autoUpdateLogRepository.logUpdate(triggerEvent = "自动更新", result = result,
            successMessage = successMessage, failureReason = failureReason, scheduleId = scheduleId, durationMs = durationMs)

    private fun elapsed(start: Long): Long = System.currentTimeMillis() - start

    data class AutoUpdateResult(val success: Boolean, val courseCount: Int, val message: String, val hasChanges: Boolean = true, val changeDetails: String = "")
}
