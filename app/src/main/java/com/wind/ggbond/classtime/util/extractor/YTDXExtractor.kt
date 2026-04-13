package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

/**
 * 烟台大学课表提取器
 * 系统：URP教务系统
 * 参考：temp_aishedule/URP教务/烟台大学/provider.js
 */
@Singleton
class YTDXExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "ytdx"
    override val schoolName = "烟台大学"
    override val systemType = "urp"

    override val aliases = listOf("烟台大学")
    override val supportedUrls = listOf("jwgl.ytu.edu.cn")
    
    companion object {
        private const val TAG = "YTDXExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("thisSemesterCurriculum") ||
               url.contains("courseSelect") ||
               url.contains("calendarSemesterCurriculum") ||
               html.contains("courseTable") ||
               html.contains("planCode")
    }
    
    override fun getLoginUrl(): String {
        return "http://jwgl.ytu.edu.cn/"
    }
    
    override fun getScheduleUrl(): String {
        return "/student/courseSelect/thisSemesterCurriculum/callback"
    }
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取烟台大学课表（URP系统）...');
                    
                    var html = null;
                    var preUrl = window.location.href;
                    var planCode = document.getElementById("planCode");
                    var planCodeValue = planCode && planCode.value ? planCode.value : '';
                    
                    // 检查是否使用webvpn
                    if (preUrl.search('webvpn') != -1) {
                        console.log('检测到webvpn，使用HTML表格模式');
                        var courseTable = document.getElementById('courseTable');
                        if (courseTable) {
                            return JSON.stringify({tag: 'html', html: courseTable.outerHTML});
                        }
                    }
                    
                    // 根据不同URL使用不同API
                    if (preUrl.search('thisSemesterCurriculum') != -1) {
                        console.log('在本学期课表页面');
                        var response = await fetch('/student/courseSelect/thisSemesterCurriculum/callback', {
                            method: 'GET'
                        });
                        html = await response.text();
                    } else if (preUrl.search('calendarSemesterCurriculum') != -1) {
                        console.log('在日历课表页面，planCode: ' + planCodeValue);
                        var formData = new FormData();
                        formData.set("planCode", planCodeValue);
                        var response = await fetch('/student/courseSelect/thisSemesterCurriculum/ajaxStudentSchedule/callback', {
                            method: 'POST',
                            body: formData
                        });
                        html = await response.text();
                    } else if (preUrl.search('courseSelectResult') != -1) {
                        console.log('在选课结果页面');
                        var response = await fetch('/student/courseSelect/thisSemesterCurriculum/callback', {
                            method: 'GET'
                        });
                        html = await response.text();
                    } else {
                        console.log('默认使用本学期课表API');
                        var response = await fetch('/student/courseSelect/thisSemesterCurriculum/callback', {
                            method: 'GET'
                        });
                        html = await response.text();
                    }
                    
                    var jsonData = JSON.parse(html);
                    
                    if (!jsonData.dateList || jsonData.dateList.length === 0) {
                        return JSON.stringify({courses: [], error: '未获取到课表数据'});
                    }
                    
                    var coursesList = jsonData.dateList[0].selectCourseList;
                    if (!coursesList) {
                        return JSON.stringify({courses: [], error: '课程列表为空'});
                    }
                    
                    var courses = [];
                    coursesList.forEach(function(course) {
                        var name = course.courseName || '';
                        var teacher = (course.attendClassTeacher || '').replace("*", "").trim().replace(/\s+/g, ",");
                        
                        if (!course.timeAndPlaceList) return;
                        
                        course.timeAndPlaceList.forEach(function(time) {
                            var day = time.classDay || 1;
                            var position = time.classroomName || '';
                            var weeks = [];
                            var sections = [];
                            
                            var classWeek = time.classWeek || '';
                            for (var i = 0; i < classWeek.length; i++) {
                                if (classWeek[i] === '1') {
                                    weeks.push(i + 1);
                                }
                            }
                            
                            var startSection = time.classSessions || 1;
                            var continuingSession = time.continuingSession || 1;
                            for (var i = 0; i < continuingSession; i++) {
                                sections.push(startSection + i);
                            }
                            
                            courses.push({
                                courseName: name,
                                teacher: teacher,
                                classroom: position,
                                dayOfWeek: day,
                                weeks: weeks,
                                sections: sections,
                                credit: 0
                            });
                        });
                    });
                    
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
            AppLogger.d(TAG, "开始解析华北理工大学课程数据...")
            
            val cleanJson = jsonData.trim()
                .removePrefix("\"").removeSuffix("\"")
                .replace("\\\"", "\"")
                .replace("\\n", "")
                .replace("\\r", "")
            
            val jsonObject = JSONObject(cleanJson)
            
            if (jsonObject.has("error")) {
                throw Exception("提取失败: ${jsonObject.getString("error")}")
            }
            
            val coursesArray = jsonObject.getJSONArray("courses")
            AppLogger.d(TAG, "找到 ${coursesArray.length()} 门课程")
            
            for (i in 0 until coursesArray.length()) {
                val courseObj = coursesArray.getJSONObject(i)
                
                val courseName = courseObj.optString("courseName", "").trim()
                val teacher = courseObj.optString("teacher", "").trim()
                val classroom = courseObj.optString("classroom", "").trim()
                val dayOfWeek = courseObj.optInt("dayOfWeek", 1)
                
                val weeksArray = courseObj.optJSONArray("weeks")
                val weeks = mutableListOf<Int>()
                if (weeksArray != null) {
                    for (j in 0 until weeksArray.length()) {
                        val week = weeksArray.optInt(j, 0)
                        if (week > 0) weeks.add(week)
                    }
                }
                
                val sectionsArray = courseObj.optJSONArray("sections")
                val sections = mutableListOf<Int>()
                if (sectionsArray != null) {
                    for (j in 0 until sectionsArray.length()) {
                        val section = sectionsArray.optInt(j, 0)
                        if (section > 0) sections.add(section)
                    }
                }
                
                val startSection = sections.minOrNull() ?: 1
                val sectionCount = sections.size
                val weekExpression = if (weeks.isNotEmpty()) weeks.sorted().joinToString(",") + "周" else ""
                
                if (courseName.isNotEmpty() && weeks.isNotEmpty() && sections.isNotEmpty()) {
                    courses.add(
                        ParsedCourse(
                            courseName = courseName,
                            teacher = teacher,
                            classroom = classroom,
                            dayOfWeek = dayOfWeek,
                            startSection = startSection,
                            sectionCount = sectionCount,
                            weeks = weeks,
                            credit = 0f,
                            weekExpression = weekExpression
                        )
                    )
                }
            }
            
            AppLogger.d(TAG, "解析完成，共 ${courses.size} 门课程")
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析课程数据失败", e)
            throw e
        }
        
        return courses
    }
}


















