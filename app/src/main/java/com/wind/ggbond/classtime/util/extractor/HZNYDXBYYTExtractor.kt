package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.util.WeekParser
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

/**
 * 华中农业大学-本研一体化课表提取器
 * 系统：个性化正方教务系统
 * 参考：temp_aishedule/正方教务/个性化正方/华中农业大学-本研一体化/provider.js
 */
@Singleton
class HZNYDXBYYTExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "hznydx_byyt"
    override val schoolName = "华中农业大学-本研一体化"
    override val systemType = "zfsoft"
    
    companion object {
        private const val TAG = "HZNYDXBYYTExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("index_initMenu") || 
               url.contains("bjkbdy") ||
               html.contains("dqxnxq") ||
               html.contains("ylkbTable")
    }
    
    override fun getLoginUrl(): String {
        return "http://jwgl.hzau.edu.cn/"
    }
    
    override fun getScheduleUrl(): String {
        return "/kbcx/xskbcx_cxXsKb.html"
    }
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取华中农业大学-本研一体化课表...');
                    
                    var htt = null;
                    var xnm = '';
                    var xqm = '';
                    var tag = 'json';
                    var currentUrl = location.href;
                    
                    // 检查是否在首页（index_initMenu）
                    if (currentUrl.search("index_initMenu") != -1) {
                        console.log('在首页，使用JSON模式');
                        var dqxnxq = document.getElementById("dqxnxq");
                        if (!dqxnxq) {
                            return JSON.stringify({courses: [], error: '未找到学年学期选择器'});
                        }
                        
                        xqm = dqxnxq.value;
                        var xqxh = xqm.split("-");
                        xnm = xqxh[0];
                        xqm = xqxh[1];
                        
                        console.log('学年: ' + xnm + ', 学期: ' + xqm);
                        
                        var formData = "localeKey=zh_CN&xnm=" + xnm + "&xqm=" + xqm;
                        var response = await fetch("/kbcx/xskbcx_cxXsKb.html?gnmkdm=index", {
                            method: 'POST',
                            body: formData,
                            headers: {
                                'Content-Type': 'application/x-www-form-urlencoded'
                            }
                        });
                        var result = await response.json();
                        htt = result.kbList || [];
                        tag = 'json';
                    } 
                    // 检查是否在推荐课表查询页（HTML模式）
                    else if (currentUrl.search("bjkbdy_cxBjkbdyIndex") != -1) {
                        console.log('在推荐课表查询页，使用HTML模式');
                        var ylkbTable = document.getElementById("ylkbTable");
                        if (!ylkbTable) {
                            return JSON.stringify({courses: [], error: '未找到预览课表表格'});
                        }
                        htt = ylkbTable.outerHTML;
                        tag = 'html';
                        
                        // HTML模式暂不支持，返回提示
                        return JSON.stringify({courses: [], error: 'HTML模式暂不支持，请使用首页JSON模式'});
                    } 
                    else {
                        return JSON.stringify({courses: [], error: '暂不支持此页面，请使用首页或推荐课表查询页'});
                    }
                    
                    // JSON模式处理
                    if (tag === 'json' && htt && htt.length > 0) {
                        var courses = [];
                        htt.forEach(function(course) {
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
                    }
                    
                    return JSON.stringify({courses: [], error: '未获取到课表数据'});
                    
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
            AppLogger.d(TAG, "开始解析华中农业大学-本研一体化课程数据...")
            
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
                val sectionsStr = courseObj.optString("sections", "")
                
                val weeks = if (weekExpression.isNotEmpty()) {
                    WeekParser.parseWeekExpression(weekExpression)
                } else {
                    emptyList()
                }
                
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
            
            AppLogger.d(TAG, "解析完成，共 ${courses.size} 门课程")
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析课程数据失败", e)
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
            AppLogger.e(TAG, "解析节次失败: $sectionsStr", e)
            return listOf(1)
        }
        return sections.sorted()
    }
}


















