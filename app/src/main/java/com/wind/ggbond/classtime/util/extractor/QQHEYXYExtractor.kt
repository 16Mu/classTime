package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

/**
 * 齐齐哈尔医学院课表提取器
 * 系统：自研教务系统
 */
@Singleton
class QQHEYXYExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "qqheyxy"
    override val schoolName = "齐齐哈尔医学院"
    override val systemType = "custom"

    override val aliases = listOf("齐齐哈尔医学院")
    override val supportedUrls = listOf("qmu.edu.cn")
    
    companion object {
        private const val TAG = "QQHEYXYExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("qmu.edu.cn", ignoreCase = true) &&
               html.contains("rightFrame")
    }
    
    override fun getLoginUrl(): String = "http://jwgl.qmu.edu.cn/"
    override fun getScheduleUrl(): String? = null
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取${schoolName}课表...');
                    
                    let dom = window.frames["rightFrame"].document;
                    let tables = dom.getElementsByTagName("table");
                    let table = tables[tables.length - 1];
                    
                    if (!table) {
                        return JSON.stringify({error: '未找到课表数据'});
                    }
                    
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
            AppLogger.d(TAG, "开始解析${schoolName}课程数据...")
            
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
            val rawCourses = parseScheduleTable(html)
            val resolvedCourses = resolveCourseConflicts(rawCourses)
            
            AppLogger.d(TAG, "✅ 成功解析 ${resolvedCourses.size} 门课程")
            return resolvedCourses
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析课程数据失败", e)
            throw e
        }
    }
    
    /**
     * 解析课表HTML表格
     */
    private fun parseScheduleTable(html: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        
        // 解析表格行
        val rows = html.split("<tr>", "<tr ", "</tr>").filter { it.contains("<td") }
        
        // 从第4行开始（跳过表头）
        val dataRows = rows.drop(3).dropLast(1)
        
        for ((rowIndex, row) in dataRows.withIndex()) {
            val cells = row.split("<td", "</td>")
                .filter { it.contains(">") }
                .drop(1) // 跳过第一列
            
            for ((dayIndex, cell) in cells.withIndex()) {
                val cellText = cell.replace(Regex("<[^>]+>"), "\n")
                    .replace("&nbsp;", " ")
                    .trim()
                
                if (cellText.length <= 1) continue
                
                // 按<br>分割课程信息
                val parts = cellText.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                
                // 每4个元素为一组：课程名、教师、教室、周次
                var i = 0
                while (i + 3 < parts.size) {
                    val courseName = parts[i]
                    val teacher = parts[i + 1]
                    val classroom = parts[i + 2]
                    val weekStr = parts[i + 3].split(Regex("\\s"))[0] // 取第一个部分（周次）
                    
                    val weeks = parseWeeks(weekStr)
                    
                    // 每个大节包含2个小节
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
                    
                    i += 4
                }
            }
        }
        
        return courses
    }
    
    /**
     * 解析周次字符串
     * 支持格式：1-6,7-13周(单), 1.2.3.4周 等
     */
    private fun parseWeeks(weekStr: String): List<Int> {
        val weeks = mutableListOf<Int>()
        
        if (weekStr.isEmpty()) return listOf(1)
        
        val cleaned = weekStr.replace(Regex("[(){}|第到]"), "")
            .replace(".", ",")
        
        val parts = cleaned.split(Regex("周|\\s")).filter { it.isNotBlank() }
        
        for (part in parts) {
            val isDan = part.contains("单")
            val isShuang = part.contains("双")
            val rangeStr = part.replace("单", "").replace("双", "").trim()
            
            if (rangeStr.isEmpty()) continue
            
            val rangeParts = rangeStr.split(",", "-")
            
            for (rangePart in rangeParts) {
                val num = rangePart.toIntOrNull() ?: continue
                
                when {
                    isDan && num % 2 == 1 -> weeks.add(num)
                    isShuang && num % 2 == 0 -> weeks.add(num)
                    !isDan && !isShuang -> weeks.add(num)
                }
            }
            
            // 如果是范围格式（如1-16）
            if (rangeParts.size == 2) {
                weeks.clear()
                val start = rangeParts[0].toIntOrNull() ?: continue
                val end = rangeParts[1].toIntOrNull() ?: continue
                
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
     * 合并相同课程
     */
    private fun resolveCourseConflicts(rawCourses: List<ParsedCourse>): List<ParsedCourse> {
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
        
        return courseMap.values.toList()
    }
}


















