package com.wind.ggbond.classtime.util.extractor

import android.util.Log
import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 中原科技学院课表提取器
 * 系统：树维教务系统（版本一 - actTeacherName）
 * 参考：temp_aishedule/树维/中原科技学院/provider.js
 */
@Singleton
class ZYKJXYExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "zykjxy"
    override val schoolName = "中原科技学院"
    override val systemType = "shuwei"
    
    companion object {
        private const val TAG = "ZYKJXYExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("/eams/") && 
               (url.contains("courseTableForStd") || 
                html.contains("activity = new TaskActivity"))
    }
    
    override fun getLoginUrl(): String = "http://jwxt.zykj.edu.cn/"
    override fun getScheduleUrl(): String = "http://jwxt.zykj.edu.cn/eams/courseTableForStd.action"
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取中原科技学院课表（树维系统-版本一）...');
                    var courses = [];
                    var htmlText = document.documentElement.outerHTML;
                    
                    // 使用版本一的提取方式：actTeacherName
                    var teachers = htmlText.split(/var teachers = \[.*?\];/);
                    console.log('找到 ' + (teachers.length - 1) + ' 个课程块');
                    
                    teachers.slice(1).forEach(function(courseText) {
                        try {
                            // 提取actTeacherName后的参数
                            var orArrMatch = courseText.match(/(?<=actTeacherName\.join\(','\),).*?(?=\);)/g);
                            if (!orArrMatch || orArrMatch.length === 0) return;
                            
                            var dayMatches = courseText.match(/(?<=index \=).*?(?=\*unitCount)/g);
                            var sectionMatches = courseText.match(/(?<=unitCount\+).*?(?=;)/g);
                            var teacherMatches = courseText.match(/(?<=name:").*?(?=")/g);
                            
                            if (!dayMatches || dayMatches.length === 0) return;
                            
                            // 去重
                            var day = Array.from(new Set(dayMatches));
                            var section = Array.from(new Set(sectionMatches || []));
                            var teacher = Array.from(new Set(teacherMatches || []));
                            
                            // 解析课程信息
                            var courseCon = orArrMatch[0].split(/(?<="|l|e),(?="|n|a)/);
                            
                            var courseName = courseCon[1] ? courseCon[1].replace(/"/g, '') : '';
                            var roomName = courseCon[3] ? courseCon[3].replace(/"/g, '') : '';
                            var teacherName = teacher.join(',');
                            
                            // 解析周次（二进制字符串）
                            var weekStr = courseCon[4] ? courseCon[4].split(',')[0].replace('"', '') : '';
                            var weeks = [];
                            for (var w = 0; w < weekStr.length; w++) {
                                if (weekStr[w] == '1') weeks.push(w);
                            }
                            
                            var dayOfWeek = Number(day[0]) + 1;
                            var sections = [];
                            section.forEach(function(con) {
                                sections.push(Number(con) + 1);
                            });
                            
                            if (!courseName || weeks.length === 0) return;
                            
                            var course = {
                                courseName: courseName,
                                teacher: teacherName,
                                classroom: roomName,
                                day: dayOfWeek,
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
                    });
                    
                    console.log('✅ 提取完成，共 ' + courses.length + ' 门课程');
                    return JSON.stringify({courses: courses});
                    
                } catch (error) {
                    console.error('❌ 提取失败:', error);
                    return JSON.stringify({
                        courses: [],
                        error: '提取失败: ' + error.message
                    });
                }
            })();
        """.trimIndent()
    }
    
    override fun parseCourses(jsonData: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        
        try {
            Log.d(TAG, "开始解析中原科技学院课程数据...")
            val cleanJson = jsonData.trim().removePrefix("\"").removeSuffix("\"")
                .replace("\\\"", "\"").replace("\\n", "").replace("\\r", "")
            val jsonObject = JSONObject(cleanJson)
            
            if (jsonObject.has("error")) {
                throw Exception("提取失败: ${jsonObject.getString("error")}")
            }
            
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
        } catch (e: Exception) {
            Log.e(TAG, "解析课程数据失败", e)
            throw e
        }
        
        return courses
    }
}


















