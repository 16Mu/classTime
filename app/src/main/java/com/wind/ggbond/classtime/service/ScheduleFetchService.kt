package com.wind.ggbond.classtime.service

import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.data.model.SchoolConfig
import com.wind.ggbond.classtime.service.contract.IScheduleFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Deprecated("已合并到 UnifiedScheduleFetchService/UnifiedScheduleUpdateService，请使用新服务")
@Singleton
class ScheduleFetchService @Inject constructor(
    private val unifiedScheduleFetchService: IScheduleFetcher
) {
    companion object { private const val TAG = "ScheduleFetch" }

    @Deprecated("请使用 IScheduleFetcher.fetchSchedule()", ReplaceWith("unifiedScheduleFetchService.fetchSchedule(config, showWebView = false)"))
    suspend fun fetchSchedule(config: SchoolConfig, cookies: String): Result<String> =
        withContext(Dispatchers.IO) {
            val result = unifiedScheduleFetchService.fetchSchedule(config, showWebView = false)
            if (result.isSuccess) {
                Result.success(result.getOrNull()?.second ?: "")
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("获取课表失败"))
            }
        }

    @Deprecated("请使用 HtmlScheduleParser 或对应解析器直接解析")
    fun parseScheduleData(data: String, config: SchoolConfig): List<ParsedCourse> = emptyList()
}
