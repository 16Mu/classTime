package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

/**
 * 德阳通用电子科技学校课表提取器
 * 系统：YN智慧校园
 */
@Singleton
class DYTYDZKJXXExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "dytydzkjxx"
    override val schoolName = "德阳通用电子科技学校"
    override val systemType = "ynsmart"

    override val aliases = listOf("德阳通用电子科技学校")
    override val supportedUrls = listOf("aixiaoyuan.cn")
    
    companion object {
        private const val TAG = "DYTYDZKJXXExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("aixiaoyuan.cn", ignoreCase = true) &&
               (html.contains("ynedut") || html.contains("getStudentTimetableData"))
    }
    
    override fun getLoginUrl(): String = "http://dytyzj.aixiaoyuan.cn/"
    override fun getScheduleUrl(): String? = null
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取${schoolName}课表...');
                    console.log('⚠️ 注意：此系统需要逐周获取数据，可能需要较长时间');
                    
                    async function fetchCourseData() {
                        // 获取学生ID
                        let studentId = window.localStorage["ls.platformSysUserId"].replace(/"/g, "");
                        if (!studentId) {
                            return JSON.stringify({error: '无法获取学生ID，请确保已登录'});
                        }
                        
                        console.log('📌 学生ID:', studentId);
                        
                        // 获取系统ID
                        let systemIdUrl = "http://dytyzj.aixiaoyuan.cn/ynedut/portal/queryServiceTypes.htm";
                        let systemResponse = await fetch(systemIdUrl);
                        let systemData = await systemResponse.json();
                        let systemId = systemData.result
                            .filter(v => v.name == "教务教学")[0]
                            .serviceLevelMenuList
                            .filter(v => v.name == "查看课表")[0].id;
                        
                        // 获取菜单ID
                        let menuIdUrl = "http://dytyzj.aixiaoyuan.cn/ynedut/portal/queryMenusOfService.htm?serviceId=" + systemId;
                        let menuResponse = await fetch(menuIdUrl);
                        let menuData = await menuResponse.json();
                        let menuId = menuData.result.children
                            .filter(v => v.name == "我的课表")[0].id;
                        
                        // 获取学期ID
                        let termIdUrl = "http://dytyzj.aixiaoyuan.cn/ynedut/commonDropDownListController/queryAllUseAbleTerms.htm";
                        let termResponse = await fetch(termIdUrl);
                        let termData = await termResponse.json();
                        let termId = termData.result
                            .filter(v => v.typeDesc == "当前学期")[0].id;
                        
                        console.log('📌 系统ID:', systemId, '菜单ID:', menuId, '学期ID:', termId);
                        
                        let result = [];
                        let courseUrl = "http://dytyzj.aixiaoyuan.cn/ynedut/schoolTimetable/studentTimetable/getStudentTimetableData.htm";
                        
                        // 逐周获取（最多30周）
                        for (let i = 1; i <= 30; i++) {
                            console.log('获取第', i, '周...');
                            
                            let data = "termId=" + termId + "&week=" + i + "&studentId=" + studentId + "&systemId=" + systemId + "&menuId=" + menuId;
                            
                            let response = await fetch(courseUrl, {
                                method: 'POST',
                                headers: {
                                    'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
                                    'X-Requested-With': 'XMLHttpRequest'
                                },
                                body: data
                            });
                            
                            let jsonText = await response.json();
                            let list = jsonText.result.timeTableTimeTypeProcessVOList;
                            
                            if (!list || list.length === 0) {
                                console.log('第', i, '周无数据，停止获取');
                                break;
                            }
                            
                            let jc = 0;
                            list.forEach(con => {
                                let secs = con.timePeriodProcessVOList;
                                secs.forEach((sec, secIndex) => {
                                    jc++;
                                    let courses = sec.knobProcessVOList;
                                    courses.forEach((weeks, weekIndx) => {
                                        if (!weeks.detailVOList || weeks.detailVOList.length === 0) return;
                                        
                                        let couseInfo = weeks.detailVOList[0];
                                        
                                        result.push({
                                            name: couseInfo.courseName,
                                            teacher: couseInfo.teacherNames || "",
                                            position: couseInfo.classRoomNames || "",
                                            day: weekIndx + 1,
                                            sections: [jc],
                                            weeks: [i]
                                        });
                                    });
                                });
                            });
                            
                            if (i == 15) {
                                console.log('已解析到第15周，继续解析...');
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


















