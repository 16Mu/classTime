package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

/**
 * 衡阳师范学院课表提取器
 * 系统：强智教务系统-iframe版本
 * 参考：temp_aishedule/强智教务/iframe强智/衡阳师范学院/timer.js
 */
@Singleton
class HYSFXYExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "hysfxy"
    override val schoolName = "衡阳师范学院"
    override val systemType = "qiangzhi"
    
    companion object {
        private const val TAG = "HYSFXYExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return (url.contains("/jsxsd/xskb/xskb_list.do") || url.contains("hynu.edu.cn")) &&
               (html.contains("kbtable") || html.contains("content_box"))
    }
    
    override fun getLoginUrl(): String = "http://jwgl.hynu.edu.cn/"
    override fun getScheduleUrl(): String = "http://jwgl.hynu.edu.cn/jsxsd/xskb/xskb_list.do"
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取衡阳师范学院课表（强智iframe）...');
                    
                    function getWeeks(weekStr) {
                        let weekss = weekStr.replace(/第|\(|\)/g, '');
                        let week1 = [];
                        while (weekss.search(/周/) !== -1) {
                            let zindex = weekss.search(/周/);
                            week1.push(weekss.slice(0, zindex + 1).replace('周', ''));
                            if (weekss[zindex + 1] === undefined) {
                                weekss = '';
                            } else if (weekss[zindex + 1].search(/\d/) !== -1) {
                                weekss = weekss.slice(zindex + 1);
                            } else {
                                weekss = weekss.slice(zindex + 2);
                            }
                        }
                        week1.push(weekss);
                        
                        let reweek = [];
                        week1.filter(function(s) { return s && s.trim(); }).forEach((v) => {
                            if (v.substring(v.length - 1) === '双') {
                                v.substring(0, v.length - 1).split(',').forEach((w) => {
                                    let tt = w.split('-').filter(function(s) { return s && s.trim(); });
                                    for (let z = Number(tt[0]); z <= Number(tt[tt.length - 1]); z++) {
                                        if (z % 2 === 0) reweek.push(z);
                                    }
                                });
                            } else if (v.substring(v.length - 1) === '单') {
                                v.substring(0, v.length - 1).split(',').forEach((w) => {
                                    let tt = w.split('-').filter(function(s) { return s && s.trim(); });
                                    for (let z = Number(tt[0]); z <= Number(tt[tt.length - 1]); z++) {
                                        if (z % 2 !== 0) reweek.push(z);
                                    }
                                });
                            } else {
                                v.split(',').forEach((w) => {
                                    let tt = w.split('-').filter(function(s) { return s && s.trim(); });
                                    for (let z = Number(tt[0]); z <= Number(tt[tt.length - 1]); z++) {
                                        reweek.push(z);
                                    }
                                });
                            }
                        });
                        return reweek;
                    }
                    
                    function getSection(Str) {
                        let rejc = [];
                        Str = Str.replace('节', '').trim().split('-');
                        Str.forEach((v) => rejc.push(Number(v)));
                        return rejc;
                    }
                    
                    let html = '';
                    let iframes = document.getElementsByTagName('iframe');
                    let found = false;
                    
                    for (let i = 0; i < iframes.length; i++) {
                        let iframe = iframes[i];
                        if (iframe.src && iframe.src.indexOf('/jsxsd/xskb/xskb_list.do') !== -1) {
                            try {
                                let contentDoc = iframe.contentDocument || iframe.contentWindow.document;
                                let kbtable = contentDoc.getElementById('kbtable');
                                if (kbtable) {
                                    html = kbtable.outerHTML;
                                } else {
                                    let contentBox = contentDoc.getElementsByClassName('content_box')[0];
                                    if (contentBox) html = contentBox.outerHTML;
                                }
                                found = true;
                                break;
                            } catch (e) {
                                console.log('无法访问iframe内容:', e);
                            }
                        }
                    }
                    
                    if (!found) {
                        let kbtable = document.getElementById('kbtable');
                        if (kbtable) {
                            html = kbtable.outerHTML;
                            found = true;
                        }
                    }
                    
                    if (!html) {
                        console.error('未找到课表');
                        return JSON.stringify({courses: [], error: '未找到课表'});
                    }
                    
                    let parser = new DOMParser();
                    let doc = parser.parseFromString(html, 'text/html');
                    let result = [];
                    
                    let rows = doc.querySelectorAll('tbody tr');
                    rows.forEach((row, jcIndex) => {
                        let cells = row.querySelectorAll('td');
                        cells.forEach((cell, day) => {
                            let kbcontents = cell.querySelectorAll('div.kbcontent');
                            if (kbcontents.length === 0 || cell.textContent.trim().length <= 6) return;
                            
                            kbcontents.forEach((kbcontent) => {
                                let innerHTML = kbcontent.innerHTML;
                                // 使用 --- 分割课程块
                                let courseBlocks = innerHTML.split(/---+/);
                                
                                courseBlocks.forEach((courseBlock) => {
                                    let conarr = courseBlock.split('<br>').filter(function(s) { return s && s.trim(); });
                                    
                                    let re = { weeks: [], sections: [] };
                                    re.day = day + 1;
                                    
                                    conarr.forEach((em, index) => {
                                        if (index === 0) {
                                            let nameDiv = document.createElement('div');
                                            nameDiv.innerHTML = em;
                                            re.name = nameDiv.textContent.trim();
                                        } else {
                                            // 提取老师
                                            let teacherMatch = em.match(/(?<=title="老师">).*?(?=<)|(?<=title="教师">).*?(?=<)/);
                                            if (teacherMatch) {
                                                re.teacher = teacherMatch[0].replace(/无职称|（高校）/g, '');
                                            }
                                            
                                            // 提取教室
                                            let positionMatch = em.match(/(?<=title="教室">).*?(?=<)/);
                                            if (positionMatch) {
                                                re.position = positionMatch[0].replace('(主校区)', '');
                                            }
                                            
                                            // 提取周次和节次
                                            let weekSectionMatch = em.match(/(?<=title="周次\(节次\)">).*?(?=<)/);
                                            if (weekSectionMatch) {
                                                let text = weekSectionMatch[0];
                                                re.weeks = getWeeks(text.split('[')[0]);
                                                
                                                let jcMatch = text.match(/(?<=\[).*?(?=\])/);
                                                if (jcMatch) {
                                                    re.sections = getSection(jcMatch[0]);
                                                } else {
                                                    for (let jie = jcIndex * 2; jie <= jcIndex * 2 + 1; jie++) {
                                                        re.sections.push(jie);
                                                    }
                                                }
                                            }
                                        }
                                    });
                                    
                                    if (re.name && re.weeks.length > 0 && re.sections.length > 0) {
                                        result.push(JSON.parse(JSON.stringify(re)));
                                    }
                                });
                            });
                        });
                    });
                    
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

















