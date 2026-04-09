package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

/**
 * 河南中医药大学-研究生课表提取器
 * 系统：自研教务系统
 */
@Singleton
class HNZYYDXYJSExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "hnzyydx_yjs"
    override val schoolName = "河南中医药大学(研究生)"
    override val systemType = "custom"
    
    companion object {
        private const val TAG = "HNZYYDXYJSExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("hactcm.edu.cn", ignoreCase = true) &&
               (html.contains("rightFrame") || html.contains("mainFrame"))
    }
    
    override fun getLoginUrl(): String = "http://yjsy.hactcm.edu.cn/"
    override fun getScheduleUrl(): String? = null
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取${schoolName}课表...');
                    
                    let table;
                    try {
                        let rightFrame = window.frames['rightFrame'];
                        if (rightFrame) {
                            let doc = rightFrame.document;
                            let tables = doc.getElementsByTagName('table');
                            table = tables[tables.length - 1];
                        }
                    } catch (e) {
                        console.error('无法访问frame:', e);
                        return JSON.stringify({error: '无法访问课表页面，请确保在正确的页面'});
                    }
                    
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
            val rawCourses = parseGraduateTable(html)
            val resolvedCourses = resolveCourseConflicts(rawCourses)
            
            AppLogger.d(TAG, "✅ 成功解析 ${resolvedCourses.size} 门课程")
            return resolvedCourses
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析课程数据失败", e)
            throw e
        }
    }
    
    /**
     * 解析研究生课表HTML
     */
    private fun parseGraduateTable(html: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        
        // 解析表格行
        val rows = html.split("<tr>", "<tr ", "</tr>").filter { it.contains("<td") }
        
        for ((rowIndex, row) in rows.withIndex()) {
            if (rowIndex < 3) continue // 跳过表头
            
            val cells = row.split("<td", "</td>").filter { 
                it.contains(">") && !it.contains("align=\"center\"")
            }
            
            for ((dayIndex, cell) in cells.withIndex()) {
                if (dayIndex == 0) continue // 跳过第一列
                
                val cellContent = cell.replace(Regex("<[^>]+>"), " ")
                    .replace("&nbsp;", " ")
                    .trim()
                
                if (cellContent.length <= 1) continue
                
                // 按<br><br>分割多个课程
                val courseBlocks = cell.split(Regex("<br>\\s*<br>|<br/>\\s*<br/>"))
                
                for (block in courseBlocks) {
                    val parts = block.split("<br>", "<br/>", "<br />")
                        .map { it.replace(Regex("<[^>]+>"), "").trim() }
                        .filter { it.isNotEmpty() }
                    
                    if (parts.isEmpty()) continue
                    
                    var partIndex = 0
                    val courseName = parts.getOrNull(partIndex) ?: continue
                    partIndex++
                    
                    if (courseName.startsWith("课程:")) {
                        // 新格式：课程: xxx, 教室: xxx, 周次: xxx, 教师: xxx
                        var classroom = ""
                        var weekStr = ""
                        var teacher = ""
                        
                        for (part in parts) {
                            when {
                                part.startsWith("课程:") -> {}
                                part.contains("教室") || part.contains("室") -> classroom = part
                                part.contains("周") -> weekStr = part
                                part.startsWith("主讲教师:") -> teacher = part.removePrefix("主讲教师:")
                            }
                        }
                        
                        val weeks = parseWeeks(weekStr)
                        
                        // 计算节次（每个单元格是一个大节，包含2小节）
                        val startSection = (rowIndex - 2) * 2 - 1
                        val rowspanMatch = Regex("rowspan=\"(\\d+)\"").find(cell)
                        val rowspan = rowspanMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
                        val sectionCount = rowspan * 2
                        
                        courses.add(ParsedCourse(
                            courseName = courseName.removePrefix("课程:").trim(),
                            teacher = teacher.trim(),
                            classroom = classroom.trim(),
                            dayOfWeek = dayIndex,
                            startSection = startSection,
                            sectionCount = sectionCount,
                            weeks = weeks,
                            weekExpression = weeks.joinToString(",") + "周"
                        ))
                    }
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
        
        val cleaned = weekStr.replace(Regex("[(){}|第周\\[\\]]"), "")
            .replace("到", "-")
        
        val parts = cleaned.split(Regex("[,、]")).filter { it.isNotBlank() }
        
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


















