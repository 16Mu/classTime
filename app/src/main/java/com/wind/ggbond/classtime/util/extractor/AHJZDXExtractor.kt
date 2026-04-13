package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.util.WeekParser
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

/**
 * 安徽建筑大学课表提取器
 * 系统：新正方教务系统（HTML解析模式）
 * 参考：temp_aishedule/正方教务/新正方教务/安徽建筑大学/Provider.js
 */
@Singleton
class AHJZDXExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "ahjzdx"
    override val schoolName = "安徽建筑大学"
    override val systemType = "zfsoft"

    override val aliases = listOf("安徽建筑大学")
    override val supportedUrls = listOf("jwgl.ahjzu.edu.cn")
    
    companion object {
        private const val TAG = "AHJZDXExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("xskbcx") || 
               html.contains("ylkbTable") ||
               html.contains("tab-pane")
    }
    
    override fun getLoginUrl(): String {
        return "http://jwgl.ahjzu.edu.cn/"
    }
    
    override fun getScheduleUrl(): String {
        return "/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html"
    }
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取安徽建筑大学课表...');
                    
                    var ylkbTable = document.getElementById("ylkbTable");
                    if (!ylkbTable) {
                        return JSON.stringify({courses: [], error: '未找到预览课表表格，请确保在学生课表查询页面'});
                    }
                    
                    var courses = [];
                    var activeTab = document.querySelector('div.tab-pane.fade.active.in');
                    
                    if (!activeTab) {
                        return JSON.stringify({courses: [], error: '未找到活动标签页'});
                    }
                    
                    var tabId = activeTab.getAttribute('id');
                    console.log('活动标签页ID: ' + tabId);
                    
                    // 解析table1模式（网格视图）
                    if (tabId === 'table1') {
                        var trs = activeTab.querySelectorAll('table tbody tr');
                        console.log('找到 ' + trs.length + ' 行');
                        
                        for (var i = 2; i < trs.length; i++) {
                            var tr = trs[i];
                            var tdWraps = tr.querySelectorAll('.td_wrap');
                            
                            tdWraps.forEach(function(tdWrap) {
                                var weekdayId = tdWrap.getAttribute('id');
                                if (!weekdayId) return;
                                    
                                var weekday = parseInt(weekdayId.slice(0, 1));
                                var divs = tdWrap.querySelectorAll('div');
                                
                                divs.forEach(function(div) {
                                    var course = {
                                        courseName: '',
                                        teacher: '',
                                        classroom: '',
                                        dayOfWeek: weekday,
                                        weekExpression: '',
                                        sections: [],
                                        credit: 0
                                    };
                                    
                                    // 提取课程名
                                    var titleSpan = div.querySelector('span.title');
                                    var uTag = div.querySelector('u');
                                    if (titleSpan) {
                                        course.courseName = titleSpan.textContent.trim();
                                    } else if (uTag) {
                                        course.courseName = uTag.textContent.trim();
                                    }
                                    
                                    // 移除最后一个字符并截取前40字节
                                    if (course.courseName.length > 0) {
                                        course.courseName = course.courseName.slice(0, -1);
                                    }
                                    course.courseName = cutOutStringByteLength(course.courseName, 40);
                                    
                                    // 提取课程详情
                                    var pTags = div.querySelectorAll('p');
                                    pTags.forEach(function(p) {
                                        var span = p.querySelector('span');
                                        if (!span) return;
                                        
                                        var title = span.getAttribute('title');
                                        var text = p.textContent.trim();
                                        
                                        switch(title) {
                                            case '节/周':
                                                var match1 = text.match(/\\(([^)]+)\\)/);
                                                var match2 = text.match(/节\\)(.+)$/);
                                                if (match1) {
                                                    course.sections = parseSections(match1[1]);
                                                }
                                                if (match2) {
                                                    course.weekExpression = match2[1].trim();
                                                }
                                                break;
                                            case '上课地点':
                                                course.classroom = text.replace(/北校区|南校区/g, '').trim();
                                                course.classroom = cutOutStringByteLength(course.classroom, 40);
                                                break;
                                            case '教师':
                                                course.teacher = text.replace(/\\([^)]*\\)/g, '').trim();
                                                course.teacher = cutOutStringByteLength(course.teacher, 40);
                                                break;
                                        }
                                    });
                                    
                                    if (course.courseName && course.weekExpression && course.sections.length > 0) {
                                        courses.push(course);
                                    }
                                });
                            });
                        }
                    }
                    // 解析table2模式（列表视图）
                    else if (tabId === 'table2') {
                        var tbs = activeTab.querySelectorAll('table tbody');
                        
                        tbs.forEach(function(tb) {
                            var tbId = tb.getAttribute('id');
                            if (!tbId) return;
                            
                            var dayOfWeek = parseInt(tbId.replace('xq_', ''));
                            var trs = tb.querySelectorAll('tr');
                            
                            for (var i = 1; i < trs.length; i++) {
                                var tr = trs[i];
                                var course = {
                                    courseName: '',
                                    teacher: '',
                                    classroom: '',
                                    dayOfWeek: dayOfWeek,
                                    weekExpression: '',
                                    sections: [],
                                    credit: 0
                                };
                                
                                var tds = tr.querySelectorAll('td');
                                var spTr = null;
                                
                                if (tds.length === 2) {
                                    var sectionsText = tds[0].textContent.trim();
                                    course.sections = parseSections(sectionsText);
                                    spTr = tds[1].querySelector('div');
                                } else {
                                    spTr = tds[0].querySelector('div');
                                }
                                
                                if (!spTr) continue;
                                
                                // 提取课程名
                                var titleSpan = spTr.querySelector('span.title');
                                var uTag = spTr.querySelector('u');
                                if (titleSpan) {
                                    course.courseName = titleSpan.textContent.trim();
                                } else if (uTag) {
                                    course.courseName = uTag.textContent.trim();
                                }
                                if (course.courseName.length > 0) {
                                    course.courseName = course.courseName.slice(0, -1);
                                }
                                course.courseName = cutOutStringByteLength(course.courseName, 40);
                                
                                // 提取详情
                                var fontTags = spTr.querySelectorAll('p font');
                                fontTags.forEach(function(font) {
                                    var span = font.querySelector('span:last-child');
                                    if (!span) return;
                                    
                                    var className = span.getAttribute('class');
                                    var text = font.textContent.trim();
                                    
                                    switch(className) {
                                        case 'glyphicon glyphicon-calendar':
                                            course.weekExpression = text.replace('周数：', '').trim();
                                            break;
                                        case 'glyphicon glyphicon-map-marker':
                                            course.classroom = text.replace(/校区：|南校区|北校区|上课地点：/g, '').trim();
                                            course.classroom = cutOutStringByteLength(course.classroom, 40);
                                            break;
                                        case 'glyphicon glyphicon-user':
                                            course.teacher = text.replace(/教师：|\\([^)]*\\)/g, '').trim();
                                            course.teacher = cutOutStringByteLength(course.teacher, 40);
                                            break;
                                    }
                                });
                                
                                if (course.courseName && course.weekExpression && course.sections.length > 0) {
                                    courses.push(course);
                                }
                            }
                        });
                    }
                    
                    console.log('✅ 提取完成，共 ' + courses.length + ' 门课程');
                    return JSON.stringify({courses: courses});
                    
                } catch (error) {
                    console.error('❌ 提取失败:', error);
                    return JSON.stringify({courses: [], error: '提取失败: ' + error.message});
                }
                
                function parseSections(str) {
                    str = str.replace(/节/g, '').trim();
                    var sections = [];
                    var arr = str.split('-');
                    var start = parseInt(arr[0]);
                    var end = arr.length > 1 ? parseInt(arr[arr.length - 1]) : start;
                    for (var i = start; i <= end; i++) {
                        sections.push(i);
                    }
                    return sections;
                }
                
                function cutOutStringByteLength(str, long) {
                    var seeString = [];
                    var countByteLength = 0;
                    for (var i = 0; i < str.length; i++) {
                        var charCode = str.charCodeAt(i);
                        if (charCode >= 0 && charCode <= 128) {
                            countByteLength += 1;
                        } else {
                            countByteLength += 2;
                        }
                        if (countByteLength <= long) {
                            seeString.push(str[i]);
                        } else {
                            break;
                        }
                    }
                    return seeString.join('');
                }
            })();
        """.trimIndent()
    }
    
    override fun parseCourses(jsonData: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        
        try {
            AppLogger.d(TAG, "开始解析安徽建筑大学课程数据...")
            
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
                
                // 解析节次数组
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


















