package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

@Singleton
class YTNSXYExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "ytnsxy"
    override val schoolName = "烟台南山学院"
    override val systemType = "kingosoft"

    override val aliases = listOf("烟台南山学院", "南山学院")
    override val supportedUrls = listOf("jwxt.nanshan.edu.cn")
    
    companion object {
        private const val TAG = "YTNSXYExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("nanshan.edu.cn") && (url.contains("xskb") || html.contains("frmDesk"))
    }
    
    override fun getLoginUrl(): String = "http://jwxt.nanshan.edu.cn/"
    override fun getScheduleUrl(): String = "http://jwxt.nanshan.edu.cn/jwglxt/student/xskb"
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取烟台南山学院课表（青果教务）...');
                    
                    function getWeeks(Str) {
                        function range(con, tag) {
                            let retWeek = [];
                            con.slice(0, -1).split(',').forEach((w) => {
                                let tt = w.split('-');
                                let start = parseInt(tt[0]);
                                let end = parseInt(tt[tt.length - 1]);
                                if (tag === 1 || tag === 2) {
                                    retWeek.push(...Array(end + 1 - start).fill(start).map((x, y) => x + y).filter((f) => f % tag === 0));
                                } else {
                                    retWeek.push(...Array(end + 1 - start).fill(start).map((x, y) => x + y).filter((v) => v % 2 !== 0));
                                }
                            });
                            return retWeek;
                        }
                        
                        Str = Str.replace(/[(){}|第\[\]]/g, '').replace(/到/g, '-');
                        let reWeek = [];
                        let week1 = [];
                        while (Str.search(/周|\s/) !== -1) {
                            let index = Str.search(/周|\s/);
                            if (Str[index + 1] === '单' || Str[index + 1] === '双') {
                                week1.push(Str.slice(0, index + 2).replace('周', ''));
                                index += 2;
                            } else {
                                week1.push(Str.slice(0, index + 1).replace('周', ''));
                                index += 1;
                            }
                            Str = Str.slice(index);
                            index = Str.search(/\d/);
                            if (index !== -1) Str = Str.slice(index);
                            else Str = '';
                        }
                        if (Str.length !== 0) week1.push(Str);
                        
                        week1.forEach((v) => {
                            if (v.slice(-1) === '双') reWeek.push(...range(v, 2));
                            else if (v.slice(-1) === '单') reWeek.push(...range(v, 3));
                            else reWeek.push(...range(v + '全', 1));
                        });
                        return reWeek;
                    }
                    
                    function getjc(Str) {
                        let jc = [];
                        let jcar = Str.replace(/节|\[|\]/g, '').split('-');
                        for (let i = Number(jcar[0]); i <= Number(jcar[jcar.length - 1]); i++) {
                            jc.push(i);
                        }
                        return jc;
                    }
                    
                    let html = '';
                    let bz = '';
                    
                    try {
                        if (window.frames && window.frames['frmDesk']) {
                            let frmDesk = window.frames['frmDesk'];
                            
                            if (frmDesk.frames['frame_1'] && frmDesk.frames['frame_1'].contentDocument.getElementById('cxfs_ewb')) {
                                let frame1Doc = frmDesk.frames['frame_1'].contentDocument;
                                let lb = frame1Doc.getElementById('cxfs_lb');
                                let ewb = frame1Doc.getElementById('cxfs_ewb');
                                if (lb && lb.checked) {
                                    bz = '列表';
                                } else if (ewb && ewb.checked) {
                                    bz = '二维表';
                                }
                                
                                if (frmDesk.frames['frame_1'].contentWindow.frames['frmReport']) {
                                    let frmReportDoc = frmDesk.frames['frame_1'].contentWindow.frames['frmReport'].document;
                                    let tables = frmReportDoc.getElementsByTagName('table');
                                    for (let i = 0; i < tables.length; i++) {
                                        html += tables[i].outerHTML;
                                    }
                                }
                            }
                        }
                    } catch (e) {
                        console.error('提取课表出错:', e);
                    }
                    
                    if (!html) {
                        console.error('未找到课表');
                        return JSON.stringify({courses: [], error: '未找到课表'});
                    }
                    
                    let parser = new DOMParser();
                    let doc = parser.parseFromString(html, 'text/html');
                    let result = [];
                    
                    if (bz === '二维表') {
                        let trs = doc.querySelectorAll('tr');
                        trs.forEach((tr) => {
                            let tds = tr.querySelectorAll('.td');
                            let dayIndex = 1;
                            tds.forEach((td) => {
                                let divs = td.querySelectorAll('div');
                                divs.forEach((div) => {
                                    if (div.textContent.trim().length === 0) return;
                                    
                                    let content = div.innerHTML.split('<br>');
                                    if (content.length < 3) return;
                                    
                                    let jcMatch = content[2].match(/\[(.*?)\]/);
                                    if (!jcMatch) return;
                                    
                                    let week = content[2].split('[')[0];
                                    let fonts = div.querySelectorAll('font');
                                    let courseName = fonts.length > 0 ? fonts[0].textContent.trim() : '';
                                    if (courseName.indexOf('网络课') !== -1) return;
                                    
                                    result.push({
                                        name: courseName,
                                        teacher: content[1],
                                        position: content[3],
                                        sections: getjc(jcMatch[1]),
                                        weeks: getWeeks(week),
                                        day: dayIndex
                                    });
                                });
                                dayIndex++;
                            });
                        });
                    }
                    
                    console.log('✅ 提取到 ' + result.length + ' 门课程');
                    return JSON.stringify({courses: result});
                    
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
            val jsonObject = JSONObject(jsonData)
            val coursesArray = jsonObject.optJSONArray("courses") ?: JSONArray()
            
            for (i in 0 until coursesArray.length()) {
                val courseObj = coursesArray.getJSONObject(i)
                
                val name = courseObj.optString("name", "")
                if (name.isEmpty()) continue
                val teacher = courseObj.optString("teacher", "")
                val classroom = courseObj.optString("position", "")
                val day = courseObj.optInt("day", 1)
                
                val weeksArray = courseObj.optJSONArray("weeks") ?: JSONArray()
                val weeks = mutableListOf<Int>()
                for (j in 0 until weeksArray.length()) {
                    weeks.add(weeksArray.getInt(j))
                }
                
                val sectionsArray = courseObj.optJSONArray("sections") ?: JSONArray()
                val sections = mutableListOf<Int>()
                for (j in 0 until sectionsArray.length()) {
                    sections.add(sectionsArray.getInt(j))
                }
                
                if (weeks.isNotEmpty() && sections.isNotEmpty()) {
                    courses.add(
                        ParsedCourse(
                            courseName = name,
                            teacher = teacher,
                            classroom = classroom,
                            dayOfWeek = day,
                            startSection = sections.minOrNull() ?: 1,
                            sectionCount = sections.size,
                            weeks = weeks,
                            weekExpression = "${weeks.minOrNull()}-${weeks.maxOrNull()}周"
                        )
                    )
                }
            }
            
            AppLogger.d(TAG, "成功解析 ${courses.size} 门课程")
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析课程数据失败", e)
            throw e
        }
        return courses
    }
}

















