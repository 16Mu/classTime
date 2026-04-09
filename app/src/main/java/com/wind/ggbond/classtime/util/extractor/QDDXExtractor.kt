package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

/**
 * 青岛大学课表提取器
 * 系统：自研教务系统
 */
@Singleton
class QDDXExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "qddx"
    override val schoolName = "青岛大学"
    override val systemType = "custom"
    
    companion object {
        private const val TAG = "QDDXExtractor"
        
        // 星期映射
        private val WEEK_DAY_MAP = mapOf(
            "一" to 1, "二" to 2, "三" to 3, "四" to 4,
            "五" to 5, "六" to 6, "七" to 7, "日" to 7
        )
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("qdu.edu.cn", ignoreCase = true) &&
               (html.contains("infolist_tab") || html.contains("mainFrame"))
    }
    
    override fun getLoginUrl(): String = "http://jw.qdu.edu.cn/"
    override fun getScheduleUrl(): String? = null
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取${schoolName}课表...');
                    
                    let result = {};
                    let frames = window.frames;
                    
                    if (frames.length != 0) {
                        try {
                            let dom = frames[0].frames['mainFrame'].document;
                            result.course = dom.getElementsByClassName("infolist_tab")[0].outerHTML;
                            result.time = dom.getElementsByClassName("infolist_tab")[1].outerHTML;
                            result.tag = "LIST";
                        } catch(e) {
                            return JSON.stringify({
                                error: '遇到错误，可能未处于课表页面：' + e.message
                            });
                        }
                    } else {
                        result.tag = location.href.split("=").pop();
                        result.course = document.getElementsByClassName("content_tab")[0].outerHTML;
                    }
                    
                    if (result.tag == "COMBINE") {
                        return JSON.stringify({error: '当前页面暂未适配，请切换其他课表页面'});
                    }
                    
                    console.log('✅ 提取完成, tag:', result.tag);
                    return JSON.stringify(result);
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
            
            val providerJSON = JSONObject(cleanJson)
            
            if (providerJSON.has("error")) {
                throw Exception(providerJSON.getString("error"))
            }
            
            val tag = providerJSON.getString("tag")
            val courseHtml = providerJSON.getString("course")
            
            when (tag) {
                "LIST" -> {
                    val timeHtml = providerJSON.getString("time")
                    courses.addAll(parseListMode(courseHtml, timeHtml))
                }
                "BASE" -> {
                    courses.addAll(parseBaseMode(courseHtml))
                }
                else -> {
                    throw Exception("未知的页面类型: $tag")
                }
            }
            
            val resolvedCourses = resolveCourseConflicts(courses)
            
            AppLogger.d(TAG, "✅ 成功解析 ${resolvedCourses.size} 门课程")
            return resolvedCourses
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析课程数据失败", e)
            throw e
        }
    }
    
    /**
     * 解析LIST模式（列表模式）
     */
    private fun parseListMode(courseHtml: String, timeHtml: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        
        // 解析节次映射表
        val sectionMap = parseSectionMap(timeHtml)
        
        // 解析课程（简化版HTML解析）
        val courseRows = courseHtml.split("<tr", "</tr>").filter { it.contains("infolist_common") }
        
        for (row in courseRows) {
            val cells = row.split("<td", "</td>").filter { it.contains(">") }
            
            if (cells.size < 10) continue
            
            // 提取课程名称（第3列）
            val courseName = extractText(cells.getOrNull(2) ?: "")
            if (courseName.isEmpty()) continue
            
            // 提取教师（第4列）
            val teacher = extractText(cells.getOrNull(3) ?: "")
                .replace(Regex("<a.*?>|</a>"), "")
                .replace("<br>", " ")
            
            // 解析课程时间和地点（第10列的table）
            val courseTimePosCell = cells.getOrNull(9) ?: ""
            val timePosTables = courseTimePosCell.split("<tr>", "</tr>")
                .filter { it.contains("<td") }
            
            for (timeRow in timePosTables) {
                val timeCells = timeRow.split("<td", "</td>").filter { it.contains(">") }
                if (timeCells.size < 4) continue
                
                // 周次
                val weekStr = extractText(timeCells.getOrNull(0) ?: "")
                val weeks = parseWeeks(weekStr)
                
                // 星期
                val dayStr = extractText(timeCells.getOrNull(1) ?: "")
                val dayOfWeek = getWeekDay(dayStr.takeLast(1))
                
                // 节次
                val sectionKey = extractText(timeCells.getOrNull(2) ?: "")
                val sections = sectionMap[sectionKey] ?: listOf(1)
                
                // 教室
                val classroom = extractText(timeCells.getOrNull(3) ?: "")
                
                courses.add(ParsedCourse(
                    courseName = courseName.trim(),
                    teacher = teacher.trim(),
                    classroom = classroom.trim(),
                    dayOfWeek = dayOfWeek,
                    startSection = sections.firstOrNull() ?: 1,
                    sectionCount = sections.size,
                    weeks = weeks,
                    weekExpression = weeks.joinToString(",") + "周"
                ))
            }
        }
        
        return courses
    }
    
    /**
     * 解析BASE模式（网格模式）
     */
    private fun parseBaseMode(courseHtml: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        
        // 简化的BASE模式解析
        // 实际需要根据具体HTML结构实现
        
        return courses
    }
    
    /**
     * 解析节次映射表
     */
    private fun parseSectionMap(timeHtml: String): Map<String, List<Int>> {
        val sectionMap = mutableMapOf<String, List<Int>>()
        
        val rows = timeHtml.split("<tr", "</tr>").filter { it.contains("infolist_common") }
        
        for (row in rows) {
            val cells = row.split("<td", "</td>").filter { it.contains(">") }
            
            if (cells.size < 3) continue
            
            val key = extractText(cells.getOrNull(1) ?: "")
            val sectionsText = extractText(cells.getOrNull(2) ?: "")
                .replace(Regex("[第节]"), "")
            
            val sections = sectionsText.split(Regex("\\s+"))
                .mapNotNull { it.toIntOrNull() }
            
            sectionMap[key] = sections
        }
        
        return sectionMap
    }
    
    /**
     * 提取HTML中的文本
     */
    private fun extractText(html: String): String {
        return html.replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .trim()
    }
    
    /**
     * 获取星期对应的数字
     */
    private fun getWeekDay(dayStr: String): Int {
        return WEEK_DAY_MAP[dayStr] ?: 1
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


















