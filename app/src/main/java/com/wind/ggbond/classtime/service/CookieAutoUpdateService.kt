package com.wind.ggbond.classtime.service

import com.wind.ggbond.classtime.data.model.SchoolConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Deprecated("已合并到 UnifiedScheduleUpdateService，请直接使用 UnifiedScheduleUpdateService")
@Singleton
class CookieAutoUpdateService @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val unifiedScheduleUpdateService: UnifiedScheduleUpdateService
) {
    companion object { private const val TAG = "CookieAutoUpdate" }

    @Deprecated("请使用 UnifiedScheduleUpdateService.hasSavedCookie()", ReplaceWith("unifiedScheduleUpdateService.hasSavedCookie(domain)"))
    suspend fun hasSavedCookie(domain: String): Boolean = unifiedScheduleUpdateService.hasSavedCookie(domain)

    @Deprecated("请使用 UnifiedScheduleUpdateService.performUpdate()", ReplaceWith("unifiedScheduleUpdateService.performUpdate(request)"))
    suspend fun performAutoUpdate(schoolConfig: SchoolConfig, scheduleId: Long): Result<AutoUpdateResult> =
        withContext(Dispatchers.IO) {
            val request = ScheduleUpdateRequest.Auto(schoolConfig = schoolConfig, scheduleId = scheduleId)
            val result = unifiedScheduleUpdateService.performUpdate(request)
            if (result.isSuccess) {
                val r = result.getOrNull()!!
                Result.success(AutoUpdateResult(r.success, r.courseCount, r.message, r.hasChanges, r.changeDetails))
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("更新失败"))
            }
        }

    @Deprecated("请使用 UnifiedScheduleUpdateService.shouldUpdate()", ReplaceWith("unifiedScheduleUpdateService.shouldUpdate()"))
    suspend fun shouldUpdate(): Boolean = unifiedScheduleUpdateService.shouldUpdate()

    @Deprecated("请使用 UnifiedScheduleUpdateService.performSimpleUpdate()", ReplaceWith("unifiedScheduleUpdateService.performSimpleUpdate()"))
    suspend fun performUpdate(): Pair<Boolean, String> = unifiedScheduleUpdateService.performSimpleUpdate()

    data class AutoUpdateResult(val success: Boolean, val courseCount: Int, val message: String, val hasChanges: Boolean = true, val changeDetails: String = "")
}
