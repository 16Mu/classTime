package com.wind.ggbond.classtime.util.extractor

import android.util.Log
import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.util.WeekParser
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 东华理工大学课表提取器
 * 系统：新正方教务系统
 * 参考：temp_aishedule/正方教务/新正方教务/东华理工大学/provider.js
 */
@Singleton
class DHLGDXExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "dhlgdx"
    override val schoolName = "东华理工大学"
    override val systemType = "zfsoft"
    
    companion object {
        private const val TAG = "DHLGDXExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("xskbcx") || 
               url.contains("courseTableForStd") ||
               html.contains("ajaxForm")
    }
    
    override fun getLoginUrl(): String {
        return "http://jwc.ecut.edu.cn/"
    }
    
    override fun getScheduleUrl(): String {
        return "/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html"
    }
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取东华理工大学课表...');
                    
                    var htt = null;
                    var xnm = '';
                    var xqm = '';
                    var url = location.href;
                    
                    // 检查是否在移动端页面
                    if (url.search("xskbcxMobile_cxXskbcxIndex") != -1 || url.search("xskbcxMobile_cxTimeTableIndex") != -1) {
                        xnm = document.getElementById("xnm_hide").value;
                        xqm = document.getElementById("xqm_hide").value;
                        
                        console.log('移动端页面，学年: ' + xnm + ', 学期: ' + xqm);
                        
                        // 发送请求获取课表
                        var formData = "xnm=" + xnm + "&zs=1&doType=app&xqm=" + xqm + "&kblx=2";
                        var response = await fetch("/jwglxt/kbcx/xskbcxMobile_cxXsgrkb.html?sf_request_type=ajax", {
                            method: 'POST',
                            body: formData,
                            headers: {
                                'Content-Type': 'application/x-www-form-urlencoded'
                            }
                        });
                        htt = await response.json();
                    } else {
                        // PC端页面
                        var forms = document.getElementById('ajaxForm');
                        if (!forms) {
                            return JSON.stringify({courses: [], error: '未找到ajaxForm，请确保在课表查询页面'});
                        }
                        
                        xnm = forms.xnm.value;
                        xqm = forms.xqm.value;
                        
                        console.log('PC端页面，学年: ' + xnm + ', 学期: ' + xqm);
                        
                        // 发送请求获取课表
                        var formData = 'xnm=' + xnm + '&xqm=' + xqm;
                        var response = await fetch('/jwglxt/kbcx/xskbcxMobile_cxXsKb.html', {
                            method: 'POST',
                            body: formData,
                            headers: {
                                'Content-Type': 'application/x-www-form-urlencoded'
                            }
                        });
                        htt = await response.json();
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
            Log.d(TAG, "开始解析东华理工大学课程数据...")
            
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
                
                // 解析节次（格式如 "1-2节" 或 "3,5节"）
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
    
    /**
     * 解析节次字符串
     * 格式：
     * - "1-2节" -> [1, 2]
     * - "3,5节" -> [3, 5]
     * - "1-3,5节" -> [1, 2, 3, 5]
     */
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



















