package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

/**
 * 南京中医药大学课表提取器
 * 系统：自研教务系统
 */
@Singleton
class NJZYYDXExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "njzyydx"
    override val schoolName = "南京中医药大学"
    override val systemType = "custom"

    override val aliases = listOf("南京中医药大学")
    override val supportedUrls = listOf("njucm.edu.cn")
    
    companion object {
        private const val TAG = "NJZYYDXExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("njucm.edu.cn", ignoreCase = true) &&
               (url.contains("course-table", ignoreCase = true) ||
                html.contains("student/for-std/course-table"))
    }
    
    override fun getLoginUrl(): String = "https://jwgl.njucm.edu.cn/"
    override fun getScheduleUrl(): String = "https://jwgl.njucm.edu.cn/student/for-std/course-table"
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取${schoolName}课表...');
                    
                    async function fetchData() {
                        // 获取基础页面信息
                        let preUrl = location.origin;
                        let ctResponse = await fetch(preUrl + '/student/for-std/course-table');
                        let cthtml = await ctResponse.text();
                        
                        // 提取必要参数
                        let bizTypeId = cthtml.match(/(?<=const bizTypeId \= ).*?(?=;)/)[0];
                        let semesterMatch = cthtml.match(/(?<="selected" value=").*?(?="\>)/);
                        let semesterId = semesterMatch ? semesterMatch[0] : '';
                        let stdPersonId = cthtml.match(/(?<=var personId \= ).*?(?=;)/)[0];
                        let studentId = cthtml.match(/(?<=var dataId = ).*?(?=;)/)[0];
                        
                        console.log('📌 参数提取完成');
                        
                        // 获取课程数据
                        let kcUrl = preUrl + '/student/for-std/course-table/get-data?bizTypeId=' + bizTypeId + '&semesterId=' + semesterId;
                        let kcResponse = await fetch(kcUrl);
                        let kcjson = await kcResponse.json();
                        
                        // 获取时间表布局
                        let timeUrl = preUrl + '/student/ws/schedule-table/timetable-layout';
                        let timeResponse = await fetch(timeUrl, {
                            method: 'POST',
                            headers: {'Content-Type': 'application/json'},
                            body: JSON.stringify({timeTableLayoutId: kcjson.timeTableLayoutId})
                        });
                        let starTime = await timeResponse.json();
                        let startTimeJson = starTime.result.courseUnitList;
                        
                        // 获取课程详细信息
                        let data = {
                            lessonIds: kcjson.lessonIds,
                            stdPersonId: Number(stdPersonId),
                            studentId: studentId == 'null' ? null : studentId,
                            weekIndex: null
                        };
                        
                        let daumUrl = preUrl + '/student/ws/schedule-table/datum';
                        let daumResponse = await fetch(daumUrl, {
                            method: 'POST',
                            headers: {'Content-Type': 'application/json'},
                            body: JSON.stringify(data)
                        });
                        let daum = await daumResponse.json();
                        
                        console.log('✅ 数据获取完成');
                        
                        return JSON.stringify({
                            courseJson: daum.result,
                            startTimeJson: startTimeJson
                        });
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
            AppLogger.d(TAG, "开始解析${schoolName}课程数据...")
            
            // 清理JSON字符串
            val cleanJson = jsonData.trim()
                .removePrefix("\"").removeSuffix("\"")
                .replace("\\\"", "\"")
                .replace("\\n", "")
                .replace("\\r", "")
            
            val jsonObject = JSONObject(cleanJson)
            
            if (jsonObject.has("error")) {
                throw Exception(jsonObject.getString("error"))
            }
            
            val allJson = jsonObject
            val startTimeJson = allJson.getJSONArray("startTimeJson")
            val courseJson = allJson.getJSONObject("courseJson")
            val scheduleList = courseJson.getJSONArray("scheduleList")
            val lessonList = courseJson.getJSONArray("lessonList")
            
            // 构建时间映射表 (startTime -> indexNo)
            val timeMap = mutableMapOf<String, Int>()
            for (i in 0 until startTimeJson.length()) {
                val timeObj = startTimeJson.getJSONObject(i)
                timeMap[timeObj.getString("startTime")] = timeObj.getInt("indexNo")
            }
            
            // 构建课程名称映射表 (lessonId -> courseName)
            val lessonMap = mutableMapOf<String, String>()
            for (i in 0 until lessonList.length()) {
                val lessonObj = lessonList.getJSONObject(i)
                lessonMap[lessonObj.getString("id")] = lessonObj.getString("courseName")
            }
            
            // 解析课程表
            val rawCourses = mutableListOf<ParsedCourse>()
            for (i in 0 until scheduleList.length()) {
                val schedule = scheduleList.getJSONObject(i)
                
                val lessonId = schedule.getString("lessonId")
                val courseName = lessonMap[lessonId] ?: ""
                val teacher = schedule.optString("personName", "")
                val roomObj = schedule.optJSONObject("room")
                val classroom = roomObj?.optString("nameZh", "") ?: ""
                val dayOfWeek = schedule.getInt("weekday")
                val weekIndex = schedule.getInt("weekIndex")
                val startTime = schedule.getString("startTime")
                val periods = schedule.getInt("periods")
                
                // 计算节次
                val startSection = timeMap[startTime] ?: 1
                val sections = (0 until periods).map { startSection + it }
                
                rawCourses.add(ParsedCourse(
                    courseName = courseName.trim(),
                    teacher = teacher.trim(),
                    classroom = classroom.trim(),
                    dayOfWeek = dayOfWeek,
                    startSection = sections.firstOrNull() ?: 1,
                    sectionCount = sections.size,
                    weeks = listOf(weekIndex),
                    weekExpression = "${weekIndex}周"
                ))
            }
            
            // 合并相同课程的不同周次
            courses.addAll(resolveCourseConflicts(rawCourses))
            
            AppLogger.d(TAG, "✅ 成功解析 ${courses.size} 门课程")
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析课程数据失败", e)
            throw e
        }
        return courses
    }
    
    /**
     * 合并相同课程的不同周次
     */
    private fun resolveCourseConflicts(rawCourses: List<ParsedCourse>): List<ParsedCourse> {
        val result = mutableListOf<ParsedCourse>()
        val courseMap = mutableMapOf<String, ParsedCourse>()
        
        for (course in rawCourses) {
            val key = "${course.courseName}|${course.teacher}|${course.classroom}|${course.dayOfWeek}|${course.startSection}"
            
            if (courseMap.containsKey(key)) {
                // 合并周次
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
        
        result.addAll(courseMap.values)
        return result
    }
}



















