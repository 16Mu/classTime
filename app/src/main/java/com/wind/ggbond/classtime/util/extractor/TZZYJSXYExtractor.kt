package com.wind.ggbond.classtime.util.extractor

import android.util.Log
import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 泰州职业技术学院课表提取器
 * 系统：自研教务系统
 */
@Singleton
class TZZYJSXYExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "tzzyjsxy"
    override val schoolName = "泰州职业技术学院"
    override val systemType = "custom"
    
    companion object {
        private const val TAG = "TZZYJSXYExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("tzpc.edu.cn", ignoreCase = true) &&
               (html.contains("ContentPanel1_DataGrid1") || html.contains("rightFrame"))
    }
    
    override fun getLoginUrl(): String = "http://jwgl.tzpc.edu.cn/"
    override fun getScheduleUrl(): String? = null
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取${schoolName}课表...');
                    
                    let table;
                    try {
                        let frame = window.frames['fnode24'];
                        if (frame) {
                            let doc = frame.document;
                            table = doc.getElementById("ContentPanel1_DataGrid1");
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
            val rawCourses = parseScheduleTable(html)
            val resolvedCourses = resolveCourseConflicts(rawCourses)
            
            Log.d(TAG, "✅ 成功解析 ${resolvedCourses.size} 门课程")
            return resolvedCourses
        } catch (e: Exception) {
            Log.e(TAG, "解析课程数据失败", e)
            throw e
        }
    }
    
    /**
     * 解析课表HTML
     */
    private fun parseScheduleTable(html: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        
        // 解析表格行
        val rows = html.split("<tr>", "<tr ", "</tr>").filter { it.contains("<td") }
        
        for (row in rows.drop(1)) { // 跳过表头
            val cells = row.split("<td", "</td>")
                .filter { it.contains("align") && !it.contains("align=\"center\"") }
            
            for ((dayIndex, cell) in cells.withIndex()) {
                // 检查rowspan
                val rowspanMatch = Regex("rowspan=\"(\\d+)\"").find(cell)
                val rowspan = rowspanMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
                
                // 获取单元格起始节次（通过查找它在哪一行）
                val startSection = calculateStartSection(row, rows)
                
                val cellContent = cell.replace(Regex("<[^>]+>"), "\n")
                    .replace("&nbsp;", " ")
                    .trim()
                
                if (cellContent.isEmpty()) continue
                
                // 按<br><br>分割多个课程
                val courseBlocks = cellContent.split(Regex("\n{2,}"))
                
                for (block in courseBlocks) {
                    val parts = block.split("\n")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    
                    if (parts.size < 4) continue
                    
                    var partIndex = 0
                    
                    // 课程名
                    val courseName = parts.getOrNull(partIndex)?.removePrefix("课程:") ?: continue
                    partIndex++
                    
                    // 教室
                    val classroom = parts.getOrNull(partIndex) ?: ""
                    partIndex++
                    
                    // 周次
                    val weekStr = parts.getOrNull(partIndex) ?: ""
                    val weeks = parseWeeks(weekStr)
                    partIndex++
                    
                    // 教师
                    val teacher = parts.getOrNull(partIndex)?.removePrefix("主讲教师:") ?: ""
                    
                    courses.add(ParsedCourse(
                        courseName = courseName.trim(),
                        teacher = teacher.trim(),
                        classroom = classroom.trim(),
                        dayOfWeek = dayIndex + 1,
                        startSection = startSection,
                        sectionCount = rowspan,
                        weeks = weeks,
                        weekExpression = weeks.joinToString(",") + "周"
                    ))
                }
            }
        }
        
        return courses
    }
    
    /**
     * 计算起始节次（简化版）
     */
    private fun calculateStartSection(currentRow: String, allRows: List<String>): Int {
        val index = allRows.indexOf(currentRow)
        return if (index > 0) index else 1
    }
    
    /**
     * 解析周次字符串
     */
    private fun parseWeeks(weekStr: String): List<Int> {
        val weeks = mutableListOf<Int>()
        
        if (weekStr.isEmpty()) return listOf(1)
        
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


















