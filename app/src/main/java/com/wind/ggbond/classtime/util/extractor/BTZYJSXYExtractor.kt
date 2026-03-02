package com.wind.ggbond.classtime.util.extractor

import android.util.Log
import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.util.WeekParser
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 包头职业技术学院课表提取器
 * 系统：新正方教务系统
 * 参考：temp_aishedule/正方教务/新正方教务/包头职业技术学院/provider.js
 */
@Singleton
class BTZYJSXYExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "btzyjsxy"
    override val schoolName = "包头职业技术学院"
    override val systemType = "zfsoft"
    
    companion object {
        private const val TAG = "BTZYJSXYExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("xskbcx") || 
               url.contains("courseTableForStd") ||
               html.contains("ajaxForm")
    }
    
    override fun getLoginUrl(): String {
        return "http://jwgl.btvtc.edu.cn/"
    }
    
    override fun getScheduleUrl(): String {
        return "/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html"
    }
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取包头职业技术学院课表...');
                    
                    var htt = null;
                    var xnm = '';
                    var xqm = '';
                    
                    // 尝试从移动端页面获取
                    try {
                        xnm = document.getElementById('xnm_hide').value;
                        xqm = document.getElementById('xqm_hide').value;
                        
                        console.log('学年: ' + xnm + ', 学期: ' + xqm);
                        
                        var formData = 'xnm=' + xnm + '&xqm=' + xqm;
                        var response = await fetch('/jwglxt/kbcx/xskbcxMobile_cxXsKb.html', {
                            method: 'POST',
                            body: formData,
                            headers: {
                                'Content-Type': 'application/x-www-form-urlencoded'
                            }
                        });
                        htt = await response.json();
                    } catch (e) {
                        console.log('移动端获取失败，尝试PC端: ' + e.message);
                        
                        // 尝试从PC端页面获取
                        var response2 = await fetch('/jwglxt/kbcx/xskbcxZccx_cxXskbcxIndex.html?gnmkdm=N2151', {
                            method: 'GET'
                        });
                        var html = await response2.text();
                        var parser = new DOMParser();
                        var doc = parser.parseFromString(html, 'text/html');
                        var form = doc.getElementById('ajaxForm');
                        
                        if (!form) {
                            return JSON.stringify({courses: [], error: '未找到课表表单'});
                        }
                        
                        xnm = form.xnm.value;
                        xqm = form.xqm.value;
                        
                        var formData2 = 'xnm=' + xnm + '&xqm=' + xqm;
                        var response3 = await fetch('/jwglxt/kbcx/xskbcxMobile_cxXsKb.html', {
                            method: 'POST',
                            body: formData2,
                            headers: {
                                'Content-Type': 'application/x-www-form-urlencoded'
                            }
                        });
                        htt = await response3.json();
                    }
                    
                    console.log('获取到课表数据:', htt);
                    
                    if (!htt || !htt.kbList) {
                        return JSON.stringify({courses: [], error: '未获取到课表数据'});
                    }
                    
                    var courses = [];
                    htt.kbList.forEach(function(course) {
                        courses.push({
                            courseName: course.kcmc || '',
                            teacher: course.xm || '',
                            classroom: course.cdmc || '',
                            dayOfWeek: parseInt(course.xqj) || 1,
                            weekExpression: course.zcd || '',
                            sections: course.jc || '',
                            credit: 0
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
            Log.d(TAG, "开始解析包头职业技术学院课程数据...")
            
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
                val sectionsStr = courseObj.optString("sections", "")
                
                // 解析周次
                val weeks = if (weekExpression.isNotEmpty()) {
                    WeekParser.parseWeekExpression(weekExpression)
                } else {
                    emptyList()
                }
                
                // 解析节次
                val sections = parseSections(sectionsStr)
                val startSection = sections.minOrNull() ?: 1
                val sectionCount = sections.size
                
                if (courseName.isNotEmpty() && weeks.isNotEmpty()) {
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
    
    private fun parseSections(sectionsStr: String): List<Int> {
        val sections = mutableListOf<Int>()
        try {
            val cleanStr = sectionsStr.replace("节", "").trim()
            if (cleanStr.isEmpty()) return listOf(1)
            
            cleanStr.split(",").forEach { part ->
                if (part.contains("-")) {
                    val range = part.split("-")
                    val start = range[0].trim().toIntOrNull() ?: 1
                    val end = range.getOrNull(1)?.trim()?.toIntOrNull() ?: start
                    for (i in start..end) {
                        sections.add(i)
                    }
                } else {
                    part.trim().toIntOrNull()?.let { sections.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析节次失败: $sectionsStr", e)
            return listOf(1)
        }
        return sections.sorted()
    }
}



















