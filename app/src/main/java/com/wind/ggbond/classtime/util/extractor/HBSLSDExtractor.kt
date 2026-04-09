package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

/**
 * 华北水利水电大学课表提取器
 * 系统：青果教务系统
 * 参考：temp_aishedule/青果教务/华北水利水电/provider.js
 */
@Singleton
class HBSLSDExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "hbslsd"
    override val schoolName = "华北水利水电大学"
    override val systemType = "kingosoft"
    
    companion object {
        private const val TAG = "HBSLSDExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("ncwu.edu.cn") && 
               (url.contains("xskb") || url.contains("wsxk.xskcb") || html.contains("frmDesk"))
    }
    
    override fun getLoginUrl(): String = "http://hsjw.ncwu.edu.cn/"
    override fun getScheduleUrl(): String = "http://hsjw.ncwu.edu.cn/hsjw/student/xskb"
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取华北水利水电大学课表（青果教务）...');
                    
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
                    
                    function cton(str) {
                        let arr = ['', '一', '二', '三', '四', '五', '六', '七', '日'];
                        return arr.indexOf(str) === 8 ? arr.indexOf(str) - 1 : arr.indexOf(str);
                    }
                    
                    // 尝试从不同位置获取课表
                    let html = '';
                    let bz = '';
                    
                    try {
                        // 从frmDesk获取
                        if (window.frames && window.frames['frmDesk']) {
                            let frmDesk = window.frames['frmDesk'];
                            
                            // 检查是否在index页面
                            if (frmDesk.document.getElementById('xnxq') && 
                                !frmDesk.frames['frame_1'] && 
                                !frmDesk.frames['frmReport']) {
                                // index模式，需要额外获取数据
                                let lessonContent = frmDesk.document.getElementById('lessonSchedule-content');
                                if (lessonContent) {
                                    html = lessonContent.outerHTML;
                                    bz = 'index';
                                }
                            } else if (frmDesk.frames['frame_1']) {
                                // 有frame_1，检查格式选择
                                let frame1Doc = frmDesk.frames['frame_1'].contentDocument;
                                if (frame1Doc.getElementById('cxfs_ewb')) {
                                    let lb = frame1Doc.getElementById('cxfs_lb');
                                    let ewb = frame1Doc.getElementById('cxfs_ewb');
                                    if (lb && lb.checked) {
                                        bz = '列表';
                                    } else if (ewb && ewb.checked) {
                                        bz = '二维表';
                                    }
                                }
                                
                                if (frmDesk.frames['frame_1'].contentWindow.frames['frmReport']) {
                                    let frmReportDoc = frmDesk.frames['frame_1'].contentWindow.frames['frmReport'].document;
                                    let tables = frmReportDoc.getElementsByTagName('table');
                                    for (let i = 0; i < tables.length; i++) {
                                        html += tables[i].outerHTML;
                                    }
                                }
                            } else if (frmDesk.frames['frmReport']) {
                                let frmReportDoc = frmDesk.frames['frmReport'].document;
                                let tables = frmReportDoc.getElementsByTagName('table');
                                for (let i = 0; i < tables.length; i++) {
                                    html += tables[i].outerHTML;
                                }
                                let selGS = frmDesk.document.getElementById('selGS');
                                if (selGS) bz = selGS.value;
                            }
                        }
                    } catch (e) {
                        console.error('提取课表出错:', e);
                    }
                    
                    if (!html) {
                        console.error('未找到课表');
                        return JSON.stringify({courses: [], error: '未找到课表'});
                    }
                    
                    // 解析课表
                    let parser = new DOMParser();
                    let doc = parser.parseFromString(html, 'text/html');
                    let result = [];
                    
                    if (bz === '二维表') {
                        let trs = doc.querySelectorAll('tr');
                        trs.forEach((tr, index) => {
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
                    } else if (bz === '列表') {
                        let trs = doc.querySelectorAll('tr');
                        trs.forEach((tr) => {
                            let tds = tr.querySelectorAll('td:not([style*="display: none"])');
                            if (tds.length !== 10) return;
                            if (tds[0].textContent.slice(0, 1).search(/\d/) === -1) return;
                            if (tds[8].textContent.indexOf('未排课') !== -1) return;
                            
                            let names = tds[1].textContent.split(']');
                            let teacher = tds[5].textContent.split(']');
                            let courseName = names[names.length - 1];
                            if (courseName.indexOf('网络课') !== -1) return;
                            
                            let schedules = tds[8].textContent.split(/\(\d+\),?|\s{1},/).filter(v => v && v.trim());
                            schedules.forEach((sch) => {
                                let arr = sch.split(' ').filter(v => v && v.trim());
                                if (arr.length < 3) return;
                                
                                let jcMatch = arr[1].match(/\[(.*?)\]/);
                                if (!jcMatch) return;
                                
                                result.push({
                                    name: courseName,
                                    teacher: teacher[teacher.length - 1],
                                    position: arr[2],
                                    weeks: getWeeks(arr[0]),
                                    day: cton(arr[1].slice(0, 1)),
                                    sections: getjc(jcMatch[0])
                                });
                            });
                        });
                    } else if (bz === 'index') {
                        let weeklessons = doc.querySelectorAll('div.weeklesson');
                        weeklessons.forEach((weeklesson) => {
                            let id = weeklesson.getAttribute('id').replace('weekly', '');
                            let dayjie = id.split('_');
                            let uls = weeklesson.querySelectorAll('ul');
                            
                            uls.forEach((ul) => {
                                let re = { weeks: [], sections: [] };
                                let lis = ul.querySelectorAll('li');
                                
                                lis.forEach((li) => {
                                    let text = li.textContent;
                                    let parts = text.split('：');
                                    if (parts.length < 2) return;
                                    
                                    let label = parts[0];
                                    let value = li.querySelector('b') ? li.querySelector('b').textContent : '';
                                    
                                    switch(label) {
                                        case '课程名称':
                                            re.name = value;
                                            break;
                                        case '任课教师':
                                            re.teacher = value;
                                            break;
                                        case '上课地点':
                                            re.position = value;
                                            break;
                                        case '上课时间':
                                            re.day = parseInt(dayjie[0]);
                                            let timeMatches = value.match(/\[.*?\]/g);
                                            if (timeMatches && timeMatches.length >= 2) {
                                                re.sections = getjc(timeMatches[1]);
                                                let weekMatch = value.match(/\(.*?\)/);
                                                re.weeks = getWeeks(timeMatches[0] + (weekMatch ? weekMatch[0] : ''));
                                            }
                                            break;
                                    }
                                });
                                
                                if (re.name && re.weeks.length > 0 && re.sections.length > 0) {
                                    result.push(re);
                                }
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

















