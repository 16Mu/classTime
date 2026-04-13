package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

/**
 * 长春大学课表提取器
 * 系统：强智教务系统-iframe版本
 * 参考：temp_aishedule/强智教务/iframe强智/长春大学/provider.js
 */
@Singleton
class CCDXExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "ccdx"
    override val schoolName = "长春大学"
    override val systemType = "qiangzhi"

    override val aliases = listOf("长春大学")
    override val supportedUrls = listOf("jwgl.ccu.edu.cn")
    
    companion object {
        private const val TAG = "CCDXExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return (url.contains("/jsxsd/xskb/xskb_list.do") || url.contains("ccu.edu.cn")) &&
               (html.contains("kbtable") || html.contains("content_box"))
    }
    
    override fun getLoginUrl(): String = "http://jwgl.ccu.edu.cn/"
    override fun getScheduleUrl(): String = "http://jwgl.ccu.edu.cn/jsxsd/xskb/xskb_list.do"
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取长春大学课表（强智iframe）...');
                    
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
                                week1.push(Str.slice(0, index + 2).replace(/周|\s/g, ''));
                                index += 2;
                            } else {
                                week1.push(Str.slice(0, index + 1).replace(/周|\s/g, ''));
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
                    
                    function getSection(Str) {
                        let reJc = [];
                        let strArr = Str.replace('节', '').trim().split('-');
                        if (strArr.length <= 2) {
                            for (let i = Number(strArr[0]); i <= Number(strArr[strArr.length - 1]); i++) {
                                reJc.push(Number(i));
                            }
                        } else {
                            strArr.forEach((v) => reJc.push(Number(v)));
                        }
                        return reJc;
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
                                let parts = innerHTML.split(/<br>/i);
                                
                                let re = { weeks: [], sections: [] };
                                let nameTag = true;
                                let nameAfter = 1;
                                
                                parts.forEach((part) => {
                                    let tempDiv = document.createElement('div');
                                    tempDiv.innerHTML = part;
                                    
                                    if (tempDiv.textContent.trim().length === 0) return;
                                    
                                    let fonts = tempDiv.querySelectorAll('font');
                                    if (fonts.length === 0) {
                                        fonts = [tempDiv];
                                    }
                                    
                                    let visibleFonts = Array.from(fonts).filter(f => {
                                        let style = f.getAttribute('style');
                                        return !style || style.indexOf('display:none') === -1;
                                    });
                                    
                                    if (visibleFonts.length === 0) return;
                                    
                                    visibleFonts.forEach((font) => {
                                        re.day = day + 1;
                                        nameAfter += 1;
                                        
                                        let title = font.getAttribute('title');
                                        if (!title && nameAfter > 1) title = '课程';
                                        
                                        let text = font.textContent.trim();
                                        
                                        switch(title) {
                                            case '课程':
                                                if (nameTag) {
                                                    re.name = text.replace(/\([a-z]+\d+.*?\)/, '');
                                                    nameTag = false;
                                                    nameAfter = 0;
                                                } else {
                                                    result.push(JSON.parse(JSON.stringify(re)));
                                                    re = { weeks: [], sections: [] };
                                                    nameTag = true;
                                                }
                                                break;
                                            case '老师':
                                            case '教师':
                                                re.teacher = text;
                                                break;
                                            case '教室':
                                            case '教学楼':
                                                re.position = (re.position || '') + text;
                                                break;
                                            case '周次(节次)':
                                                let weekPart = text.split('[')[0];
                                                re.weeks = getWeeks(weekPart);
                                                let jcMatch = text.match(/\[(.*?)\]/);
                                                if (jcMatch) {
                                                    re.sections = getSection(jcMatch[1]);
                                                } else {
                                                    for (let jie = jcIndex * 2; jie <= jcIndex * 2 + 1; jie++) {
                                                        re.sections.push(jie);
                                                    }
                                                }
                                                break;
                                        }
                                    });
                                });
                                
                                if (re.name) {
                                    result.push(re);
                                }
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

















