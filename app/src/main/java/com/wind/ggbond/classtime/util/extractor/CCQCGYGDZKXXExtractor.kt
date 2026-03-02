package com.wind.ggbond.classtime.util.extractor

import android.util.Log
import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.util.WeekParser
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 长春汽车工业高等专科学校课表提取器
 * 系统：金智教务系统（HTML解析）
 * 参考：temp_aishedule/金智教务/长春汽车工业高等专科学校/Provider.js
 */
@Singleton
class CCQCGYGDZKXXExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "ccqcgygdzkxx"
    override val schoolName = "长春汽车工业高等专科学校"
    override val systemType = "jinzhi"
    
    companion object {
        private const val TAG = "CCQCGYGDZKXXExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("wdkb") ||
               html.contains("kcb_container") ||
               html.contains("wut_table")
    }
    
    override fun getLoginUrl(): String {
        return "http://jwgl.caii.edu.cn/"
    }
    
    override fun getScheduleUrl(): String {
        return "/jwapp/sys/wdkb/"
    }
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取长春汽车工业高等专科学校课表（金智系统-HTML）...');
                    
                    var kcbContainer = document.getElementById("kcb_container");
                    if (!kcbContainer) {
                        return JSON.stringify({courses: [], error: '未找到课表容器kcb_container'});
                    }
                    
                    var courses = [];
                    var tbody = kcbContainer.querySelector('table.wut_table tbody');
                    
                    if (!tbody) {
                        return JSON.stringify({courses: [], error: '未找到表格tbody'});
                    }
                    
                    var trs = tbody.querySelectorAll('tr');
                    console.log('找到 ' + trs.length + ' 行');
                    
                    trs.forEach(function(tr) {
                        var tds = tr.querySelectorAll('td[data-role=item]');
                        
                        tds.forEach(function(td, dayIndex) {
                            var divs = td.querySelectorAll('.mtt_arrange_item');
                            
                            divs.forEach(function(div) {
                                // 跳过带有链接的课程（可能是特殊标记）
                                var linkInKcmc = div.querySelector('.mtt_item_kcmc a');
                                if (linkInKcmc) return;
                                
                                var course = {
                                    courseName: '',
                                    teacher: '',
                                    classroom: '',
                                    dayOfWeek: 0,
                                    weekExpression: '',
                                    sections: [],
                                    credit: 0
                                };
                                
                                // 提取课程名
                                var kcmcElem = div.querySelector('.mtt_item_kcmc');
                                if (kcmcElem) {
                                    var namet = kcmcElem.textContent.trim();
                                    // 提取课程名（去除前缀和方括号内容）
                                    var nameMatch = namet.match(/(?:(([A-Z]|\d)*\s))?(.+?)(?=\[|$)/);
                                    if (nameMatch && nameMatch[3]) {
                                        course.courseName = nameMatch[3].trim();
                                    } else {
                                        course.courseName = namet.split(/\[|\$|\s/)[0];
                                    }
                                }
                                
                                // 提取教师
                                var jxbmcElem = div.querySelector('.mtt_item_jxbmc');
                                if (jxbmcElem) {
                                    course.teacher = jxbmcElem.textContent.trim();
                                }
                                
                                // 提取教室、节次、星期、周次
                                var roomElem = div.querySelector('.mtt_item_room');
                                if (roomElem) {
                                    var jskc = roomElem.textContent.trim();
                                    var jskcar = jskc.split(",");
                                    
                                    if (jskcar.length >= 4) {
                                        // 最后一项是教室
                                        course.classroom = jskcar[jskcar.length - 1].trim();
                                        
                                        // 倒数第二项是节次
                                        var sectionsStr = jskcar[jskcar.length - 2].trim();
                                        course.sections = parseSections(sectionsStr);
                                        
                                        // 倒数第三项是星期
                                        var dayStr = jskcar[jskcar.length - 3].replace("星期", "").trim();
                                        course.dayOfWeek = parseInt(dayStr) || 1;
                                        
                                        // 其余部分是周次
                                        var zcParts = jskcar.slice(0, jskcar.length - 3);
                                        course.weekExpression = zcParts.join(",").trim();
                                    }
                                }
                                
                                if (course.courseName && course.weekExpression && course.sections.length > 0) {
                                    courses.push(course);
                                }
                            });
                        });
                    });
                    
                    console.log('✅ 提取完成，共 ' + courses.length + ' 门课程');
                    return JSON.stringify({courses: courses});
                    
                } catch (error) {
                    console.error('❌ 提取失败:', error);
                    return JSON.stringify({courses: [], error: '提取失败: ' + error.message});
                }
                
                function parseSections(str) {
                    str = str.replace(/第|节/g, '').trim();
                    var sections = [];
                    var arr = str.split('-');
                    
                    var start = parseInt(arr[0]);
                    var end = arr.length > 1 ? parseInt(arr[arr.length - 1]) : start;
                    
                    for (var i = start; i <= end; i++) {
                        sections.push(i);
                    }
                    
                    return sections;
                }
            })();
        """.trimIndent()
    }
    
    override fun parseCourses(jsonData: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        
        try {
            Log.d(TAG, "开始解析长春汽车工业高等专科学校课程数据...")
            
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
            Log.d(TAG, "找到 ${coursesArray.length()} 门课程")
            
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
            
            Log.d(TAG, "解析完成，共 ${courses.size} 门课程")
        } catch (e: Exception) {
            Log.e(TAG, "解析课程数据失败", e)
            throw e
        }
        
        return courses
    }
}


















