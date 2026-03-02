package com.wind.ggbond.classtime.util.extractor

import android.util.Log
import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 泉州师范学院课表提取器
 * 系统：自研教务系统
 */
@Singleton
class QZSFXYExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "qzsfxy"
    override val schoolName = "泉州师范学院"
    override val systemType = "custom"
    
    companion object {
        private const val TAG = "QZSFXYExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("qztc.edu.cn", ignoreCase = true) &&
               (url.contains("cx_kb_bjkb_bj") || html.contains("班级课表"))
    }
    
    override fun getLoginUrl(): String = "http://jwgl.qztc.edu.cn/"
    override fun getScheduleUrl(): String? = null
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取${schoolName}课表...');
                    
                    // 检查是否在班级课表页面
                    if (location.href.search('cx_kb_bjkb_bj') == -1) {
                        return JSON.stringify({
                            error: '请在班级课表页面进行导入'
                        });
                    }
                    
                    let tables = document.getElementsByTagName("table");
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
            val rawCourses = parseClassTable(html)
            val resolvedCourses = resolveCourseConflicts(rawCourses)
            
            Log.d(TAG, "✅ 成功解析 ${resolvedCourses.size} 门课程")
            return resolvedCourses
        } catch (e: Exception) {
            Log.e(TAG, "解析课程数据失败", e)
            throw e
        }
    }
    
    /**
     * 解析班级课表HTML
     */
    private fun parseClassTable(html: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        
        // 解析表格
        val rows = html.split("<tr", "</tr>").filter { it.contains("class=\"dg1-item\"") }
        
        for ((sectionIndex, row) in rows.withIndex()) {
            val cells = row.split("<td", "</td>")
                .filter { it.contains(">") }
                .drop(1) // 跳过第一列（节次列）
            
            for ((dayIndex, cell) in cells.withIndex()) {
                val cellText = cell.replace(Regex("<[^>]+>"), "")
                    .replace("&nbsp;", " ")
                    .trim()
                
                if (cellText.isEmpty()) continue
                
                // 按 / 分割多个课程
                val courseParts = cellText.split("/").filter { it.trim().isNotEmpty() }
                
                for (coursePart in courseParts) {
                    val parts = coursePart.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    
                    if (parts.isEmpty()) continue
                    
                    var partIndex = 0
                    val courseName = parts.getOrNull(partIndex) ?: continue
                    partIndex++
                    
                    var teacher = ""
                    var classroom = ""
                    var weekStr = ""
                    
                    // 解析剩余部分
                    while (partIndex < parts.size) {
                        val part = parts[partIndex]
                        
                        when {
                            // 如果包含数字和中文（如1-16周），是周次
                            part.contains(Regex("\\d")) && (part.contains("周") || part.contains("单") || part.contains("双")) -> {
                                weekStr = part
                                // 检查下一个是否也是周次的一部分
                                if (partIndex + 1 < parts.size) {
                                    val next = parts[partIndex + 1]
                                    if (next.contains("单") || next.contains("双")) {
                                        weekStr += next
                                        partIndex++
                                    }
                                }
                            }
                            // 包含数字的很可能是教室
                            part.contains(Regex("\\d+")) -> classroom = part
                            // 否则是教师
                            teacher.isEmpty() -> teacher = part
                            else -> {}
                        }
                        
                        partIndex++
                    }
                    
                    val weeks = parseWeeks(weekStr)
                    
                    courses.add(ParsedCourse(
                        courseName = courseName.trim(),
                        teacher = teacher.trim(),
                        classroom = classroom.trim(),
                        dayOfWeek = dayIndex + 1,
                        startSection = sectionIndex + 1,
                        sectionCount = 1,
                        weeks = weeks,
                        weekExpression = weeks.joinToString(",") + "周"
                    ))
                }
            }
        }
        
        return courses
    }
    
    /**
     * 解析周次字符串
     * 支持格式：1-6,7-13周(单), 1-16周, 1 2 3 4周 等
     */
    private fun parseWeeks(weekStr: String): List<Int> {
        val weeks = mutableListOf<Int>()
        
        if (weekStr.isEmpty()) return listOf(1)
        
        val cleaned = weekStr.replace(Regex("[(){}|第\\[\\]]"), "")
            .replace("到", "-")
        
        val parts = cleaned.split(Regex("周|\\s")).filter { it.isNotBlank() }
        
        for (part in parts) {
            val isDan = part.contains("单")
            val isShuang = part.contains("双")
            val rangeStr = part.replace("单", "").replace("双", "").trim()
            
            if (rangeStr.isEmpty()) continue
            
            val rangeParts = rangeStr.split("-", ",")
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


















