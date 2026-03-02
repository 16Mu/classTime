package com.wind.ggbond.classtime.util.extractor

import android.util.Log
import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 江西水利职业学院课表提取器
 * 系统：联亦科技
 */
@Singleton
class JXSLZYXYExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "jxslzyxy"
    override val schoolName = "江西水利职业学院"
    override val systemType = "lianyi"
    
    companion object {
        private const val TAG = "JXSLZYXYExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("jxssly.cn", ignoreCase = true) ||
               html.contains("studentCourseSchedule") ||
               html.contains("ant-spin-container")
    }
    
    override fun getLoginUrl(): String = "http://jwgl.jxssly.cn/"
    override fun getScheduleUrl(): String? = null
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取${schoolName}课表...');
                    
                    async function fetchCourseData() {
                        let bodyText = document.getElementsByTagName("body")[0].outerText.replace(/\\n|\\s/g, "");
                        let idMatch = bodyText.match(/(?<=学号:).*?(?=姓名)/);
                        
                        if (idMatch) {
                            // 方式1：通过API获取JSON数据
                            let currentUrl = "api/baseInfo/semester/selectCurrentXnXq";
                            let timeTag = parseInt(+new Date() / 1000);
                            
                            let xqResponse = await fetch(currentUrl + "?_t=" + timeTag);
                            let xqData = await xqResponse.json();
                            let semester = xqData.data.semester;
                            
                            let data = {
                                "semester": semester,
                                "weeks": [...new Array(31).keys()].slice(1),
                                "studentId": idMatch[0],
                                "source": "xs",
                                "oddOrDouble": 1,
                                "startWeek": "1",
                                "stopWeek": "30"
                            };
                            
                            console.log('📌 通过API获取数据');
                            
                            let kcResponse = await fetch("/api/arrange/CourseScheduleAllQuery/studentCourseSchedule?_t=" + timeTag, {
                                method: 'POST',
                                headers: {'Content-Type': 'application/json;charset=UTF-8'},
                                body: JSON.stringify(data)
                            });
                            let kcText = await kcResponse.text();
                            
                            console.log('✅ 获取完成');
                            return JSON.stringify({html: kcText, tag: "json"});
                        } else {
                            // 方式2：解析HTML表格
                            let divs = document.getElementsByClassName("ant-spin-container")[0];
                            if (!divs) {
                                return JSON.stringify({error: '未找到课表数据'});
                            }
                            
                            let table = divs.getElementsByTagName("table")[0];
                            if (!table) {
                                return JSON.stringify({error: '未找到课表表格'});
                            }
                            
                            console.log('✅ 提取HTML完成');
                            return JSON.stringify({html: table.outerHTML, tag: "html"});
                        }
                    }
                    
                    return fetchCourseData();
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
                .replace("\\n", "")
                .replace("\\r", "")
            
            val jsonObject = JSONObject(cleanJson)
            
            if (jsonObject.has("error")) {
                throw Exception(jsonObject.getString("error"))
            }
            
            val tag = jsonObject.getString("tag")
            val html = jsonObject.getString("html")
            
            when (tag) {
                "json" -> {
                    courses.addAll(parseJsonData(html))
                }
                "html" -> {
                    courses.addAll(parseHtmlTable(html))
                }
                else -> {
                    throw Exception("未知的数据格式: $tag")
                }
            }
            
            val resolvedCourses = resolveCourseConflicts(courses)
            
            Log.d(TAG, "✅ 成功解析 ${resolvedCourses.size} 门课程")
            return resolvedCourses
        } catch (e: Exception) {
            Log.e(TAG, "解析课程数据失败", e)
            throw e
        }
    }
    
    /**
     * 解析JSON数据
     */
    private fun parseJsonData(jsonStr: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        
        val jsonObject = JSONObject(jsonStr)
        val dataArray = jsonObject.getJSONArray("data")
        
        for (i in 0 until dataArray.length()) {
            val con = dataArray.getJSONObject(i)
            val courseList = con.getJSONArray("courseList")
            
            for (j in 0 until courseList.length()) {
                val kc = courseList.getJSONObject(j)
                
                val courseName = kc.optString("courseName", "")
                if (courseName.isEmpty()) continue
                
                val teacher = kc.optString("teacherName", "")
                val classroom = kc.optString("classroomName", "")
                val dayOfWeek = kc.optInt("dayOfWeek", 1)
                val day = if (dayOfWeek == 0) 7 else dayOfWeek - 1
                
                val weeksStr = kc.optString("weeks", "")
                val weeks = parseWeeks(weeksStr)
                
                val timeStr = kc.optString("time", "")
                val sections = parseSections(timeStr)
                
                courses.add(ParsedCourse(
                    courseName = courseName.trim(),
                    teacher = teacher.trim(),
                    classroom = classroom.trim(),
                    dayOfWeek = day,
                    startSection = sections.minOrNull() ?: 1,
                    sectionCount = sections.size,
                    weeks = weeks,
                    weekExpression = weeks.joinToString(",") + "周"
                ))
            }
        }
        
        return courses
    }
    
    /**
     * 解析HTML表格
     */
    private fun parseHtmlTable(html: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        
        // 简化的HTML解析
        val rows = html.split("<tr>", "<tr ", "</tr>").filter { it.contains("<td") }
        
        for (row in rows) {
            val cells = row.split("<td", "</td>").filter { it.contains(">") }
            
            for ((dayIndex, cell) in cells.withIndex()) {
                if (dayIndex == 0) continue // 跳过第一列
                
                val divs = cell.split("<div", "</div>").filter { it.contains("class") }
                
                for (div in divs) {
                    val course = mutableMapOf<String, Any>()
                    course["day"] = dayIndex
                    course["weeks"] = mutableListOf<Int>()
                    course["sections"] = mutableListOf<Int>()
                    
                    val innerDivs = div.split("<div", "</div>").filter { it.contains(">") }
                    
                    for ((divIndex, innerDiv) in innerDivs.withIndex()) {
                        if (divIndex == 0) {
                            // 课程名
                            val nameMatch = Regex(">([^<]+)<").find(innerDiv)
                            course["name"] = nameMatch?.groupValues?.getOrNull(1)?.replace(Regex("\\s"), "") ?: ""
                        } else {
                            // 检查img的alt属性
                            val altMatch = Regex("alt=['\"]([^'\"]+)['\"]").find(innerDiv)
                            val alt = altMatch?.groupValues?.getOrNull(1) ?: ""
                            
                            val textMatch = Regex(">([^<]+)<").find(innerDiv)
                            val text = textMatch?.groupValues?.getOrNull(1) ?: ""
                            
                            when (alt) {
                                "地点" -> {
                                    course["position"] = text.replace(Regex("\\(\\d{5,}?\\)"), "")
                                }
                                "教师" -> {
                                    course["teacher"] = text
                                }
                                "时间" -> {
                                    // 解析周次和节次
                                    val spans = innerDiv.split("<span", "</span>").filter { it.contains(">") }
                                    if (spans.size >= 2) {
                                        val weekStr = spans[0].replace(Regex("<[^>]+>"), "").replace(Regex("\\s"), "")
                                        (course["weeks"] as MutableList<Int>).addAll(parseWeeks(weekStr))
                                        
                                        val sectionStr = spans[1].replace(Regex("<[^>]+>"), "").replace(Regex("\\s"), "")
                                        (course["sections"] as MutableList<Int>).addAll(parseSections(sectionStr))
                                    }
                                }
                            }
                        }
                    }
                    
                    val name = course["name"] as? String ?: ""
                    if (name.isNotEmpty()) {
                        courses.add(ParsedCourse(
                            courseName = name.trim(),
                            teacher = (course["teacher"] as? String ?: "").trim(),
                            classroom = (course["position"] as? String ?: "").trim(),
                            dayOfWeek = course["day"] as? Int ?: 1,
                            startSection = (course["sections"] as? List<Int>)?.minOrNull() ?: 1,
                            sectionCount = (course["sections"] as? List<Int>)?.size ?: 1,
                            weeks = course["weeks"] as? List<Int> ?: listOf(1),
                            weekExpression = (course["weeks"] as? List<Int>)?.joinToString(",") + "周"
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
        
        if (weekStr.isEmpty()) return listOf(1)
        
        var cleaned = weekStr.replace(Regex("[(){}|第到]"), "")
        val weekParts = mutableListOf<String>()
        
        while (cleaned.contains("周")) {
            val index = cleaned.indexOf("周")
            val nextChar = cleaned.getOrNull(index + 1)
            
            if (nextChar == '单' || nextChar == '双') {
                weekParts.add(cleaned.substring(0, index + 2).replace("周", ""))
                cleaned = cleaned.substring(index + 2)
            } else {
                weekParts.add(cleaned.substring(0, index + 1).replace("周", ""))
                cleaned = cleaned.substring(index + 1)
            }
            
            val digitIndex = cleaned.indexOfFirst { it.isDigit() }
            cleaned = if (digitIndex >= 0) cleaned.substring(digitIndex) else ""
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
     * 解析节次字符串
     * 例如：第1,2,3节 -> [1, 2, 3]
     */
    private fun parseSections(sectionStr: String): List<Int> {
        val sections = mutableListOf<Int>()
        
        val cleaned = sectionStr.replace(Regex("[第节()]"), "")
        val parts = cleaned.split(",")
        
        for (part in parts) {
            part.toIntOrNull()?.let { sections.add(it) }
        }
        
        return sections.distinct().sorted()
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


















