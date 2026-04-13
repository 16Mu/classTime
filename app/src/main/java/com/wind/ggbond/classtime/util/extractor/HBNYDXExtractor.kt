package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.util.WeekParser
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

/**
 * 河北农业大学课表提取器
 * 系统：URP教务系统（HTML表格解析）
 * 参考：temp_aishedule/URP教务/河北农业大学/河北农业.js
 */
@Singleton
class HBNYDXExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "hbnydx"
    override val schoolName = "河北农业大学"
    override val systemType = "urp"

    override val aliases = listOf("河北农业大学")
    override val supportedUrls = listOf("jwgl.hebau.edu.cn")
    
    companion object {
        private const val TAG = "HBNYDXExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("thisSemesterCurriculum") ||
               html.contains("user") ||
               html.contains("tbody")
    }
    
    override fun getLoginUrl(): String {
        return "http://jwgl.hebau.edu.cn/"
    }
    
    override fun getScheduleUrl(): String {
        return "/student/courseSelect/thisSemesterCurriculum/callback"
    }
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取河北农业大学课表（URP系统-HTML表格）...');
                    
                    // 查找课表表格
                    var userTables = document.querySelectorAll('#user');
                    var lb = null;
                    
                    if (userTables.length >= 2) {
                        lb = userTables[1];
                    } else if (userTables.length === 1) {
                        lb = userTables[0];
                    }
                    
                    if (!lb) {
                        return JSON.stringify({courses: [], error: '未找到课表表格'});
                    }
                    
                    var tbody = lb.querySelector('tbody');
                    if (!tbody) {
                        return JSON.stringify({courses: [], error: '未找到tbody'});
                    }
                    
                    var trs = tbody.querySelectorAll('tr');
                    console.log('找到 ' + trs.length + ' 行数据');
                    
                    var courses = [];
                    var currentCourseName = '';
                    var currentTeacher = '';
                    
                    trs.forEach(function(tr) {
                        var tds = tr.querySelectorAll('td');
                        
                        // 长行（完整课程信息，td>7）
                        if (tds.length > 7) {
                            var weekText = tds[11] ? tds[11].textContent.trim() : '';
                            var positionText = tds[16] ? tds[16].textContent.trim() : '';
                            
                            if (weekText.length > 0 && positionText !== "虚拟教学楼") {
                                currentCourseName = tds[2] ? tds[2].textContent.trim() : '';
                                currentTeacher = tds[7] ? tds[7].textContent.trim().replace(/\d|\*/g, "").replace(/\*/g, " ") : '';
                                
                                var course = {
                                    courseName: currentCourseName,
                                    teacher: currentTeacher,
                                    classroom: positionText + (tds[17] ? tds[17].textContent.trim() : ''),
                                    dayOfWeek: parseInt(tds[12] ? tds[12].textContent.trim() : '1'),
                                    weekExpression: weekText,
                                    sections: [],
                                    credit: 0
                                };
                                
                                // 解析节次
                                var startSectionCN = tds[13] ? tds[13].textContent.trim() : '';
                                var continuingSession = parseInt(tds[14] ? tds[14].textContent.trim() : '1');
                                var startSection = cnToNumber(startSectionCN);
                                
                                for (var i = 0; i < continuingSession; i++) {
                                    course.sections.push(startSection + i);
                                }
                                
                                courses.push(course);
                            }
                        }
                        // 短行（附加课程时间，td<=7）
                        else {
                            var weekText = tds[0] ? tds[0].textContent.trim() : '';
                            var positionText = tds[5] ? tds[5].textContent.trim() : '';
                            
                            if (weekText.length > 0 && positionText !== "虚拟教学楼") {
                                var course = {
                                    courseName: currentCourseName,
                                    teacher: currentTeacher,
                                    classroom: positionText + (tds[6] ? tds[6].textContent.trim() : ''),
                                    dayOfWeek: parseInt(tds[1] ? tds[1].textContent.trim() : '1'),
                                    weekExpression: weekText,
                                    sections: [],
                                    credit: 0
                                };
                                
                                // 替换特殊字符
                                course.classroom = course.classroom.replace("到个人课表查询71", "");
                                
                                // 解析节次
                                var startSectionCN = tds[2] ? tds[2].textContent.trim() : '';
                                var continuingSession = parseInt(tds[3] ? tds[3].textContent.trim() : '1');
                                var startSection = cnToNumber(startSectionCN);
                                
                                for (var i = 0; i < continuingSession; i++) {
                                    course.sections.push(startSection + i);
                                }
                                
                                courses.push(course);
                            }
                        }
                    });
                    
                    console.log('✅ 提取完成，共 ' + courses.length + ' 门课程');
                    return JSON.stringify({courses: courses});
                    
                } catch (error) {
                    console.error('❌ 提取失败:', error);
                    return JSON.stringify({courses: [], error: '提取失败: ' + error.message});
                }
                
                function cnToNumber(str) {
                    var map = ['', '一', '二', '三', '四', '五', '六', '七', '八', '九', '十', '十一', '十二', '十三', '十四'];
                    return map.indexOf(str);
                }
            })();
        """.trimIndent()
    }
    
    override fun parseCourses(jsonData: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        
        try {
            AppLogger.d(TAG, "开始解析河北农业大学课程数据...")
            
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
                val weekExpression = courseObj.optString("weekExpression", "")
                
                val sectionsArray = courseObj.optJSONArray("sections")
                val sections = mutableListOf<Int>()
                if (sectionsArray != null) {
                    for (j in 0 until sectionsArray.length()) {
                        val section = sectionsArray.optInt(j, 0)
                        if (section > 0) sections.add(section)
                    }
                }
                
                val weeks = if (weekExpression.isNotEmpty()) {
                    WeekParser.parseWeekExpression(weekExpression)
                } else {
                    emptyList()
                }
                
                val startSection = sections.minOrNull() ?: 1
                val sectionCount = sections.size
                
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


















