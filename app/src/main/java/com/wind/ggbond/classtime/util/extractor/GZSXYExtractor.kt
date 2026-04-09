package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.util.WeekParser
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

/**
 * 广州商学院课表提取器
 * 系统：正方教务系统（旧版）
 * 参考：temp_aishedule/正方教务/正方教务/广州商学院/provider.js
 */
@Singleton
class GZSXYExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "gzsxy"
    override val schoolName = "广州商学院"
    override val systemType = "zfsoft"
    
    companion object {
        private const val TAG = "GZSXYExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("xskbcx") || 
               html.contains("Table1") ||
               html.contains("学生个人课表")
    }
    
    override fun getLoginUrl(): String {
        return "http://jw.gzcc.cn/"
    }
    
    override fun getScheduleUrl(): String {
        return "/xskbcx.aspx"
    }
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取广州商学院课表...');
                    
                    var table = null;
                    var dqwz = document.getElementById("dqwz");
                    
                    if (!dqwz) {
                        return JSON.stringify({courses: [], error: '未找到位置标识，请确保在课表页面'});
                    }
                    
                    var text = dqwz.textContent || dqwz.innerText;
                    
                    // 检查是否在学生个人课表页面
                    if (text === "学生个人课表") {
                        console.log('在学生个人课表页面');
                        var zhutiFrame = window.frames["zhuti"];
                        if (zhutiFrame && zhutiFrame.document) {
                            table = zhutiFrame.document.getElementById("Table1");
                        }
                    } else {
                        console.log('通过导航获取课表');
                        var navxl = document.getElementById("navxl");
                        if (!navxl) {
                            return JSON.stringify({courses: [], error: '未找到导航元素'});
                        }
                        
                        var match = navxl.outerHTML.match(/xskbcx\\.aspx([^"]+)/);
                        if (!match) {
                            return JSON.stringify({courses: [], error: '未找到课表URL'});
                        }
                        
                        var kbUrl = "xskbcx.aspx" + match[1].replace(/&amp;/g, "&");
                        console.log('课表URL: ' + kbUrl);
                        
                        // 使用fetch获取课表页面
                        var response = await fetch(kbUrl, {
                            method: 'GET'
                        });
                        var htmlText = await response.text();
                        var parser = new DOMParser();
                        var htmlDom = parser.parseFromString(htmlText, 'text/html');
                        table = htmlDom.getElementById("Table1");
                    }
                    
                    if (!table) {
                        return JSON.stringify({courses: [], error: '未找到课表表格Table1'});
                    }
                    
                    console.log('找到表格，开始解析...');
                    
                    var courses = [];
                    var days = ["", "一", "二", "三", "四", "五", "六", "日"];
                    var tbody = table.querySelector('tbody');
                    
                    if (!tbody) {
                        return JSON.stringify({courses: [], error: '未找到表格tbody'});
                    }
                    
                    var trs = tbody.querySelectorAll('tr');
                    console.log('找到 ' + trs.length + ' 行');
                    
                    // 从第3行开始（跳过表头）
                    for (var i = 2; i < trs.length; i++) {
                        var tr = trs[i];
                        var tds = tr.querySelectorAll('td[align="center"]');
                        
                        tds.forEach(function(td, colIndex) {
                            var text = td.textContent.trim();
                            if (text.length <= 6) return; // 跳过空单元格
                            
                            var html = td.innerHTML;
                            // 按多个<br>分割课程
                            var kc = html.split(/<br\s*\/?>\s*<br\s*\/?>/);
                            
                            kc.forEach(function(con) {
                                if (!con.trim()) return;
                                
                                var singleCon = con.split(/<br\s*\/?>/);
                                if (singleCon.length < 5) return;
                                
                                var course = {
                                    courseName: '',
                                    teacher: '',
                                    classroom: '',
                                    dayOfWeek: 0,
                                    weekExpression: '',
                                    sections: [],
                                    credit: 0
                                };
                                
                                course.courseName = singleCon[0].trim();
                                course.teacher = singleCon[3] ? singleCon[3].trim() : '';
                                course.classroom = singleCon[4] ? singleCon[4].trim() : '';
                                
                                // 解析星期和节次、周次
                                var dayTimeStr = singleCon[2] ? singleCon[2].trim() : '';
                                if (dayTimeStr) {
                                    var dayChar = dayTimeStr.slice(1, 2);
                                    course.dayOfWeek = days.indexOf(dayChar);
                                    
                                    // 提取节次（格式：第1,2,3节）
                                    var sectionsPart = dayTimeStr.slice(2).split('{')[0];
                                    course.sections = parseSections(sectionsPart);
                                    
                                    // 提取周次（格式：{1-16周}）
                                    var weeksMatch = dayTimeStr.match(/\\{([^}]+)\\}/);
                                    if (weeksMatch) {
                                        course.weekExpression = weeksMatch[1].trim();
                                    }
                                }
                                
                                if (course.courseName && course.dayOfWeek > 0 && course.sections.length > 0) {
                                    courses.push(course);
                                }
                            });
                        });
                    }
                    
                    console.log('✅ 提取完成，共 ' + courses.length + ' 门课程');
                    return JSON.stringify({courses: courses});
                    
                } catch (error) {
                    console.error('❌ 提取失败:', error);
                    return JSON.stringify({courses: [], error: '提取失败: ' + error.message});
                }
                
                function parseSections(str) {
                    str = str.replace(/第|节/g, '').trim();
                    var sections = [];
                    var parts = str.split(',');
                    
                    parts.forEach(function(part) {
                        var num = parseInt(part.trim());
                        if (!isNaN(num) && num > 0) {
                            sections.push(num);
                        }
                    });
                    
                    return sections;
                }
                
                function getWeeks(str) {
                    function range(con, tag) {
                        var retWeek = [];
                        if (con.length <= 1) return retWeek;
                        
                        con.slice(0, -1).split(',').forEach(function(w) {
                            var tt = w.split('-');
                            var start = parseInt(tt[0]);
                            var end = tt.length > 1 ? parseInt(tt[tt.length - 1]) : start;
                            
                            for (var z = start; z <= end; z++) {
                                if (tag === 1 || tag === 2) {
                                    if (z % tag === 0) retWeek.push(z);
                                } else {
                                    if (z % 2 !== 0) retWeek.push(z);
                                }
                            }
                        });
                        
                        return retWeek;
                    }
                    
                    str = str.replace(/[(){}|第\\[\\]]/g, '').replace(/到/g, '-');
                    var reWeek = [];
                    var week1 = [];
                    
                    while (str.search(/周|\\s/) !== -1) {
                        var index = str.search(/周|\\s/);
                        if (str[index + 1] === '单' || str[index + 1] === '双') {
                            week1.push(str.slice(0, index + 2).replace(/周|\\s/g, ''));
                            index += 2;
                        } else {
                            week1.push(str.slice(0, index + 1).replace(/周|\\s/g, ''));
                            index += 1;
                        }
                        
                        str = str.slice(index);
                        var nextIndex = str.search(/\\d/);
                        if (nextIndex !== -1) str = str.slice(nextIndex);
                        else str = '';
                    }
                    
                    if (str.length !== 0) week1.push(str);
                    
                    week1.forEach(function(v) {
                        if (v.slice(-1) === "双") {
                            reWeek = reWeek.concat(range(v, 2));
                        } else if (v.slice(-1) === "单") {
                            reWeek = reWeek.concat(range(v, 3));
                        } else {
                            reWeek = reWeek.concat(range(v + "全", 1));
                        }
                    });
                    
                    return reWeek;
                }
            })();
        """.trimIndent()
    }
    
    override fun parseCourses(jsonData: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        
        try {
            AppLogger.d(TAG, "开始解析广州商学院课程数据...")
            
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
                
                if (courseName.isNotEmpty() && weeks.isNotEmpty() && sections.isNotEmpty() && dayOfWeek > 0) {
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


















