package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

/**
 * 长春师范大学课表提取器
 * 系统：教务管理系统V6.0
 */
@Singleton
class CCSFDXExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "ccsfdx"
    override val schoolName = "长春师范大学"
    override val systemType = "jwxtv6"

    override val aliases = listOf("长春师范大学")
    override val supportedUrls = listOf("ccsfu.edu.cn")
    
    companion object {
        private const val TAG = "CCSFDXExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("ccsfu.edu.cn", ignoreCase = true) &&
               html.contains("TableLCRoomOccupy")
    }
    
    override fun getLoginUrl(): String = "http://jwgl.ccsfu.edu.cn/"
    override fun getScheduleUrl(): String? = null
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取${schoolName}课表...');
                    
                    // 从iframe中提取form
                    let page = document.getElementsByClassName("page unitBox");
                    let str = "";
                    
                    Array.from(page).forEach(v => {
                        if (v.style.display == "block") {
                            try {
                                let iframe = v.getElementsByTagName("iframe")[0];
                                if (iframe && iframe.contentWindow) {
                                    let form = iframe.contentWindow.document.getElementById("form1");
                                    if (form) {
                                        str = form.outerHTML;
                                    }
                                }
                            } catch (e) {
                                console.error('无法访问iframe:', e);
                            }
                        }
                    });
                    
                    if (!str) {
                        return JSON.stringify({error: '未找到课表数据，请确保在查询本学期课表页面'});
                    }
                    
                    console.log('✅ 提取完成');
                    return JSON.stringify({html: str});
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
            val rawCourses = parseV6Table(html)
            val resolvedCourses = resolveCourseConflicts(rawCourses)
            
            AppLogger.d(TAG, "✅ 成功解析 ${resolvedCourses.size} 门课程")
            return resolvedCourses
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析课程数据失败", e)
            throw e
        }
    }
    
    /**
     * 解析V6.0系统的课表HTML
     */
    private fun parseV6Table(html: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        
        // 解析script标签中的setContentArray数据
        val morear = mutableMapOf<String, MutableList<String>>()
        val scriptMatches = Regex("setContentArray\\(([^)]+)\\)").findAll(html)
        
        for (match in scriptMatches) {
            val content = match.groupValues[1]
            val parts = content.split("'")
            if (parts.size >= 3) {
                val id = parts[2].removePrefix(",").trim()
                val con = parts[1]
                
                if (morear.containsKey(id)) {
                    morear.getValue(id).add(con)
                } else {
                    morear[id] = mutableListOf(con)
                }
            }
        }
        
        // 解析表格行
        val rows = html.split("<tr>", "<tr ", "</tr>").filter { 
            it.contains("PuTongCell") || it.contains("TableLCRoomOccupy")
        }
        
        for (row in rows) {
            val cells = row.split("<td", "</td>").filter { 
                it.contains("PuTongCell") || it.contains("class")
            }
            
            var xq = 0
            for (cell in cells) {
                xq++
                
                val cellContent = cell.split("<br>", "<br/>", "<br />")
                    .map { it.replace(Regex("<[^>]+>"), "").trim() }
                    .filter { it.isNotEmpty() }
                
                if (cellContent.size < 2) continue
                
                // 检查是否是网上课程（跳过）
                if (cellContent.getOrNull(1)?.contains("网上") == true) continue
                
                // 第1行：课程名
                val nameMatch = Regex("(?<=\\>).*?(?=\\<)").find(cellContent.getOrNull(0) ?: "")
                val courseName = nameMatch?.value ?: continue
                
                // 第2行：教师
                val teacherMatches = Regex("(?<=\\>).*?(?=\\<)").findAll(cellContent.getOrNull(1) ?: "")
                val teacher = teacherMatches.map { it.value }.joinToString(",")
                
                // 第3行：教室
                val classroom = cellContent.getOrNull(2) ?: ""
                
                // 第4行：周次和节次 [1-16周][1-2节]
                val weekSectionStr = cellContent.getOrNull(3) ?: ""
                val weekStr = weekSectionStr.split("[")[0]
                val weeks = parseWeeks(weekStr)
                
                val sectionMatch = Regex("(?<=\\[).*?(?=\\])").find(weekSectionStr)
                val sectionStr = sectionMatch?.value?.replace("节", "") ?: ""
                val sections = parseSections(sectionStr)
                
                courses.add(ParsedCourse(
                    courseName = courseName.trim(),
                    teacher = teacher.trim(),
                    classroom = classroom.trim(),
                    dayOfWeek = xq,
                    startSection = sections.minOrNull() ?: 1,
                    sectionCount = sections.size,
                    weeks = weeks,
                    weekExpression = weeks.joinToString(",") + "周"
                ))
                
                // 处理更多信息（showMoreInfomation）
                val moreInfoMatch = Regex("showMoreInfomation\\('([^']+)'\\)").find(cell)
                if (moreInfoMatch != null) {
                    val id = moreInfoMatch.groupValues[1]
                    val moreInfoList = morear[id]
                    
                    moreInfoList?.forEach { moreCon ->
                        val moreParts = moreCon.split("^")
                        if (moreParts.size >= 5 && !moreParts[1].contains("网上")) {
                            val moreWeeks = parseWeeks(moreParts[4].split("[")[0])
                            val moreSectionMatch = Regex("(?<=\\[).*?(?=\\])").findAll(moreParts[4])
                            val moreSections = moreSectionMatch.mapNotNull { 
                                parseSections(it.value.replace("节", "")).firstOrNull()
                            }.toList()
                            
                            courses.add(ParsedCourse(
                                courseName = moreParts[0].trim(),
                                teacher = moreParts[1].trim(),
                                classroom = moreParts[2].trim(),
                                dayOfWeek = xq,
                                startSection = moreSections.minOrNull() ?: 1,
                                sectionCount = moreSections.size,
                                weeks = moreWeeks,
                                weekExpression = moreWeeks.joinToString(",") + "周"
                            ))
                        }
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
        
        if (weekStr.isEmpty()) return listOf(1)
        
        var weekss = weekStr.replace(Regex("[第()]"), "")
        val weekParts = mutableListOf<String>()
        
        // 按"单"或"双"分割
        while (weekss.contains("单") || weekss.contains("双")) {
            val zindex = minOf(
                weekss.indexOf("单").let { if (it >= 0) it else Int.MAX_VALUE },
                weekss.indexOf("双").let { if (it >= 0) it else Int.MAX_VALUE }
            )
            
            if (zindex < Int.MAX_VALUE) {
                weekParts.add(weekss.substring(0, zindex + 1))
                weekss = weekss.substring(zindex + 1)
                val digitIndex = weekss.indexOfFirst { it.isDigit() }
                weekss = if (digitIndex >= 0) weekss.substring(digitIndex) else ""
            } else {
                break
            }
        }
        
        if (weekss.isNotEmpty()) {
            weekParts.add(weekss)
        }
        
        for (part in weekParts.filter { it.isNotBlank() }) {
            val isDan = part.endsWith("单")
            val isShuang = part.endsWith("双")
            val cleanPart = part.replace("单", "").replace("双", "").replace("周", "")
            
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
     * 解析节次字符串
     * 例如：1-2 -> [1, 2]
     */
    private fun parseSections(sectionStr: String): List<Int> {
        val sections = mutableListOf<Int>()
        
        val parts = sectionStr.split("-")
        val start = parts.getOrNull(0)?.toIntOrNull() ?: 1
        val end = parts.getOrNull(1)?.toIntOrNull() ?: start
        
        for (section in start..end) {
            sections.add(section)
        }
        
        return sections
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

