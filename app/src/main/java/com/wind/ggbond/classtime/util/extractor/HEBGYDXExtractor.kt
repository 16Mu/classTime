package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

/**
 * 哈尔滨工业大学-本部课表提取器
 * 系统：自研教务系统
 */
@Singleton
class HEBGYDXExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "hebgydx"
    override val schoolName = "哈尔滨工业大学"
    override val systemType = "custom"

    override val aliases = listOf("哈尔滨工业大学", "哈工大")
    override val supportedUrls = listOf("hit.edu.cn", "jwts.hit.edu.cn")
    
    companion object {
        private const val TAG = "HEBGYDXExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("hit.edu.cn", ignoreCase = true) &&
               html.contains("iframe")
    }
    
    override fun getLoginUrl(): String = "http://jwts.hit.edu.cn/"
    override fun getScheduleUrl(): String? = null
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取${schoolName}课表...');
                    
                    let html = '';
                    try {
                        let doms = document.getElementsByTagName('iframe')[0].contentDocument;
                        let table = doms.getElementsByTagName('table')[1];
                        html = table.outerHTML;
                        console.log('✅ 提取完成');
                    } catch (e) {
                        console.error('提取失败:', e);
                        return JSON.stringify({error: '无法访问iframe内容: ' + e.message});
                    }
                    
                    return JSON.stringify({html: html});
                } catch (error) {
                    console.error('❌ 提取失败:', error);
                    return JSON.stringify({error: '提取失败: ' + error.message});
                }
            })();
        """.trimIndent()
    }
    
    override fun parseCourses(jsonData: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        try {
            AppLogger.d(TAG, "开始解析${schoolName}课程数据...")
            
            val cleanJson = jsonData.trim()
                .removePrefix("\"").removeSuffix("\"")
                .replace("\\\"", "\"")
                .replace("\\n", "")
                .replace("\\r", "")
            
            val jsonObject = JSONObject(cleanJson)
            
            if (jsonObject.has("error")) {
                throw Exception(jsonObject.getString("error"))
            }
            
            val html = jsonObject.getString("html")
            
            // 解析HTML表格
            val rawCourses = parseHtmlTable(html)
            courses.addAll(resolveCourseConflicts(rawCourses))
            
            AppLogger.d(TAG, "✅ 成功解析 ${courses.size} 门课程")
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析课程数据失败", e)
            throw e
        }
        return courses
    }
    
    private fun parseHtmlTable(html: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        
        // 简单的HTML表格解析逻辑
        // 实际应用中需要更完整的HTML解析
        val rows = html.split("<tr", "</tr>")
        
        for ((jcIndex, row) in rows.withIndex()) {
            if (jcIndex == 0) continue // 跳过表头
            
            val cells = row.split("<td", "</td>")
            for ((dayIndex, cell) in cells.withIndex()) {
                if (dayIndex < 2) continue // 跳过前两列
                
                val text = cell.replace(Regex("<[^>]+>"), "").trim()
                if (text.length <= 6) continue
                
                // 解析课程信息（按<br>分割）
                val parts = text.split("<br>", "<br/>", "<br />")
                    .map { it.replace(Regex("<[^>]+>"), "").trim() }
                    .filter { it.isNotEmpty() }
                
                if (parts.isEmpty()) continue
                
                var i = 0
                while (i < parts.size) {
                    val courseName = parts.getOrNull(i) ?: break
                    i++
                    
                    // 解析周次和教师
                    val weekInfo = parts.getOrNull(i) ?: break
                    val weekMatch = Regex("\\[.*?\\]").find(weekInfo)
                    val weeks = if (weekMatch != null) {
                        parseWeeks(weekMatch.value)
                    } else {
                        listOf(1)
                    }
                    
                    val teacher = weekInfo.split("[")[0].trim()
                    i++
                    
                    // 可能有教室信息
                    val classroom = parts.getOrNull(i)?.takeIf { 
                        it.endsWith("楼") || it.endsWith("室") || it.contains(Regex("\\d{2,}"))
                    } ?: ""
                    
                    if (classroom.isNotEmpty()) i++
                    
                    courses.add(ParsedCourse(
                        courseName = courseName,
                        teacher = teacher,
                        classroom = classroom,
                        dayOfWeek = dayIndex - 1,
                        startSection = (jcIndex) * 2 - 1,
                        sectionCount = 2,
                        weeks = weeks,
                        weekExpression = weeks.joinToString(",") + "周"
                    ))
                }
            }
        }
        
        return courses
    }
    
    /**
     * 解析周次字符串，支持单双周
     * 例如: [1-6,7-13周(单)] -> [1,3,5,7,9,11,13]
     */
    private fun parseWeeks(weekStr: String): List<Int> {
        val weeks = mutableListOf<Int>()
        val cleaned = weekStr.replace(Regex("[\\[\\](){}第到]"), "").replace("到", "-")
        
        // 按周或空格分割
        val parts = cleaned.split(Regex("周|\\s|,")).filter { it.isNotBlank() }
        
        for (part in parts) {
            val isDan = part.contains("单")
            val isShuang = part.contains("双")
            val rangeStr = part.replace("单", "").replace("双", "").trim()
            
            if (rangeStr.isEmpty()) continue
            
            val rangeParts = rangeStr.split("-")
            val start = rangeParts.getOrNull(0)?.toIntOrNull() ?: continue
            val end = rangeParts.getOrNull(1)?.toIntOrNull() ?: start
            
            for (week in start..end) {
                when {
                    isDan && week % 2 == 1 -> weeks.add(week)
                    isShuang && week % 2 == 0 -> weeks.add(week)
                    !isDan && !isShuang -> weeks.add(week)
                }
            }
        }
        
        return weeks.distinct().sorted()
    }
    
    /**
     * 合并相同课程
     */
    private fun resolveCourseConflicts(rawCourses: List<ParsedCourse>): List<ParsedCourse> {
        val result = mutableListOf<ParsedCourse>()
        val courseMap = mutableMapOf<String, ParsedCourse>()
        
        for (course in rawCourses) {
            val key = "${course.courseName}|${course.teacher}|${course.classroom}|${course.dayOfWeek}|${course.startSection}"
            
            if (courseMap.containsKey(key)) {
                val existing = courseMap.getValue(key)
                val mergedWeeks = (existing.weeks + course.weeks).distinct().sorted()
                courseMap[key] = existing.copy(
                    weeks = mergedWeeks,
                    weekExpression = mergedWeeks.joinToString(",") + "周"
                )
            } else {
                courseMap[key] = course
            }
        }
        
        result.addAll(courseMap.values)
        return result
    }
}



















