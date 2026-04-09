package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

/**
 * 荆州理工职业学院课表提取器
 * 系统：CRP系统
 */
@Singleton
class JZLGZYXYExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "jzlgzyxy"
    override val schoolName = "荆州理工职业学院"
    override val systemType = "crp"
    
    companion object {
        private const val TAG = "JZLGZYXYExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("jzlgedu.cn", ignoreCase = true) &&
               html.contains("st_p.aspx")
    }
    
    override fun getLoginUrl(): String = "http://jwgl.jzlgedu.cn/"
    override fun getScheduleUrl(): String? = null
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取${schoolName}课表...');
                    console.log('⚠️ 注意：此系统需要逐周获取数据，可能需要较长时间');
                    
                    async function fetchCourseData() {
                        // 获取总周数（用户输入或默认20周）
                        let totalWeeks = prompt('请输入本学期总共有多少周？', '20');
                        totalWeeks = parseInt(totalWeeks) || 20;
                        
                        if (totalWeeks <= 0 || totalWeeks > 30) {
                            return JSON.stringify({error: '周数必须在1-30之间'});
                        }
                        
                        console.log('📌 总周数:', totalWeeks);
                        
                        let result = [];
                        let htmstr = document.documentElement.outerHTML;
                        
                        // 逐周获取课表
                        for (let i = 0; i < totalWeeks; i++) {
                            console.log('获取第', (i + 1), '周...');
                            
                            let formDom = new DOMParser().parseFromString(htmstr, "text/html");
                            let tables = formDom.getElementsByTagName("table");
                            
                            if (tables.length < 3) {
                                console.warn('第', (i + 1), '周数据格式异常');
                                break;
                            }
                            
                            let trs = tables[2].getElementsByTagName("tr");
                            
                            // 解析5个时间段（上午1-4节，下午5-8节，晚上9-10节，周末，其他）
                            for (let j = 1; j <= 5; j++) {
                                if (j >= trs.length) continue;
                                
                                let tds = trs[j].getElementsByTagName("td");
                                
                                // 解析7天
                                for (let k = 1; k <= 7; k++) {
                                    if (k >= tds.length) continue;
                                    
                                    let spans = tds[k].getElementsByTagName("span");
                                    if (spans.length === 0) continue;
                                    
                                    let kcsText = spans[0].innerHTML
                                        .replace(/\s/g, "")
                                        .split(/<br><br>/)
                                        .slice(0, -1);
                                    
                                    kcsText.forEach(v => {
                                        let kcarr = v.split("<br>");
                                        let re = {
                                            name: "",
                                            teacher: "",
                                            position: "",
                                            weeks: [i + 1],
                                            day: k,
                                            sections: []
                                        };
                                        
                                        kcarr.forEach(con => {
                                            let parts = con.split("：");
                                            if (parts.length < 2) return;
                                            
                                            switch (parts[0]) {
                                                case "时间":
                                                    if (j != 5) {
                                                        let jcStr = parts[1].split(",")[1];
                                                        if (jcStr) {
                                                            let jc = jcStr.replace(/第|节/g, "").split('-');
                                                            let start = parseInt(jc[0]) || 1;
                                                            let end = parseInt(jc[jc.length - 1]) || start;
                                                            for (let y = start; y <= end; y++) {
                                                                re.sections.push(y);
                                                            }
                                                        }
                                                    } else {
                                                        // 周末或晚上，默认1-10节
                                                        for (let y = 1; y <= 10; y++) {
                                                            re.sections.push(y);
                                                        }
                                                    }
                                                    break;
                                                case "课程名称":
                                                    re.name = parts[1];
                                                    break;
                                                case "任课老师":
                                                    re.teacher = parts[1].replace(/老师/g, "");
                                                    break;
                                                case "课室":
                                                    re.position = parts[1].split("(").slice(0, -1).join("(");
                                                    break;
                                            }
                                        });
                                        
                                        if (re.name) {
                                            result.push(re);
                                        }
                                    });
                                }
                            }
                            
                            // 获取下一周
                            if (i < totalWeeks - 1) {
                                let form = formDom.getElementById("form1");
                                let formData = new FormData(form);
                                formData.set("__EVENTTARGET", "LinkButton_下一周");
                                
                                let response = await fetch('/st/student/st_p.aspx', {
                                    method: 'POST',
                                    body: formData
                                });
                                htmstr = await response.text();
                            }
                        }
                        
                        console.log('✅ 获取完成，共', result.length, '条记录');
                        return JSON.stringify(result);
                    }
                    
                    return fetchCourseData();
                } catch (error) {
                    console.error('❌ 提取失败:', error);
                    return JSON.stringify({error: '提取失败: ' + error.message});
                }
            })();
        """.trimIndent()
    }
    
    override fun parseCourses(jsonData: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        try {
            AppLogger.d(TAG, "开始解析${schoolName}课程数据...")
            
            val cleanJson = jsonData.trim()
                .removePrefix("\"").removeSuffix("\"")
                .replace("\\\"", "\"")
                .replace("\\n", "")
                .replace("\\r", "")
            
            // 检查是否是错误信息
            if (cleanJson.startsWith("{") && cleanJson.contains("error")) {
                val errorObj = JSONObject(cleanJson)
                throw Exception(errorObj.getString("error"))
            }
            
            val jsonArray = JSONArray(cleanJson)
            
            for (i in 0 until jsonArray.length()) {
                val course = jsonArray.getJSONObject(i)
                
                val courseName = course.optString("name", "")
                if (courseName.isEmpty()) continue
                
                val teacher = course.optString("teacher", "")
                val classroom = course.optString("position", "")
                val dayOfWeek = course.optInt("day", 1)
                
                // 解析周次数组
                val weeksArray = course.optJSONArray("weeks")
                val weeks = mutableListOf<Int>()
                if (weeksArray != null) {
                    for (j in 0 until weeksArray.length()) {
                        weeks.add(weeksArray.getInt(j))
                    }
                }
                
                // 解析节次数组
                val sectionsArray = course.optJSONArray("sections")
                val sections = mutableListOf<Int>()
                if (sectionsArray != null) {
                    for (j in 0 until sectionsArray.length()) {
                        sections.add(sectionsArray.getInt(j))
                    }
                }
                
                courses.add(ParsedCourse(
                    courseName = courseName.trim(),
                    teacher = teacher.trim(),
                    classroom = classroom.trim(),
                    dayOfWeek = dayOfWeek,
                    startSection = sections.minOrNull() ?: 1,
                    sectionCount = sections.size,
                    weeks = weeks,
                    weekExpression = weeks.joinToString(",") + "周"
                ))
            }
            
            val resolvedCourses = resolveCourseConflicts(courses)
            
            AppLogger.d(TAG, "✅ 成功解析 ${resolvedCourses.size} 门课程")
            return resolvedCourses
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析课程数据失败", e)
            throw e
        }
    }
    
    /**
     * 合并相同课程的不同周次
     */
    private fun resolveCourseConflicts(rawCourses: List<ParsedCourse>): List<ParsedCourse> {
        val courseMap = mutableMapOf<String, ParsedCourse>()
        
        for (course in rawCourses) {
            val key = "${course.courseName}|${course.teacher}|${course.classroom}|${course.dayOfWeek}|${course.startSection}"
            
            if (courseMap.containsKey(key)) {
                val existing = courseMap.getValue(key)
                val mergedWeeks = (existing.weeks + course.weeks).distinct().sorted()
                courseMap[key] = existing.copy(
                    weeks = mergedWeeks,
                    weekExpression = mergedWeeks.joinToString(",") + "周"
                )
            } else {
                courseMap[key] = course
            }
        }
        
        return courseMap.values.toList()
    }
}


















