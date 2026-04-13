package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

/**
 * 武汉信息传播职业技术学院课表提取器-手机端版本
 * 系统：强智教务系统-手机端
 * 参考：temp_aishedule/强智教务/手机端/武汉信息传播职业技术学院/provider.js
 */
@Singleton
class WHXXCBZYJSXYMobileExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "whxxcbzyjsxy_mobile"
    override val schoolName = "武汉信息传播职业技术学院(手机端)"
    override val systemType = "qiangzhi"

    override val aliases = listOf("武汉信息传播职业技术学院(手机端")
    override val supportedUrls = listOf("bzb_njwhd")
    
    companion object {
        private const val TAG = "WHXXCBZYJSXYMobileExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("bzb_njwhd") && 
               (url.contains("curriculum") || html.contains("sessionStorage"))
    }
    
    override fun getLoginUrl(): String = "http://219.140.59.210/"
    override fun getScheduleUrl(): String = "http://219.140.59.210/bzb_njwhd/student/curriculum"
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取武汉信息传播职业技术学院课表（手机端）...');
                    
                    async function fetchData() {
                        let token = sessionStorage.getItem('Token');
                        if (!token) {
                            console.error('未找到Token');
                            return {courses: [], error: '未找到登录Token'};
                        }
                        
                        let baseUrl = 'http://219.140.59.210/bzb_njwhd';
                        
                        // 获取课表节次模式ID
                        let kbjcmsResp = await fetch(baseUrl + '/Get_sjkbms', {
                            method: 'POST',
                            headers: { 'token': token }
                        });
                        let kbjcmsData = await kbjcmsResp.json();
                        let kbjcmsid = kbjcmsData.data[0].kbjcmsid;
                        
                        // 获取教学周
                        let weekResp = await fetch(baseUrl + '/teachingWeek', {
                            method: 'POST',
                            headers: { 'token': token }
                        });
                        let weekData = await weekResp.json();
                        let teachingWeeks = weekData.data;
                        
                        // 获取所有周的课程
                        let coursesSet = new Set();
                        for (let i = 0; i < teachingWeeks.length; i++) {
                            let currResp = await fetch(baseUrl + '/student/curriculum?week=' + teachingWeeks[i].week + '&kbjcmsid=' + kbjcmsid, {
                                method: 'POST',
                                headers: { 'token': token }
                            });
                            let currData = await currResp.json();
                            let items = currData.data[0].item;
                            items.forEach(v => coursesSet.add(JSON.stringify(v)));
                        }
                        
                        // 解析课程数据
                        let courseInfo = JSON.parse('[' + Array.from(coursesSet).toString() + ']');
                        let result = [];
                        
                        courseInfo.forEach(course => {
                            let re = { sections: [], weeks: [] };
                            re.name = course.courseName;
                            re.teacher = course.teacherName;
                            re.position = course.location;
                            re.day = parseInt(course.classTime.slice(0, 1));
                            
                            let sectionString = course.classTime.slice(1);
                            while (sectionString) {
                                re.sections.push(parseInt(sectionString.slice(0, 2)));
                                sectionString = sectionString.slice(2);
                            }
                            
                            course.classWeekDetails.split(',').filter(v => v.trim()).forEach(v => {
                                re.weeks.push(parseInt(v));
                            });
                            
                            result.push(re);
                        });
                        
                        return {courses: result};
                    }
                    
                    return fetchData().then(data => JSON.stringify(data));
                    
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

















