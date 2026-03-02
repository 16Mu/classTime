package com.wind.ggbond.classtime.util.extractor

import android.util.Log
import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 广东交通职业技术学院课表提取器
 * 系统：乘方教务系统
 */
@Singleton
class GDJTZYJSXYExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "gdjtzyjsxy"
    override val schoolName = "广东交通职业技术学院"
    override val systemType = "chengfang"
    
    companion object {
        private const val TAG = "GDJTZYJSXYExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("gdcp.cn", ignoreCase = true) &&
               (html.contains("xsgrkbcx") || html.contains("getKbRq"))
    }
    
    override fun getLoginUrl(): String = "http://jw.gdcp.cn/"
    override fun getScheduleUrl(): String? = null
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取${schoolName}课表...');
                    
                    async function fetchCourseData() {
                        // 获取学年学期代码
                        let wdkbFrame = window.frames['xsgrkbcx!xsgrkbMain.action'];
                        if (!wdkbFrame) {
                            return JSON.stringify({error: '未找到课表frame，请确保在课表页面'});
                        }
                        
                        let wdkbDoc = wdkbFrame.document.getElementById("wdkb").contentWindow.document;
                        let xnxqdmValue = wdkbDoc.getElementById("xnxqdm").value;
                        
                        console.log('📌 学年学期:', xnxqdmValue);
                        
                        // 获取课表数据
                        let url = "http://jw.gdcp.cn/xsgrkbcx!getKbRq.action?xnxqdm=" + xnxqdmValue;
                        let response = await fetch(url);
                        let data = await response.json();
                        let re = data[0];
                        
                        // 合并相同课程的不同周次
                        let kcMap = new Map();
                        let result = [];
                        
                        for (let key in re) {
                            let res = {
                                name: re[key].kcmc,
                                teacher: re[key].teaxms,
                                position: re[key].jxcdmc,
                                day: re[key].xq,
                                weeks: [Number(re[key].zc)],
                                sections: re[key].jcdm2.split(",").map(v => Number(v))
                            };
                            
                            let mapKey = res.name + res.teacher + res.position + res.day + res.sections.join(",");
                            if (!kcMap.get(mapKey)) {
                                kcMap.set(mapKey, res);
                            } else {
                                kcMap.get(mapKey).weeks.push(res.weeks[0]);
                            }
                        }
                        
                        for (let [key, value] of kcMap) {
                            result.push(value);
                        }
                        
                        console.log('✅ 获取到', result.length, '门课程');
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
            Log.d(TAG, "开始解析${schoolName}课程数据...")
            
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
                    weeks = weeks.distinct().sorted(),
                    weekExpression = weeks.distinct().sorted().joinToString(",") + "周"
                ))
            }
            
            val resolvedCourses = resolveCourseConflicts(courses)
            
            Log.d(TAG, "✅ 成功解析 ${resolvedCourses.size} 门课程")
            return resolvedCourses
        } catch (e: Exception) {
            Log.e(TAG, "解析课程数据失败", e)
            throw e
        }
    }
    
    /**
     * 合并相同课程
     */
    private fun resolveCourseConflicts(rawCourses: List<ParsedCourse>): List<ParsedCourse> {
        val courseMap = mutableMapOf<String, ParsedCourse>()
        
        for (course in rawCourses) {
            val key = "${course.courseName}|${course.teacher}|${course.classroom}|${course.dayOfWeek}|${course.startSection}"
            
            if (courseMap.containsKey(key)) {
                val existing = courseMap[key]!!
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


















