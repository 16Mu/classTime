package com.wind.ggbond.classtime.service

import android.content.Context
import android.util.Log
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.UpdateResult
import com.wind.ggbond.classtime.data.model.SchoolConfig
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.AutoUpdateLogRepository
import com.wind.ggbond.classtime.util.SecureCookieManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 🍪 Cookie自动更新服务
 * 
 * 功能：使用保存的Cookie自动更新课表
 * 
 * 工作流程：
 * 0. 用户首次手动登录 → 保存Cookie
 * 1. 用户进入软件
 * 2. 判断是否触发更新(用户设置的每X天后更新)
 * 3. 自动更新触发
 * 4. 后台创建隐藏WebView
 * 5. 加载登录页面 (loginUrl)
 * 6. 服务器验证Cookie ✅
 * 7. 自动跳转到课表页面 ✅
 * 8. 等待页面加载完成
 * 9. 使用CQEPCExtractor提取课表 ✅
 * 10. 更新数据库
 * 11. 清理WebView，完成 ✅
 * 
 * @author AI Assistant
 * @since 2025-11-04
 */
@Singleton
class CookieAutoUpdateService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val unifiedScheduleFetchService: UnifiedScheduleFetchService,
    private val courseRepository: CourseRepository,
    private val autoUpdateLogRepository: AutoUpdateLogRepository,
    private val secureCookieManager: SecureCookieManager,
    private val schoolRepository: com.wind.ggbond.classtime.data.repository.SchoolRepository,
    private val scheduleRepository: com.wind.ggbond.classtime.data.repository.ScheduleRepository,
    private val courseAdjustmentRepository: com.wind.ggbond.classtime.data.repository.CourseAdjustmentRepository
) {
    companion object {
        private const val TAG = "CookieAutoUpdate"
    }
    
    /**
     * 检查是否有保存的Cookie
     */
    suspend fun hasSavedCookie(domain: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                secureCookieManager.hasCookies(domain)
            } catch (e: Exception) {
                Log.e(TAG, "检查Cookie失败", e)
                false
            }
        }
    }
    
    /**
     * 执行自动更新
     * 
     * @param schoolConfig 学校配置
     * @param scheduleId 课表ID
     * @return 更新结果
     */
    suspend fun performAutoUpdate(
        schoolConfig: SchoolConfig,
        scheduleId: Long
    ): Result<AutoUpdateResult> {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            
            try {
                Log.d(TAG, "开始自动更新课表: ${schoolConfig.name}, scheduleId=$scheduleId")
                
                // 1. 使用统一的课表获取服务（内部会自动判断使用Cookie还是账号密码）
                val fetchResult = unifiedScheduleFetchService.fetchSchedule(
                    schoolConfig = schoolConfig,
                    showWebView = false // 后台更新，不显示WebView
                )
                
                if (fetchResult.isFailure) {
                    val error = fetchResult.exceptionOrNull()
                    Log.e(TAG, "获取课表失败", error)
                    
                    // 记录更新日志（失败）
                    autoUpdateLogRepository.logUpdate(
                        triggerEvent = "自动更新",
                        result = UpdateResult.FAILED,
                        failureReason = error?.message ?: "未知错误",
                        scheduleId = scheduleId,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                    
                    return@withContext Result.failure(
                        error ?: Exception("获取课表失败")
                    )
                }
                
                val (parsedCourses, cookie) = fetchResult.getOrThrow()
                Log.d(TAG, "成功获取远程课表，共 ${parsedCourses.size} 门课程")
                
                // 1.1 更新Cookie（可能已刷新）
                if (cookie.isNotEmpty()) {
                    val domain = extractDomain(schoolConfig.loginUrl)
                    secureCookieManager.saveCookies(domain, cookie)
                    Log.d(TAG, "✓ Cookie已更新")
                }
                
                // 2. 获取本地现有课程（用于对比和保留颜色）
                val localCourses = courseRepository.getAllCoursesBySchedule(scheduleId).first()
                Log.d(TAG, "本地现有课程: ${localCourses.size} 门")
                
                // 2.1 建立课程颜色映射（保留原课程颜色）
                val courseColorMap = localCourses.associateBy(
                    { "${it.courseName}_${it.teacher}" },
                    { it.color }
                )
                
                // 2.2 转换为Course实体（用于对比），保留原颜色
                val remoteCourses = parsedCourses.map { parsed ->
                    val key = "${parsed.courseName}_${parsed.teacher}"
                    val existingColor = courseColorMap[key] // 查找是否有原颜色
                    
                    Course(
                        id = 0, // 临时ID
                        scheduleId = scheduleId,
                        courseName = parsed.courseName,
                        teacher = parsed.teacher,
                        classroom = parsed.classroom,
                        dayOfWeek = parsed.dayOfWeek,
                        startSection = parsed.startSection,
                        sectionCount = parsed.sectionCount,
                        weeks = parsed.weeks,
                        credit = parsed.credit,
                        color = existingColor ?: com.wind.ggbond.classtime.util.CourseColorPalette.getColorForCourse(parsed.courseName)
                    )
                }
                
                // 3. 获取本地课程列表（重新获取以防止变化）
                Log.d(TAG, "远程课程数: ${remoteCourses.size} 门")
                
                // 4. 检测课程变更
                val changeResult = com.wind.ggbond.classtime.util.CourseChangeDetector
                    .detectChanges(localCourses, remoteCourses)
                
                val hasChanges = with(com.wind.ggbond.classtime.util.CourseChangeDetector) {
                    changeResult.hasChanges()
                }
                
                if (!hasChanges) {
                    // 无变更，不更新数据库
                    Log.d(TAG, "✅ 课表无变化，无需更新")
                    
                    autoUpdateLogRepository.logUpdate(
                        triggerEvent = "自动更新",
                        result = UpdateResult.SUCCESS,
                        successMessage = "无课程更新",
                        scheduleId = scheduleId,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                    
                    return@withContext Result.success(
                        AutoUpdateResult(
                            success = true,
                            courseCount = 0,
                            message = "无课程更新",
                            hasChanges = false
                        )
                    )
                }
                
                // 5. 有变更：更新数据库并生成调课记录
                Log.d(TAG, "检测到变更：${with(com.wind.ggbond.classtime.util.CourseChangeDetector) { changeResult.getSummary() }}")
                
                // 5.1 删除旧课程并插入新课程
                courseRepository.deleteAllCoursesBySchedule(scheduleId)
                val insertedIds = courseRepository.insertCourses(remoteCourses)
                Log.d(TAG, "✓ 课程已更新到数据库，共 ${insertedIds.size} 门")
                
                // 5.2 为调课生成调课记录并保存到数据库
                if (changeResult.adjustedCourses.isNotEmpty()) {
                    Log.d(TAG, "开始生成调课记录，共 ${changeResult.adjustedCourses.size} 门课程")
                    try {
                        // 获取新插入的课程列表
                        val newCoursesList = courseRepository.getAllCoursesBySchedule(scheduleId).first()
                        var adjustmentCount = 0
                        
                        // 为每个调课生成调课记录
                        changeResult.adjustedCourses.forEach { adj ->
                            // ⚠️ 注意：adj.newTime.weeks 已经是发生迁移的周次了
                            // 需要在新课程列表中找到包含这些周次的课程
                            
                            // 找到新时间的课程（可能包含更多周次，我们只需要匹配时间段）
                            val newCourse = newCoursesList.find { course ->
                                course.courseName == adj.courseName &&
                                course.teacher == adj.teacher &&
                                course.dayOfWeek == adj.newTime.dayOfWeek &&
                                course.startSection == adj.newTime.startSection &&
                                course.sectionCount == adj.newTime.sectionCount &&
                                // 确保新课程包含这些迁移的周次
                                adj.newTime.weeks.all { it in course.weeks }
                            }
                            
                            if (newCourse != null) {
                                // 为每个迁移的周次创建一条调课记录
                                adj.newTime.weeks.forEach { week ->
                                    val adjustment = com.wind.ggbond.classtime.data.local.entity.CourseAdjustment(
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
                                    courseAdjustmentRepository.saveAdjustment(adjustment)
                                    adjustmentCount++
                                }
                                Log.d(TAG, "✓ 已创建调课记录: ${adj.courseName} (第${adj.newTime.weeks}周)")
                            } else {
                                Log.w(TAG, "⚠ 未找到调课后的课程: ${adj.courseName} (周${adj.newTime.dayOfWeek} 第${adj.newTime.startSection}节)")
                            }
                        }
                        Log.d(TAG, "✓ 调课记录创建完成，共 $adjustmentCount 条")
                    } catch (e: Exception) {
                        Log.w(TAG, "创建调课记录失败", e)
                        // 即使调课记录创建失败，也不影响整体更新
                    }
                }
                
                // 6. 发送变更通知
                com.wind.ggbond.classtime.util.CourseChangeNotificationHelper
                    .sendChangeNotification(context, changeResult)
                Log.d(TAG, "✓ 变更通知已发送")
                
                // 7. 记录更新日志（成功，包含变更详情）
                val detailedMessage = with(com.wind.ggbond.classtime.util.CourseChangeDetector) {
                    changeResult.getDetailedMessage()
                }
                
                autoUpdateLogRepository.logUpdate(
                    triggerEvent = "自动更新",
                    result = UpdateResult.SUCCESS,
                    successMessage = with(com.wind.ggbond.classtime.util.CourseChangeDetector) { 
                        changeResult.getSummary() 
                    },
                    scheduleId = scheduleId,
                    durationMs = System.currentTimeMillis() - startTime
                )
                
                // 8. 返回结果
                Result.success(
                    AutoUpdateResult(
                        success = true,
                        courseCount = remoteCourses.size,
                        message = with(com.wind.ggbond.classtime.util.CourseChangeDetector) { 
                            changeResult.getSummary() 
                        },
                        hasChanges = true,
                        changeDetails = detailedMessage
                    )
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "自动更新失败", e)
                
                // 记录更新日志（失败）
                try {
                    autoUpdateLogRepository.logUpdate(
                        triggerEvent = "自动更新",
                        result = UpdateResult.FAILED,
                        failureReason = e.message ?: "未知错误",
                        scheduleId = scheduleId,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                } catch (logError: Exception) {
                    Log.e(TAG, "记录更新日志失败", logError)
                }
                
                Result.failure(e)
            }
        }
    }
    
    /**
     * 从URL中提取域名
     */
    private fun extractDomain(url: String): String {
        return try {
            val urlObj = java.net.URL(url)
            urlObj.host
        } catch (e: Exception) {
            url
        }
    }
    
    /**
     * 检查是否应该更新（兼容旧API）
     */
    suspend fun shouldUpdate(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 获取当前课表
                val currentSchedule = scheduleRepository.getCurrentSchedule() ?: return@withContext false
                
                // 获取学校配置
                val schoolId = currentSchedule.schoolName ?: return@withContext false
                val schoolEntity = schoolRepository.getSchoolById(schoolId) ?: return@withContext false
                
                // 检查是否有Cookie
                val domain = extractDomain(schoolEntity.loginUrl)
                hasSavedCookie(domain)
            } catch (e: Exception) {
                Log.e(TAG, "检查更新条件失败", e)
                false
            }
        }
    }
    
    /**
     * 执行更新（兼容旧API）
     * @return Pair<成功, 消息>
     */
    suspend fun performUpdate(): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                // 获取当前课表
                val currentSchedule = scheduleRepository.getCurrentSchedule()
                    ?: return@withContext Pair(false, "未找到当前课表")
                
                // 获取学校配置
                val schoolId = currentSchedule.schoolName ?: return@withContext Pair(false, "课表缺少学校配置")
                val schoolEntity = schoolRepository.getSchoolById(schoolId)
                    ?: return@withContext Pair(false, "未找到学校配置: $schoolId")
                
                // 转换为SchoolConfig
                val schoolConfig = SchoolConfig(
                    id = schoolEntity.id,
                    name = schoolEntity.name,
                    loginUrl = schoolEntity.loginUrl,
                    scheduleUrl = schoolEntity.scheduleUrl,
                    scheduleMethod = schoolEntity.scheduleMethod,
                    scheduleParams = schoolEntity.scheduleParams,
                    dataFormat = when (schoolEntity.dataFormat.lowercase()) {
                        "json" -> com.wind.ggbond.classtime.data.model.DataFormat.JSON
                        "html" -> com.wind.ggbond.classtime.data.model.DataFormat.HTML
                        "xml" -> com.wind.ggbond.classtime.data.model.DataFormat.XML
                        else -> com.wind.ggbond.classtime.data.model.DataFormat.HTML
                    },
                    jsonPaths = schoolEntity.jsonMapping,
                    needCsrfToken = schoolEntity.needCsrfToken,
                    csrfTokenName = schoolEntity.csrfTokenName
                )
                
                // 执行更新
                val result = performAutoUpdate(schoolConfig, currentSchedule.id)
                
                if (result.isSuccess) {
                    val updateResult = result.getOrNull()!!
                    Pair(true, updateResult.message)
                } else {
                    val error = result.exceptionOrNull()
                    Pair(false, error?.message ?: "更新失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "执行更新失败", e)
                Pair(false, e.message ?: "更新失败")
            }
        }
    }
    
    /**
     * 更新结果
     */
    data class AutoUpdateResult(
        val success: Boolean,
        val courseCount: Int,
        val message: String,
        val hasChanges: Boolean = true,  // 是否有变更
        val changeDetails: String = ""   // 变更详情
    )
}




