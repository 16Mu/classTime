package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

/**
 * 上海杉达学院课表提取器
 * 系统：树维教务系统（版本二 - TaskActivity）
 */
@Singleton
class SHSDExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "shsd"
    override val schoolName = "上海杉达学院"
    override val systemType = "shuwei"
    
    companion object {
        private const val TAG = "SHSDExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("/eams/") && 
               (url.contains("courseTableForStd") || 
                html.contains("activity = new TaskActivity"))
    }
    
    override fun getLoginUrl(): String = "http://jwgl.sandau.edu.cn/"
    override fun getScheduleUrl(): String = "http://jwgl.sandau.edu.cn/eams/courseTableForStd.action"
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取上海杉达学院课表（树维系统-版本二）...');
                    var courses = [];
                    var htmlText = document.documentElement.outerHTML;
                    var activityBlocks = htmlText.split(/activity = new /);
                    console.log('找到 ' + (activityBlocks.length - 1) + ' 个课程块');
                    
                    for (var i = 1; i < activityBlocks.length; i++) {
                        var courseText = activityBlocks[i];
                        try {
                            var taskMatch = courseText.match(/TaskActivity\((.*?)\);/);
                            if (!taskMatch) continue;
                            var params = taskMatch[1].split('","');
                            if (params.length < 7) continue;
                            params = params.map(function(p) { return p.replace(/^"|"$/g, ''); });
                            
                            var dayMatch = courseText.match(/index\s*=\s*(\d+)\s*\*\s*unitCount/);
                            var day = dayMatch ? parseInt(dayMatch[1]) + 1 : 0;
                            
                            var sectionMatches = courseText.match(/unitCount\+(\d+);/g);
                            var sections = [];
                            if (sectionMatches) {
                                var sectionSet = {};
                                sectionMatches.forEach(function(match) {
                                    var num = parseInt(match.match(/\d+/)[0]) + 1;
                                    sectionSet[num] = true;
                                });
                                sections = Object.keys(sectionSet).map(Number).sort(function(a, b) { return a - b; });
                            }
                            
                            var weekStr = params[6] || '';
                            var weeks = [];
                            for (var w = 0; w < weekStr.length; w++) {
                                if (weekStr[w] == '1') weeks.push(w);
                            }
                            
                            var course = {
                                courseName: params[3] || '',
                                teacher: params[1] || '',
                                classroom: params[5] || '',
                                day: day,
                                startSection: sections[0] || 1,
                                sectionCount: sections.length || 1,
                                weeks: weeks,
                                weekExpression: weeks.join(',') + '周',
                                credit: 0
                            };
                            
                            console.log('✓ 课程: ' + course.courseName);
                            courses.push(course);
                        } catch (e) {
                            console.log('✗ 解析课程出错: ' + e.message);
                        }
                    }
                    
                    console.log('✅ 提取完成，共 ' + courses.length + ' 门课程');
                    return JSON.stringify({courses: courses});
                } catch (error) {
                    console.error('❌ 提取失败:', error);
                    return JSON.stringify({courses: [], error: '提取失败: ' + error.message});
                }
            })();
        """.trimIndent()
    }
    
    override fun parseCourses(jsonData: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        try {
            AppLogger.d(TAG, "开始解析${schoolName}课程数据...")
            val cleanJson = jsonData.trim().removePrefix("\"").removeSuffix("\"")
                .replace("\\\"", "\"").replace("\\n", "").replace("\\r", "")
            val jsonObject = JSONObject(cleanJson)
            if (jsonObject.has("error")) throw Exception("提取失败: ${jsonObject.getString("error")}")
            
            val coursesArray = jsonObject.getJSONArray("courses")
            for (i in 0 until coursesArray.length()) {
                val courseObj = coursesArray.getJSONObject(i)
                val weeksArray = courseObj.optJSONArray("weeks")
                val weeks = mutableListOf<Int>()
                if (weeksArray != null) {
                    for (j in 0 until weeksArray.length()) weeks.add(weeksArray.getInt(j))
                }
                courses.add(ParsedCourse(
                    courseName = courseObj.optString("courseName", "").trim(),
                    teacher = courseObj.optString("teacher", "").trim(),
                    classroom = courseObj.optString("classroom", "").trim(),
                    dayOfWeek = courseObj.optInt("day", 1),
                    startSection = courseObj.optInt("startSection", 1),
                    sectionCount = courseObj.optInt("sectionCount", 2),
                    weeks = weeks,
                    credit = courseObj.optDouble("credit", 0.0).toFloat(),
                    weekExpression = if (weeks.isNotEmpty()) weeks.sorted().joinToString(",") + "周" else ""
                ))
            }
        } catch (e: Exception) { AppLogger.e(TAG, "解析课程数据失败", e); throw e }
        return courses
    }
}


















