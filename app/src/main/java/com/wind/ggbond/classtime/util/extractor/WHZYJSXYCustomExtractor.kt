package com.wind.ggbond.classtime.util.extractor

import android.util.Log
import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 武汉职业技术学院-自研版课表提取器
 * 系统：自研教务系统
 * 注意：与树维版本的WHZYJSXYExtractor不同
 */
@Singleton
class WHZYJSXYCustomExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "whzyjsxy_custom"
    override val schoolName = "武汉职业技术学院(自研)"
    override val systemType = "custom"
    
    companion object {
        private const val TAG = "WHZYJSXYCustomExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("wtc.edu.cn", ignoreCase = true) &&
               url.contains("M1402", ignoreCase = true) &&
               html.contains("queryKbForXsd")
    }
    
    override fun getLoginUrl(): String = "http://zxxt.wtc.edu.cn/"
    override fun getScheduleUrl(): String? = null
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取${schoolName}课表...');
                    
                    // 检查是否在课表页面
                    if (location.href.search('M1402') == -1) {
                        return JSON.stringify({
                            error: '请先进入课表页面。路径：信息查询 -> 我的课表'
                        });
                    }
                    
                    let html = '';
                    let iframes = document.getElementsByTagName('iframe');
                    
                    for (let ifr of iframes) {
                        if (ifr.src.search('queryKbForXsd') !== -1) {
                            try {
                                let iframeDoc = ifr.contentDocument;
                                let table = iframeDoc.getElementsByTagName('table')[0];
                                html = table.outerHTML;
                                console.log('✅ 提取完成');
                                break;
                            } catch (e) {
                                console.error('无法访问iframe:', e);
                            }
                        }
                    }
                    
                    if (!html) {
                        return JSON.stringify({error: '未找到课表数据，请确保在课表页面'});
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
            Log.d(TAG, "开始解析${schoolName}课程数据...")
            
            val cleanJson = jsonData.trim()
                .removePrefix("\"").removeSuffix("\"")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "")
                .replace("\\t", "")
            
            val jsonObject = JSONObject(cleanJson)
            
            if (jsonObject.has("error")) {
                throw Exception(jsonObject.getString("error"))
            }
            
            val html = jsonObject.getString("html")
            
            // 解析HTML表格
            val rawCourses = parseTableHtml(html)
            val resolvedCourses = resolveCourseConflicts(rawCourses)
            
            Log.d(TAG, "✅ 成功解析 ${resolvedCourses.size} 门课程")
            return resolvedCourses
        } catch (e: Exception) {
            Log.e(TAG, "解析课程数据失败", e)
            throw e
        }
    }
    
    /**
     * 解析HTML表格提取课程信息
     */
    private fun parseTableHtml(html: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        
        // 简单的HTML解析（实际应该使用HTML解析库）
        val rows = html.split("<tr>", "<tr ", "</tr>").filter { it.contains("<td") }
        
        for (row in rows) {
            val cells = row.split("<td", "</td>").filter { it.contains("class=\"cell\"") }
            
            for ((dayIndex, cell) in cells.withIndex()) {
                val cellContent = cell.replace(Regex("<[^>]+>"), " ")
                    .replace("&nbsp;", " ")
                    .trim()
                
                if (cellContent.length <= 6) continue
                
                // 提取cell id和rowspan
                val idMatch = Regex("id=\"Cell(\\d+)\"").find(cell)
                val cellId = idMatch?.groupValues?.getOrNull(1) ?: continue
                
                val rowspanMatch = Regex("rowspan=\"(\\d+)\"").find(cell)
                val rowspan = rowspanMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
                
                // 解析cellId获取day和startSection
                val day = cellId.firstOrNull()?.toString()?.toIntOrNull() ?: 1
                val startSection = cellId.drop(1).toIntOrNull() ?: 1
                
                // 按双<br>分割多个课程
                val courseBlocks = cell.split(Regex("<br>\\s*<br>|<br/>\\s*<br/>"))
                
                for (block in courseBlocks) {
                    // 提取课程名、教师、教室
                    val kcMatch = Regex("openKckb[^>]*>([^<]+)").find(block)
                    val courseName = kcMatch?.groupValues?.getOrNull(1)?.trim() ?: ""
                    
                    if (courseName.isEmpty()) continue
                    
                    val jsMatch = Regex("openJskb[^>]*>([^<]+)").find(block)
                    val teacher = jsMatch?.groupValues?.getOrNull(1)?.trim() ?: ""
                    
                    val crMatch = Regex("openCrkb[^>]*>([^<]+)").find(block)
                    val classroom = crMatch?.groupValues?.getOrNull(1)?.trim() ?: ""
                    
                    // 提取周次
                    val weekMatch = Regex("[>)]\\s*([^<]+?周)").find(block)
                    val weekStr = weekMatch?.groupValues?.getOrNull(1) ?: ""
                    val weeks = parseWeeks(weekStr)
                    
                    val sections = (0 until rowspan).map { startSection + it }
                    
                    courses.add(ParsedCourse(
                        courseName = courseName,
                        teacher = teacher,
                        classroom = classroom,
                        dayOfWeek = day,
                        startSection = sections.firstOrNull() ?: 1,
                        sectionCount = sections.size,
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
     */
    private fun parseWeeks(weekStr: String): List<Int> {
        val weeks = mutableListOf<Int>()
        
        val cleaned = weekStr.replace(Regex("[(){}|第\\[\\]]"), "").replace("到", "-")
        
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


















