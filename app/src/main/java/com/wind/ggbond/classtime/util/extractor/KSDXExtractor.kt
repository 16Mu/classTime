package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.util.WeekParser
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

/**
 * 喀什大学课表提取器
 * 系统：新正方教务系统
 * 参考：temp_aishedule/正方教务/新正方教务/喀什大学/provider.js
 */
@Singleton
class KSDXExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "ksdx"
    override val schoolName = "喀什大学"
    override val systemType = "zfsoft"

    override val aliases = listOf("喀什大学")
    override val supportedUrls = listOf("jwgl.ksu.edu.cn")
    
    companion object {
        private const val TAG = "KSDXExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("xskbcx") || 
               url.contains("courseTableForStd") ||
               html.contains("ajaxForm")
    }
    
    override fun getLoginUrl(): String {
        return "http://jwgl.ksu.edu.cn/"
    }
    
    override fun getScheduleUrl(): String {
        return "/jwglxt/kbcx/xskbcxMobile_cxXsKb.html"
    }
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取喀什大学课表...');
                    
                    var htt = null;
                    var xnm = '';
                    var xqm = '';
                    
                    // 尝试直接从当前页面获取
                    try {
                        var forms = document.getElementById('ajaxForm');
                        if (forms) {
                            xnm = forms.xnm.value;
                            xqm = forms.xqm.value;
                            
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
                        } else {
                            throw new Error('未找到ajaxForm');
                        }
                    } catch (e) {
                        console.log('直接获取失败，尝试通过导航菜单: ' + e.message);
                        
                        // 通过导航菜单查找
                        var cdNav = document.getElementById('cdNav');
                        if (!cdNav) {
                            return JSON.stringify({courses: [], error: '未找到导航菜单，请确保在学生课表查询（按周次）页面'});
                        }
                        
                        var matches = cdNav.outerHTML.match(/clickMenu\((.*?)\);/g);
                        var id = '';
                        
                        if (matches) {
                            for (var i = 0; i < matches.length; i++) {
                                if (matches[i].indexOf('学生课表查询（按周次）') != -1 || matches[i].indexOf('学生课表查询') != -1) {
                                    var parts = matches[i].match(/clickMenu\('([^']+)'/);
                                    if (parts && parts.length > 1) {
                                        id = parts[1];
                                        break;
                                    }
                                }
                            }
                        }
                        
                        if (!id) {
                            return JSON.stringify({courses: [], error: '未找到学生课表查询菜单'});
                        }
                        
                        console.log('找到菜单ID: ' + id);
                        
                        var response = await fetch('/jwglxt/kbcx/xskbcxMobile_cxXsKb.html?gnmkdm=' + id, {
                            method: 'GET'
                        });
                        var html = await response.text();
                        console.log('获取到页面HTML');
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
            AppLogger.d(TAG, "开始解析喀什大学课程数据...")
            
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


















