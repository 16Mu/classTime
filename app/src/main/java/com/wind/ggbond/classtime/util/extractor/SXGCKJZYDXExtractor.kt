package com.wind.ggbond.classtime.util.extractor

import android.util.Log
import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 山西工程科技职业大学课表提取器
 * 系统：自研教务系统
 */
@Singleton
class SXGCKJZYDXExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "sxgckjzydx"
    override val schoolName = "山西工程科技职业大学"
    override val systemType = "custom"
    
    companion object {
        private const val TAG = "SXGCKJZYDXExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("sxgy.cn", ignoreCase = true) ||
               (html.contains("Tresources") && html.contains("GetXsKb"))
    }
    
    override fun getLoginUrl(): String = "http://jwgl.sxgy.cn/"
    override fun getScheduleUrl(): String? = null
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取${schoolName}课表...');
                    
                    async function fetchData() {
                        let kbjson = '';
                        
                        try {
                            // 尝试从iframe获取数据
                            let iframes = document.getElementsByTagName('iframe');
                            for (let i = 0; i < iframes.length; i++) {
                                if (iframes[i].style.display !== 'none') {
                                    try {
                                        let iframeDoc = iframes[i].contentDocument;
                                        let zxjxjhhInput = iframeDoc.getElementById('Zxjxjhh');
                                        if (zxjxjhhInput) {
                                            let Zxjxjhh = zxjxjhhInput.value;
                                            let response = await fetch('/Tresources/A1Xskb/GetXsKb', {
                                                method: 'POST',
                                                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                                                body: 'Zxjxjhh=' + Zxjxjhh
                                            });
                                            kbjson = await response.json();
                                            break;
                                        }
                                    } catch(e) {
                                        console.log('尝试iframe失败:', e);
                                    }
                                }
                            }
                            
                            // 如果iframe方式失败，直接请求
                            if (!kbjson || !kbjson.rows) {
                                let response = await fetch('/Tresources/A1Xskb/GetXsKb', {
                                    method: 'POST',
                                    headers: {'Content-Type': 'application/x-www-form-urlencoded'}
                                });
                                kbjson = await response.json();
                            }
                        } catch (e) {
                            console.error('获取课表失败:', e);
                            return JSON.stringify({error: '获取课表失败: ' + e.message});
                        }
                        
                        console.log('✅ 获取到', kbjson.rows ? kbjson.rows.length : 0, '条课程记录');
                        return JSON.stringify(kbjson.rows || []);
                    }
                    
                    return fetchData();
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
                val kb = jsonArray.getJSONObject(i)
                
                val courseName = kb.optString("Kcm", "")
                if (courseName.isEmpty()) continue
                
                val teacher = kb.optString("Jsm", "")
                val dayOfWeek = kb.optInt("Skxq", 1)
                val classroom = kb.optString("Jxlm", "") + kb.optString("Jasm", "")
                val startSection = kb.optInt("Skjc", 1)
                val sectionCount = kb.optInt("Cxjc", 2)
                
                // 解析周次（位字符串）
                val skzc = kb.optString("Skzc", "")
                val weeks = parseWeeksBitString(skzc)
                
                courses.add(ParsedCourse(
                    courseName = courseName.trim(),
                    teacher = teacher.trim(),
                    classroom = classroom.trim(),
                    dayOfWeek = dayOfWeek,
                    startSection = startSection,
                    sectionCount = sectionCount,
                    weeks = weeks,
                    weekExpression = weeks.joinToString(",") + "周"
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
     * 解析周次位字符串
     * 例如: "111000111" -> [1,2,3,7,8,9]
     * 每个位表示一周，1表示有课，0表示无课
     */
    private fun parseWeeksBitString(bitString: String): List<Int> {
        val weeks = mutableListOf<Int>()
        
        for ((index, char) in bitString.withIndex()) {
            if (char == '1') {
                weeks.add(index + 1)
            }
        }
        
        return weeks
    }
    
    /**
     * 合并相同课程的不同周次
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


















