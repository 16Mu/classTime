package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

/**
 * 贵州工程应用技术学院课表提取器
 * 系统：自研教务系统
 */
@Singleton
class GZGCYYJSXYExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "gzgcyyjsxy"
    override val schoolName = "贵州工程应用技术学院"
    override val systemType = "custom"
    
    companion object {
        private const val TAG = "GZGCYYJSXYExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("gues.edu.cn", ignoreCase = true) &&
               (url.contains("course-table") || html.contains("student/for-std"))
    }
    
    override fun getLoginUrl(): String = "https://jwxt.gues.edu.cn/"
    override fun getScheduleUrl(): String = "https://jwxt.gues.edu.cn/student/for-std/course-table"
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取${schoolName}课表...');
                    
                    async function fetchCourseData() {
                        let preUrl = "https://jwxt.gues.edu.cn";
                        
                        // 获取课表页面
                        let ctResponse = await fetch(preUrl + '/student/for-std/course-table');
                        let cthtml = await ctResponse.text();
                        
                        // 提取学期信息
                        let jsonStr = cthtml.match(/(?<=var currentSemester = ).*?(?=;)/)[0].replace(/'/g,'"');
                        
                        let localSemester = localStorage.getItem('sSemester');
                        var semesterId = localSemester ? localSemester : JSON.parse(jsonStr).id;
                        
                        console.log('📌 学期ID:', semesterId);
                        
                        // 获取课程表打印数据
                        let timeUrl = preUrl + '/student/for-std/course-table/semester/' + semesterId + '/print-data?semesterId=' + semesterId + '&hasExperiment=true';
                        let timeResponse = await fetch(timeUrl);
                        let time = await timeResponse.json();
                        
                        // 提取数据
                        let startTimeJson = time.studentTableVms[0].timeTableLayout.courseUnitList;
                        let courseJson = time.studentTableVms[0].activities;
                        
                        console.log('✅ 数据获取完成');
                        
                        return JSON.stringify({
                            courseJson: courseJson,
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
            AppLogger.d(TAG, "开始解析${schoolName}课程数据...")
            
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
            val courseJsonArray = allJson.getJSONArray("courseJson")
            
            // 解析课程活动
            for (i in 0 until courseJsonArray.length()) {
                val activity = courseJsonArray.getJSONObject(i)
                
                val courseName = activity.optString("courseName", "")
                if (courseName.isEmpty()) continue
                
                // 教师列表
                val teachersList = mutableListOf<String>()
                val teachersArray = activity.optJSONArray("teachers")
                if (teachersArray != null) {
                    for (j in 0 until teachersArray.length()) {
                        teachersList.add(teachersArray.getString(j))
                    }
                }
                val teacher = teachersList.joinToString(",")
                
                val classroom = activity.optString("room", "")
                val dayOfWeek = activity.getInt("weekday")
                val startUnit = activity.getInt("startUnit")
                val endUnit = activity.getInt("endUnit")
                val sectionCount = endUnit + 1 - startUnit
                
                // 周次索引数组
                val weekIndexesArray = activity.optJSONArray("weekIndexes")
                val weeks = mutableListOf<Int>()
                if (weekIndexesArray != null) {
                    for (j in 0 until weekIndexesArray.length()) {
                        weeks.add(weekIndexesArray.getInt(j))
                    }
                }
                
                courses.add(ParsedCourse(
                    courseName = courseName.trim(),
                    teacher = teacher.trim(),
                    classroom = classroom.trim(),
                    dayOfWeek = dayOfWeek,
                    startSection = startUnit,
                    sectionCount = sectionCount,
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
     * 合并相同课程
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


















