package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

@Singleton
class AHYKDXExtractor @Inject constructor() : SchoolScheduleExtractor {
    override val schoolId = "ahykdx"
    override val schoolName = "安徽医科大学"
    override val systemType = "shuwei"

    override val aliases = listOf("安徽医科大学", "安医大")
    override val supportedUrls = listOf("jxgl.ahmu.edu.cn")
    companion object { private const val TAG = "AHYKDXExtractor" }
    
    override fun isSchedulePage(html: String, url: String): Boolean =
        url.contains("/eams/") && (url.contains("courseTableForStd") || html.contains("activity = new TaskActivity"))
    
    override fun getLoginUrl() = "http://jxgl.ahmu.edu.cn/"
    override fun getScheduleUrl() = "http://jxgl.ahmu.edu.cn/eams/courseTableForStd.action"
    
    override fun generateExtractionScript() = """
        (function() {
            try {
                console.log('🔍 开始提取安徽医科大学课表（树维系统-版本一）...');
                var courses = [];
                var htmlText = document.documentElement.outerHTML;
                var teachers = htmlText.split(/var teachers = \[.*?\];/);
                console.log('找到 ' + (teachers.length - 1) + ' 个课程块');
                teachers.slice(1).forEach(function(courseText) {
                    try {
                        var orArrMatch = courseText.match(/(?<=actTeacherName\.join\(','\),).*?(?=\);)/g);
                        if (!orArrMatch || orArrMatch.length === 0) return;
                        var dayMatches = courseText.match(/(?<=index \=).*?(?=\*unitCount)/g);
                        var sectionMatches = courseText.match(/(?<=unitCount\+).*?(?=;)/g);
                        var teacherMatches = courseText.match(/(?<=name:").*?(?=")/g);
                        if (!dayMatches || dayMatches.length === 0) return;
                        var day = Array.from(new Set(dayMatches));
                        var section = Array.from(new Set(sectionMatches || []));
                        var teacher = Array.from(new Set(teacherMatches || []));
                        var courseCon = orArrMatch[0].split(/(?<="|l|e),(?="|n|a)/);
                        var courseName = courseCon[1] ? courseCon[1].replace(/"/g, '') : '';
                        var roomName = courseCon[3] ? courseCon[3].replace(/"/g, '') : '';
                        var teacherName = teacher.join(',');
                        var weekStr = courseCon[4] ? courseCon[4].split(',')[0].replace('"', '') : '';
                        var weeks = [];
                        for (var w = 0; w < weekStr.length; w++) {
                            if (weekStr[w] == '1') weeks.push(w);
                        }
                        var dayOfWeek = Number(day[0]) + 1;
                        var sections = [];
                        section.forEach(function(con) { sections.push(Number(con) + 1); });
                        if (!courseName || weeks.length === 0) return;
                        var course = {courseName: courseName, teacher: teacherName, classroom: roomName, day: dayOfWeek, startSection: sections[0] || 1, sectionCount: sections.length || 1, weeks: weeks, weekExpression: weeks.join(',') + '周', credit: 0};
                        console.log('✓ 课程: ' + course.courseName);
                        courses.push(course);
                    } catch (e) { console.log('✗ 解析课程出错: ' + e.message); }
                });
                console.log('✅ 提取完成，共 ' + courses.length + ' 门课程');
                return JSON.stringify({courses: courses});
            } catch (error) {
                console.error('❌ 提取失败:', error);
                return JSON.stringify({courses: [], error: '提取失败: ' + error.message});
            }
        })();
    """.trimIndent()
    
    override fun parseCourses(jsonData: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        try {
            val cleanJson = jsonData.trim().removePrefix("\"").removeSuffix("\"").replace("\\\"", "\"").replace("\\n", "").replace("\\r", "")
            val jsonObject = JSONObject(cleanJson)
            if (jsonObject.has("error")) throw Exception("提取失败: ${jsonObject.getString("error")}")
            val coursesArray = jsonObject.getJSONArray("courses")
            for (i in 0 until coursesArray.length()) {
                val courseObj = coursesArray.getJSONObject(i)
                val weeksArray = courseObj.optJSONArray("weeks")
                val weeks = mutableListOf<Int>()
                if (weeksArray != null) for (j in 0 until weeksArray.length()) weeks.add(weeksArray.getInt(j))
                courses.add(ParsedCourse(courseName = courseObj.optString("courseName", "").trim(), teacher = courseObj.optString("teacher", "").trim(), classroom = courseObj.optString("classroom", "").trim(), dayOfWeek = courseObj.optInt("day", 1), startSection = courseObj.optInt("startSection", 1), sectionCount = courseObj.optInt("sectionCount", 2), weeks = weeks, credit = courseObj.optDouble("credit", 0.0).toFloat(), weekExpression = if (weeks.isNotEmpty()) weeks.sorted().joinToString(",") + "周" else ""))
            }
        } catch (e: Exception) { AppLogger.e(TAG, "解析课程数据失败", e); throw e }
        return courses
    }
}


















