package com.wind.ggbond.classtime.util.extractor

import android.util.Log
import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 四川科技职业学院课表提取器
 * 系统：金窗教务
 */
@Singleton
class SCKJZYXYExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "sckjzyxy"
    override val schoolName = "四川科技职业学院"
    override val systemType = "jinchuang"
    
    companion object {
        private const val TAG = "SCKJZYXYExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("scstc.cn", ignoreCase = true) ||
               (html.contains("table") && html.contains("tbody"))
    }
    
    override fun getLoginUrl(): String = "http://jwgl.scstc.cn/"
    override fun getScheduleUrl(): String? = null
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取${schoolName}课表...');
                    
                    let tables = document.getElementsByTagName("table");
                    if (tables.length < 2) {
                        return JSON.stringify({error: '未找到课表数据，请确保在课表页面'});
                    }
                    
                    let table = tables[1];
                    
                    console.log('✅ 提取完成');
                    return JSON.stringify({html: table.outerHTML});
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
            Log.d(TAG, "开始解析${schoolName}课程数据...")
            
            val cleanJson = jsonData.trim()
                .removePrefix("\"").removeSuffix("\"")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "")
            
            val jsonObject = JSONObject(cleanJson)
            
            if (jsonObject.has("error")) {
                throw Exception(jsonObject.getString("error"))
            }
            
            val html = jsonObject.getString("html")
            
            // 解析HTML表格
            val rawCourses = parseJinchuangTable(html)
            val resolvedCourses = resolveCourseConflicts(rawCourses)
            
            Log.d(TAG, "✅ 成功解析 ${resolvedCourses.size} 门课程")
            return resolvedCourses
        } catch (e: Exception) {
            Log.e(TAG, "解析课程数据失败", e)
            throw e
        }
    }
    
    /**
     * 解析金窗教务系统的课表HTML
     */
    private fun parseJinchuangTable(html: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        
        // 解析表格行
        val rows = html.split("<tr>", "<tr ", "</tr>").filter { 
            it.contains("tbody") || it.contains("<td")
        }
        
        // 跳过表头，从第2行开始
        for ((rowIndex, row) in rows.drop(1).withIndex()) {
            val cells = row.split("<td", "</td>").filter { 
                it.contains("valign=top") || it.contains("valign=\"top\"")
            }
            
            for ((dayIndex, cell) in cells.withIndex()) {
                val cellContent = cell.split("<br>", "<br/>", "<br />")
                    .map { it.replace(Regex("<[^>]+>"), "").trim() }
                    .filter { it.isNotEmpty() }
                
                // 每5个元素为一组：课程名、节次(带单双)、教师、教室、周次
                var i = 0
                while (i + 4 < cellContent.size) {
                    val courseName = cellContent.getOrNull(i) ?: break
                    if (courseName.isEmpty()) {
                        i++
                        continue
                    }
                    
                    // 节次信息（可能包含单双标记）
                    val sectionText = cellContent.getOrNull(i + 1)?.replace(Regex("[\\(\\)]"), "") ?: ""
                    val tagMatch = Regex("单|双").find(sectionText)
                    
                    val teacher = cellContent.getOrNull(i + 2) ?: ""
                    val classroom = cellContent.getOrNull(i + 3) ?: ""
                    
                    // 周次（如果有单双标记，需要附加）
                    var weekStr = cellContent.getOrNull(i + 4) ?: ""
                    if (tagMatch != null) {
                        weekStr += tagMatch.value
                    }
                    val weeks = parseWeeks(weekStr)
                    
                    // 每个时间段包含2节（大节）
                    val startSection = (rowIndex + 1) * 2 - 1
                    
                    courses.add(ParsedCourse(
                        courseName = courseName.trim(),
                        teacher = teacher.trim(),
                        classroom = classroom.trim(),
                        dayOfWeek = dayIndex + 1,
                        startSection = startSection,
                        sectionCount = 2,
                        weeks = weeks,
                        weekExpression = weeks.joinToString(",") + "周"
                    ))
                    
                    i += 5
                }
            }
        }
        
        return courses
    }
    
    /**
     * 解析周次字符串
     */
    private fun parseWeeks(weekStr: String): List<Int> {
        val weeks = mutableListOf<Int>()
        
        if (weekStr.isEmpty()) return listOf(1)
        
        var cleaned = weekStr.replace(Regex("[(){}|第\\[\\]]"), "")
            .replace("到", "-")
        
        val weekParts = mutableListOf<String>()
        
        while (cleaned.contains("周") || cleaned.contains(Regex("\\s"))) {
            val index = minOf(
                cleaned.indexOf("周").let { if (it >= 0) it else Int.MAX_VALUE },
                cleaned.indexOfFirst { it.isWhitespace() }.let { if (it >= 0) it else Int.MAX_VALUE }
            )
            
            if (index < Int.MAX_VALUE) {
                val nextChar = cleaned.getOrNull(index + 1)
                if (nextChar == '单' || nextChar == '双') {
                    weekParts.add(cleaned.substring(0, index + 2).replace(Regex("[周\\s]"), ""))
                    cleaned = cleaned.substring(index + 2)
                } else {
                    weekParts.add(cleaned.substring(0, index + 1).replace(Regex("[周\\s]"), ""))
                    cleaned = cleaned.substring(index + 1)
                }
                
                val digitIndex = cleaned.indexOfFirst { it.isDigit() }
                cleaned = if (digitIndex >= 0) cleaned.substring(digitIndex) else ""
            } else {
                break
            }
        }
        
        if (cleaned.isNotEmpty()) {
            weekParts.add(cleaned)
        }
        
        for (part in weekParts.filter { it.isNotBlank() }) {
            val isDan = part.endsWith("单")
            val isShuang = part.endsWith("双")
            val cleanPart = part.replace("单", "").replace("双", "")
            
            val ranges = cleanPart.split(",")
            for (range in ranges) {
                val rangeParts = range.split("-")
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
        }
        
        return weeks.distinct().sorted().ifEmpty { listOf(1) }
    }
    
    /**
     * 合并相同课程的不同周次
     */
    private fun resolveCourseConflicts(rawCourses: List<ParsedCourse>): List<ParsedCourse> {
        val courseMap = mutableMapOf<String, ParsedCourse>()
        
        for (course in rawCourses) {
            val key = "${course.courseName}|${course.teacher}|${course.classroom}|${course.dayOfWeek}|${course.startSection}"
            
            if (courseMap.containsKey(key)) {
                val existing = courseMap[key]!!
                val mergedWeeks = (existing.weeks + course.weeks).distinct().sorted()
                courseMap[key] = existing.copy(
                    weeks = mergedWeeks,
                    weekExpression = mergedWeeks.joinToString(",") + "周"
                )
            } else {
                courseMap[key] = course
            }
        }
        
        return courseMap.values.toList()
    }
}


















