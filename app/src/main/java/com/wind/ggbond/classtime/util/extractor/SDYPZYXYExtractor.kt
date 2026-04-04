package com.wind.ggbond.classtime.util.extractor

import android.util.Log
import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 山东药品职业学院课表提取器
 * 系统：自研教务系统
 */
@Singleton
class SDYPZYXYExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "sdypzyxy"
    override val schoolName = "山东药品职业学院"
    override val systemType = "custom"
    
    companion object {
        private const val TAG = "SDYPZYXYExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("sddfvc.cn", ignoreCase = true) &&
               (html.contains("mobile_kcb") || html.contains("api_token"))
    }
    
    override fun getLoginUrl(): String = "http://jwxt.sddfvc.cn/"
    override fun getScheduleUrl(): String? = null
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取${schoolName}课表...');
                    
                    async function fetchCourseData() {
                        // 获取token
                        let token = location.href.split("=")[1];
                        if (!token) {
                            let scripts = document.getElementsByTagName("script");
                            let scriptText = scripts[scripts.length - 1].outerHTML;
                            let match = scriptText.match(/(?<=api_token:').*?(?=')/);
                            token = match ? match[0] : '';
                        }
                        
                        if (!token) {
                            return JSON.stringify({error: '无法获取token，请确保已登录'});
                        }
                        
                        console.log('📌 Token获取成功');
                        
                        // 获取学期信息
                        let xqUrl = "http://jwxt.sddfvc.cn/mobile/student/mobile_kcb_xq?api_token=" + token;
                        let xqResponse = await fetch(xqUrl);
                        let xqData = await xqResponse.json();
                        
                        let xqid;
                        let selects = document.getElementsByTagName("select");
                        if (selects.length > 0) {
                            xqid = selects[0].value;
                        } else {
                            xqid = xqData.data.xq_current.id;
                        }
                        
                        console.log('📌 学期ID:', xqid);
                        
                        // 获取课表数据
                        let kbUrl = "http://jwxt.sddfvc.cn/mobile/student/mobile_kcb?api_token=" + token + "&xq=" + xqid;
                        let kbResponse = await fetch(kbUrl);
                        let result = await kbResponse.json();
                        
                        console.log('✅ 数据获取完成');
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
            
            val jsonObject = JSONObject(cleanJson)
            
            if (jsonObject.has("error")) {
                throw Exception(jsonObject.getString("error"))
            }
            
            if (!jsonObject.has("data")) {
                throw Exception("数据格式错误：缺少data字段")
            }
            
            val courseInfo = jsonObject.getJSONObject("data")
            
            // 遍历7天的课程
            for (day in 1..7) {
                val dayKey = "xq$day"
                if (!courseInfo.has(dayKey)) continue
                
                val xqCourses = courseInfo.getJSONObject(dayKey)
                val jieciKeys = xqCourses.keys()
                
                while (jieciKeys.hasNext()) {
                    val jieci = jieciKeys.next()
                    val jieciCourses = xqCourses.getJSONObject(jieci)
                    
                    val courseKeys = jieciCourses.keys()
                    while (courseKeys.hasNext()) {
                        val courseKey = courseKeys.next()
                        val course = jieciCourses.getJSONObject(courseKey)
                        
                        // 解析课程名称
                        val pkbmc = course.getString("pkbmc")
                        val nameParts = pkbmc.split("#")
                        val courseName = if (nameParts[0].contains(Regex("\\d+级.*?班"))) {
                            nameParts.getOrNull(1) ?: nameParts[0]
                        } else {
                            nameParts[0]
                        }
                        
                        // 解析排课明细
                        val pkmx = course.getJSONObject("pkmx")
                        val pkmxKeys = pkmx.keys()
                        
                        while (pkmxKeys.hasNext()) {
                            val pkKey = pkmxKeys.next()
                            val pkDetail = pkmx.getJSONObject(pkKey)
                            
                            val classroom = pkDetail.optString("classroom", "")
                            
                            // 解析教师
                            val teacherList = mutableListOf<String>()
                            val teacherArray = pkDetail.optJSONArray("teacher")
                            if (teacherArray != null) {
                                for (i in 0 until teacherArray.length()) {
                                    val teacherObj = teacherArray.getJSONObject(i)
                                    teacherList.add(teacherObj.optString("xm", ""))
                                }
                            }
                            val teacher = teacherList.joinToString(",")
                            
                            // 解析周次
                            val zc = pkDetail.getJSONObject("zc")
                            val weeks = parseWeeksFromZC(zc)
                            
                            courses.add(ParsedCourse(
                                courseName = courseName.trim(),
                                teacher = teacher.trim(),
                                classroom = classroom.trim(),
                                dayOfWeek = day,
                                startSection = jieci.toIntOrNull() ?: 1,
                                sectionCount = 2,
                                weeks = weeks,
                                weekExpression = weeks.joinToString(",") + "周"
                            ))
                        }
                    }
                }
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
     * 解析周次对象
     * dsz: 1=单周, 2=双周, 其他=全周
     * zc: 周次字符串
     */
    private fun parseWeeksFromZC(zcObj: JSONObject): List<Int> {
        val weeks = mutableListOf<Int>()
        val dsz = zcObj.optInt("dsz", 0)
        val zcStr = zcObj.optString("zc", "")
        
        if (zcStr.isEmpty()) return listOf(1)
        
        val cleaned = zcStr.replace(Regex("[(){}|第到]"), "")
        val parts = cleaned.split(",")
        
        for (part in parts) {
            val rangeParts = part.split("-")
            val start = rangeParts.getOrNull(0)?.toIntOrNull() ?: continue
            val end = rangeParts.getOrNull(1)?.toIntOrNull() ?: start
            
            for (week in start..end) {
                when (dsz) {
                    1 -> if (week % 2 == 1) weeks.add(week) // 单周
                    2 -> if (week % 2 == 0) weeks.add(week) // 双周
                    else -> weeks.add(week) // 全周
                }
            }
        }
        
        return weeks.distinct().sorted()
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


















