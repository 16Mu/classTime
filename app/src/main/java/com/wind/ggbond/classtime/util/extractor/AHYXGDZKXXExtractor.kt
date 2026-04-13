package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

/**
 * 安徽医学高等专科学校课表提取器
 * 系统：金智教务系统
 * 参考：temp_aishedule/金智教务/安徽医学高等专科学校/provider.js
 */
@Singleton
class AHYXGDZKXXExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "ahyxgdzkxx"
    override val schoolName = "安徽医学高等专科学校"
    override val systemType = "jinzhi"

    override val aliases = listOf("安徽医学高等专科学校")
    override val supportedUrls = listOf("jwgl.ahyz.cn")
    
    companion object {
        private const val TAG = "AHYXGDZKXXExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("wdkb") ||
               url.contains("xskcb") ||
               html.contains("kcb_container") ||
               html.contains("dqxnxq")
    }
    
    override fun getLoginUrl(): String {
        return "http://jwgl.ahyz.cn/"
    }
    
    override fun getScheduleUrl(): String {
        return "/jwapp/sys/wdkb/modules/xskcb/xskcb.do"
    }
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取安徽医学高等专科学校课表（金智系统）...');
                    
                    // 获取当前学年学期
                    var dqxnxq2 = document.getElementById('dqxnxq2');
                    var xnxq = '';
                    
                    if (dqxnxq2) {
                        xnxq = dqxnxq2.getAttribute('value') || dqxnxq2.value;
                        console.log('从页面获取学年学期: ' + xnxq);
                    } else {
                        // 请求获取当前学年学期
                        console.log('请求获取当前学年学期');
                        var response = await fetch("/jwapp/sys/wdkb/modules/jshkcb/dqxnxq.do", {
                            method: 'POST',
                            headers: {
                                'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
                            }
                        });
                        var result = await response.json();
                        if (result.datas && result.datas.dqxnxq && result.datas.dqxnxq.rows && result.datas.dqxnxq.rows.length > 0) {
                            xnxq = result.datas.dqxnxq.rows[0].DM;
                            console.log('从API获取学年学期: ' + xnxq);
                        } else {
                            return JSON.stringify({courses: [], error: '无法获取当前学年学期'});
                        }
                    }
                    
                    // 访问我的课表页面
                    await fetch("/appShow?appId=4770397878132218", {
                        method: 'GET'
                    });
                    
                    // 获取课表数据
                    console.log('获取课表数据，XNXQDM: ' + xnxq);
                    var formData = "XNXQDM=" + xnxq;
                    var response2 = await fetch("/jwapp/sys/wdkb/modules/xskcb/xskcb.do", {
                        method: 'POST',
                        body: formData,
                        headers: {
                            'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
                        }
                    });
                    
                    var courseData = await response2.json();
                    console.log('获取到课表数据:', courseData);
                    
                    if (!courseData.datas || !courseData.datas.xskcb || !courseData.datas.xskcb.rows) {
                        return JSON.stringify({courses: [], error: '未获取到课表数据'});
                    }
                    
                    var coursesList = courseData.datas.xskcb.rows;
                    var courses = [];
                    
                    coursesList.forEach(function(course) {
                        var weeks = [];
                        var sections = [];
                        
                        // 解析周次字符串
                        var skzc = course.SKZC || '';
                        for (var i = 0; i < skzc.length; i++) {
                            if (skzc[i] === '1') {
                                weeks.push(i + 1);
                            }
                        }
                        
                        // 解析节次
                        var startSection = course.KSJC || 1;
                        var endSection = course.JSJC || startSection;
                        for (var i = startSection; i <= endSection; i++) {
                            sections.push(i);
                        }
                        
                        var courseName = course.KCM || '';
                        if (course.TYXMDM_DISPLAY) {
                            courseName += '(' + course.TYXMDM_DISPLAY + ')';
                        }
                        
                        courses.push({
                            courseName: courseName,
                            teacher: course.SKJS || '',
                            classroom: course.JASMC || '',
                            dayOfWeek: parseInt(course.SKXQ) || 1,
                            weeks: weeks,
                            sections: sections,
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
            AppLogger.d(TAG, "开始解析安徽医学高等专科学校课程数据...")
            
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


















