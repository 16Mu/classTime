package com.wind.ggbond.classtime.service

import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.data.model.SchoolConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import com.wind.ggbond.classtime.util.AppLogger
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 课表抓取服务
 * 
 * ✅ 使用专门的ScheduleClient，配置了更长的超时时间
 */
@Singleton
class ScheduleFetchService @Inject constructor(
    @com.wind.ggbond.classtime.di.ScheduleClient private val okHttpClient: OkHttpClient,
    private val gson: Gson = Gson()
) {
    companion object { private const val TAG = "ScheduleFetch" }
    
    /**
     * 使用Cookie抓取课表数据
     */
    suspend fun fetchSchedule(config: SchoolConfig, cookies: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(config, cookies)
            val response = okHttpClient.newCall(request).execute()
            response.use {
                if (response.isSuccessful) {
                    Result.success(response.body?.string() ?: "")
                } else {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun buildRequest(config: SchoolConfig, cookies: String): Request {
        val requestBuilder = Request.Builder()
            .url(config.scheduleUrl)
            .addHeader("Cookie", cookies)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        
        if (config.scheduleMethod == "POST") {
            val formBody = FormBody.Builder()
            config.scheduleParams.forEach { (key, value) ->
                formBody.add(key, value)
            }
            requestBuilder.post(formBody.build())
        }
        
        return requestBuilder.build()
    }
    
    /**
     * 解析课表数据
     */
    fun parseScheduleData(data: String, config: SchoolConfig): List<ParsedCourse> {
        return when (config.dataFormat) {
            com.wind.ggbond.classtime.data.model.DataFormat.JSON -> parseJsonSchedule(data, config)
            com.wind.ggbond.classtime.data.model.DataFormat.HTML -> parseHtmlSchedule(data, config)
            else -> emptyList()
        }
    }
    
    private fun parseJsonSchedule(json: String, config: SchoolConfig): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        try {
            val jsonObject = gson.fromJson(json, JsonObject::class.java)
            val dataArray = jsonObject.getAsJsonArray("data") ?: return emptyList()
            
            dataArray.forEach { element ->
                val courseObj = element.asJsonObject
                try {
                    val course = ParsedCourse(
                        courseName = courseObj.get("courseName")?.asString ?: "",
                        teacher = courseObj.get("teacher")?.asString ?: "",
                        classroom = courseObj.get("classroom")?.asString ?: "",
                        dayOfWeek = courseObj.get("dayOfWeek")?.asInt ?: 1,
                        startSection = courseObj.get("startSection")?.asInt ?: 1,
                        sectionCount = courseObj.get("sectionCount")?.asInt ?: 1,
                        weekExpression = courseObj.get("weeks")?.asString ?: ""
                    )
                    courses.add(course)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "JSON课程解析跳过: ${e.message}")
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "JSON课表解析失败: ${e.message}")
        }
        
        return courses
    }
    
    private fun parseHtmlSchedule(html: String, config: SchoolConfig): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        try {
            val document = Jsoup.parse(html)
            
            val rows = document.select("table tr")
            
            rows.drop(1).forEach { row ->
                val cells = row.select("td")
                if (cells.size >= 4) {
                    try {
                        val dayOfWeek = parseDayOfWeek(cells[3].text())
                        if (dayOfWeek == null) return@forEach
                        val course = ParsedCourse(
                            courseName = cells[0].text().trim(),
                            teacher = cells[1].text().trim(),
                            classroom = cells[2].text().trim(),
                            dayOfWeek = dayOfWeek,
                            startSection = parseSection(cells.getOrNull(4)?.text() ?: "1"),
                            sectionCount = 2,
                            weekExpression = cells.getOrNull(5)?.text() ?: ""
                        )
                        courses.add(course)
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "HTML课程行解析跳过: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "HTML课表解析失败: ${e.message}")
        }
        
        return courses
    }
    
    private fun parseDayOfWeek(text: String): Int? {
        return when {
            text.contains("一") || text.contains("1") -> 1
            text.contains("二") || text.contains("2") -> 2
            text.contains("三") || text.contains("3") -> 3
            text.contains("四") || text.contains("4") -> 4
            text.contains("五") || text.contains("5") -> 5
            text.contains("六") || text.contains("6") -> 6
            text.contains("日") || text.contains("天") || text.contains("7") -> 7
            else -> null
        }
    }
    
    private fun parseSection(text: String): Int {
        return text.filter { it.isDigit() }.toIntOrNull() ?: 1
    }
}



