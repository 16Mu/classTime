package com.wind.ggbond.classtime.util.extractor

import android.util.Log
import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 西安建筑科技大学课表提取器
 * 系统：自研教务系统
 * 注意：本科生版本，逻辑较复杂
 */
@Singleton
class XAJZKJDXExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "xajzkjdx"
    override val schoolName = "西安建筑科技大学"
    override val systemType = "custom"
    
    companion object {
        private const val TAG = "XAJZKJDXExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("xauat.edu.cn", ignoreCase = true) &&
               (url.contains("course-table") || html.contains("for-std"))
    }
    
    override fun getLoginUrl(): String = "https://jwxt.xauat.edu.cn/"
    override fun getScheduleUrl(): String = "https://jwxt.xauat.edu.cn/student/for-std/course-table"
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取${schoolName}课表...');
                    
                    async function fetchCourseData() {
                        // 获取基础参数
                        let ctResponse = await fetch('/student/for-std/course-table');
                        let cthtml = await ctResponse.text();
                        
                        let bizTypeId = cthtml.match(/(?<=var bizTypeId \= ).*?(?=;)/)[0];
                        
                        let semesterId;
                        let semesterSelect = document.getElementById('allSemesters');
                        if (semesterSelect) {
                            semesterId = semesterSelect.value;
                        } else {
                            let match = cthtml.match(/(?<=selected\="selected" value\=").*?(?="\>)/);
                            semesterId = match ? match[0] : '';
                        }
                        
                        let stdPersonId = cthtml.match(/(?<=data\['stdPersonId'\] = ).*?(?=;)/)[0];
                        let studentId = cthtml.match(/(?<=data\['studentId'\] = ).*?(?=;)/)[0];
                        
                        console.log('📌 基础参数获取完成');
                        
                        // 获取课程数据
                        let kcUrl = '/student/for-std/course-table/get-data?bizTypeId=' + bizTypeId + '&semesterId=' + semesterId;
                        let kcResponse = await fetch(kcUrl);
                        let kcjson = await kcResponse.json();
                        
                        // 获取时间表布局
                        let timeResponse = await fetch('/student/ws/schedule-table/timetable-layout', {
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
                        
                        let daumResponse = await fetch('/student/ws/schedule-table/datum', {
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
            
            val allJson = jsonObject
            val startTimeJson = allJson.getJSONArray("startTimeJson")
            val courseJson = allJson.getJSONObject("courseJson")
            val scheduleList = courseJson.getJSONArray("scheduleList")
            val lessonList = courseJson.getJSONArray("lessonList")
            
            // 构建时间映射表
            val timeMap = mutableMapOf<String, Int>()
            for (i in 0 until startTimeJson.length()) {
                val timeObj = startTimeJson.getJSONObject(i)
                timeMap[timeObj.getString("startTime")] = timeObj.getInt("indexNo")
            }
            
            // 构建课程名称映射表
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
                
                // weekIndex <= 0 表示无效数据
                if (weekIndex <= 0) continue
                
                val startTime = schedule.getString("startTime")
                val periods = schedule.getInt("periods")
                
                // 计算节次
                val startSection = timeMap[startTime] ?: 1
                
                rawCourses.add(ParsedCourse(
                    courseName = courseName.trim(),
                    teacher = teacher.trim(),
                    classroom = classroom.trim(),
                    dayOfWeek = dayOfWeek,
                    startSection = startSection,
                    sectionCount = periods,
                    weeks = listOf(weekIndex),
                    weekExpression = "${weekIndex}周"
                ))
            }
            
            // 合并相同课程的不同周次
            courses.addAll(resolveCourseConflicts(rawCourses))
            
            Log.d(TAG, "✅ 成功解析 ${courses.size} 门课程")
        } catch (e: Exception) {
            Log.e(TAG, "解析课程数据失败", e)
            throw e
        }
        return courses
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
        
        return courseMap.values.toList().filter { it.courseName.isNotEmpty() }
    }
}


















