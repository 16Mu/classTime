package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

/**
 * 武汉警官职业学院课表提取器
 * 系统：AIC智能校园
 */
@Singleton
class WHJGZYXYExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "whjgzyxy"
    override val schoolName = "武汉警官职业学院"
    override val systemType = "aic"

    override val aliases = listOf("武汉警官职业学院")
    override val supportedUrls = listOf("whpa.edu.cn")
    
    companion object {
        private const val TAG = "WHJGZYXYExtractor"
        
        // 星期映射
        private val WEEK_MAP = mapOf(
            "mon" to 1, "tue" to 2, "wed" to 3, "thu" to 4,
            "fri" to 5, "sat" to 6, "sun" to 7
        )
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("whpa.edu.cn", ignoreCase = true) &&
               (html.contains("scheduleAll.do") || html.contains("getScheduleNew"))
    }
    
    override fun getLoginUrl(): String = "http://jwgl.whpa.edu.cn/"
    override fun getScheduleUrl(): String? = null
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取${schoolName}课表...');
                    
                    async function fetchCourseData() {
                        // 获取学生ID
                        let kbHtmlurl = "/jedu/edu/core/eduStudent/scheduleAll.do";
                        let scheduleResponse = await fetch(kbHtmlurl);
                        let scheduleAllHtml = await scheduleResponse.text();
                        
                        let stuIdMatch = scheduleAllHtml.match(/(stuId\s=\s")(\d+)(";)/);
                        if (!stuIdMatch) {
                            return JSON.stringify({error: '无法获取学生ID'});
                        }
                        let stuId = stuIdMatch[2];
                        
                        // 获取学期ID
                        let semIdElement = document.getElementById('semId${'$'}value');
                        if (!semIdElement) {
                            return JSON.stringify({error: '无法获取学期ID'});
                        }
                        let semid = semIdElement.value;
                        
                        console.log('📌 学生ID:', stuId, '学期ID:', semid);
                        
                        // 获取课表数据
                        let kburl = "/jedu/edu/core/eduScheduleInfo/getScheduleNew.do";
                        let data = "semId=" + semid + "&stuId=" + stuId + "&checkType=student";
                        
                        let kbResponse = await fetch(kburl, {
                            method: 'POST',
                            headers: {'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'},
                            body: data
                        });
                        let kcjson = await kbResponse.json();
                        
                        console.log('✅ 获取到', kcjson.data.schedule.length, '门课程');
                        return JSON.stringify(kcjson.data.schedule);
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
            AppLogger.d(TAG, "开始解析${schoolName}课程数据...")
            
            val cleanJson = jsonData.trim()
                .removePrefix("\"").removeSuffix("\"")
                .replace("\\\"", "\"")
                .replace("\\n", "")
                .replace("\\r", "")
            
            // 检查是否是错误信息
            if (cleanJson.startsWith("{") && cleanJson.contains("error")) {
                val errorObj = JSONObject(cleanJson)
                throw Exception(errorObj.getString("error"))
            }
            
            val jsonArray = JSONArray(cleanJson)
            
            for (i in 0 until jsonArray.length()) {
                val content = jsonArray.getJSONObject(i)
                
                val courseName = content.optString("courseName", "")
                if (courseName.isEmpty()) continue
                
                val teacher = content.optString("teacherName", "")
                
                // 教室信息
                val eduPlace = content.optJSONObject("eduPlace")
                val classroom = eduPlace?.optString("placeName", "") ?: ""
                
                // 星期
                val weekStr = content.optString("week", "mon")
                val dayOfWeek = WEEK_MAP[weekStr] ?: 1
                
                // 周次
                val weekList = content.optString("weekList", "")
                val weeks = parseWeeks(weekList)
                
                // 节次
                val eduLesson = content.optJSONObject("eduLesson")
                val startLesson = eduLesson?.optInt("startLesson", 1) ?: 1
                val endLesson = eduLesson?.optInt("endLesson", 2) ?: 2
                val sectionCount = endLesson - startLesson + 1
                
                courses.add(ParsedCourse(
                    courseName = courseName.trim(),
                    teacher = teacher.trim(),
                    classroom = classroom.trim(),
                    dayOfWeek = dayOfWeek,
                    startSection = startLesson,
                    sectionCount = sectionCount,
                    weeks = weeks,
                    weekExpression = weeks.joinToString(",") + "周"
                ))
            }
            
            AppLogger.d(TAG, "✅ 成功解析 ${courses.size} 门课程")
            return courses
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析课程数据失败", e)
            throw e
        }
    }
    
    /**
     * 解析周次字符串
     * 格式：第1-3周,5-9周,11-15周 或 第7-9(单周),10-18周
     */
    private fun parseWeeks(weekStr: String): List<Int> {
        val weeks = mutableListOf<Int>()
        
        if (weekStr.isEmpty()) return listOf(1)
        
        // 清理字符串
        var weekss = weekStr.replace(Regex("[第()]"), "")
        val weekParts = mutableListOf<String>()
        
        // 按"周"分割
        while (weekss.contains("周")) {
            val zindex = weekss.indexOf("周")
            weekParts.add(weekss.substring(0, zindex + 1).replace("周", ""))
            
            if (zindex + 1 >= weekss.length) {
                weekss = ""
            } else {
                weekss = weekss.substring(zindex + 2)
                val digitIndex = weekss.indexOfFirst { it.isDigit() }
                weekss = if (digitIndex >= 0) weekss.substring(digitIndex) else ""
            }
        }
        
        if (weekss.isNotEmpty()) {
            weekParts.add(weekss)
        }
        
        // 解析每个部分
        for (part in weekParts.filter { it.isNotBlank() }) {
            val isDan = part.endsWith("单")
            val isShuang = part.endsWith("双")
            val cleanPart = part.replace("单", "").replace("双", "")
            
            val ranges = cleanPart.split(",")
            for (range in ranges) {
                val rangeParts = range.split("-")
                val start = rangeParts.getOrNull(0)?.toIntOrNull() ?: continue
                val end = rangeParts.getOrNull(1)?.toIntOrNull() ?: start
                
                for (week in start..minOf(end, 20)) {
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
}


















