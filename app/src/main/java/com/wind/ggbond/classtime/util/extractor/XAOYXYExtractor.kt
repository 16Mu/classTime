package com.wind.ggbond.classtime.util.extractor

import android.util.Log
import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 西安欧亚学院课表提取器
 * 系统：Eurasia系统
 */
@Singleton
class XAOYXYExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "xaoyxy"
    override val schoolName = "西安欧亚学院"
    override val systemType = "eurasia"
    
    companion object {
        private const val TAG = "XAOYXYExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("eurasia.edu", ignoreCase = true) &&
               url.contains("OuterStudWeekOfTimeTable", ignoreCase = true)
    }
    
    override fun getLoginUrl(): String = "https://my.eurasia.edu/"
    override fun getScheduleUrl(): String? = null
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取${schoolName}课表...');
                    
                    // 检查是否在学期课表页面
                    if (document.URL.search('OuterStudWeekOfTimeTable') === -1) {
                        return JSON.stringify({
                            error: '请先定位到学期课表页面。路径：左上角logo -> 课表 -> 学期课表'
                        });
                    }
                    
                    let element = document.getElementById('ContentPlaceHolder1_ucTimetableInWeeks1_tabTimetableInWeek');
                    
                    if (!element) {
                        return JSON.stringify({error: '未找到课表元素，请确保在正确的页面'});
                    }
                    
                    console.log('✅ 提取完成');
                    return JSON.stringify({html: element.outerHTML});
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
            val rawCourses = parseEurasiaTable(html)
            val resolvedCourses = resolveCourseConflicts(rawCourses)
            
            Log.d(TAG, "✅ 成功解析 ${resolvedCourses.size} 门课程")
            return resolvedCourses
        } catch (e: Exception) {
            Log.e(TAG, "解析课程数据失败", e)
            throw e
        }
    }
    
    /**
     * 解析Eurasia系统的课表HTML
     */
    private fun parseEurasiaTable(html: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        
        // 简化的HTML解析
        val rows = html.split("<tr>", "<tr ", "</tr>").filter { it.contains("<td") }
        
        for ((weekIndex, row) in rows.withIndex()) {
            if (weekIndex == 0) continue // 跳过表头
            
            val cells = row.split("<td", "</td>").filter { it.contains(">") }
            
            for ((dayIndex, cell) in cells.withIndex()) {
                if (dayIndex == 0) continue // 跳过第一列
                
                // 查找div元素
                val divs = cell.split("<div", "</div>").filter { it.contains("class") }
                
                for (div in divs) {
                    val divContent = div.split("<br>", "<br/>", "<br />")
                        .map { it.replace(Regex("<[^>]+>"), "").trim() }
                        .filter { it.isNotEmpty() }
                    
                    if (divContent.size < 4) continue
                    
                    // 检查是否是晨读（跳过）
                    if (divContent.getOrNull(0)?.contains("晨读") == true) continue
                    
                    // 第1行：节次信息（如"第1-2节"）
                    val sectionStr = divContent.getOrNull(0)
                        ?.replace(Regex("[第节\\s]"), "") ?: continue
                    val sections = parseSections(sectionStr)
                    
                    // 第2行：课程名
                    val courseName = divContent.getOrNull(1) ?: continue
                    
                    // 第3行：教师
                    val teacher = divContent.getOrNull(2) ?: ""
                    
                    // 第4行：教室
                    val classroom = divContent.getOrNull(3) ?: ""
                    
                    courses.add(ParsedCourse(
                        courseName = courseName.trim(),
                        teacher = teacher.trim(),
                        classroom = classroom.trim(),
                        dayOfWeek = dayIndex,
                        startSection = sections.minOrNull() ?: 1,
                        sectionCount = sections.size,
                        weeks = listOf(weekIndex),
                        weekExpression = "${weekIndex}周"
                    ))
                }
            }
        }
        
        return courses
    }
    
    /**
     * 解析节次字符串
     * 例如：1-2,5 -> [1, 2, 5]
     */
    private fun parseSections(sectionStr: String): List<Int> {
        val sections = mutableListOf<Int>()
        
        val parts = sectionStr.split(",", "-")
        
        // 如果是范围格式（如1-2）
        if (sectionStr.contains("-")) {
            val rangeParts = sectionStr.split("-")
            val start = rangeParts.getOrNull(0)?.toIntOrNull() ?: 1
            val end = rangeParts.getOrNull(1)?.toIntOrNull() ?: start
            
            for (section in start..end) {
                sections.add(section)
            }
        } else {
            // 逗号分隔的格式
            for (part in parts) {
                part.toIntOrNull()?.let { sections.add(it) }
            }
        }
        
        return sections.distinct().sorted()
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


















