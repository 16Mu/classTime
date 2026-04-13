package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

/**
 * 成都大学课表提取器
 * 系统：超星系统
 */
@Singleton
class CDDXExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "cddx"
    override val schoolName = "成都大学"
    override val systemType = "chaoxing"

    override val aliases = listOf("成都大学")
    override val supportedUrls = listOf("cdu.edu.cn", "chaoxing.com")
    
    companion object {
        private const val TAG = "CDDXExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        return (url.contains("cdu.edu.cn", ignoreCase = true) && url.contains("jw", ignoreCase = true)) ||
               (url.contains("chaoxing.com", ignoreCase = true) && url.contains("schedule.html"))
    }
    
    override fun getLoginUrl(): String = "https://jw.cdu.edu.cn/"
    override fun getScheduleUrl(): String? = null
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取成都大学课表...');
                    
                    async function fetchCourseData() {
                        let currentUrl = window.location.href;
                        let tag = '';
                        
                        if (currentUrl.search("jw") !== -1) {
                            tag = "jw";
                        } else if (currentUrl.search(/i.chaoxing.com|kb.chaoxing.com/) !== -1) {
                            tag = 'cx';
                        } else {
                            return JSON.stringify({
                                error: '您可能不在课表页，请到达课表页'
                            });
                        }
                        
                        console.log('📌 检测到模式:', tag);
                        
                        let json = '';
                        let xnxq = '';
                        
                        if (tag === 'jw') {
                            // 教务系统模式
                            let a_s = document.querySelectorAll('.J_menuTab a');
                            let fram = null;
                            
                            for (let index = 0; index < a_s.length; index++) {
                                const element = a_s[index];
                                if (element.innerText.trim() == '我的课表') {
                                    fram = document.getElementsByTagName('iframe')[index];
                                    break;
                                }
                            }
                            
                            if (fram) {
                                let dom1 = fram.contentDocument;
                                xnxq = dom1.getElementById('xnxq').value;
                                let xhid = dom1.getElementById('xhid').value;
                                let xqdm = dom1.getElementById('xqdm').value;
                                let url = 'admin/pkgl/xskb/sdpkkbList?xnxq=' + xnxq + '&xhid=' + xhid + '&xqdm=' + xqdm;
                                
                                let response = await fetch(url);
                                json = await response.text();
                            } else {
                                let response = await fetch('/admin/pkgl/xskb/queryKbForXsd');
                                let html = await response.text();
                                let dom1 = new DOMParser().parseFromString(html, 'text/html');
                                xnxq = dom1.getElementById('xnxq').value;
                                let xhid = dom1.getElementById('xhid').value;
                                let xqdm = dom1.getElementById('xqdm').value;
                                let url = 'admin/pkgl/xskb/sdpkkbList?xnxq=' + xnxq + '&xhid=' + xhid + '&xqdm=' + xqdm;
                                
                                let urlResponse = await fetch(url);
                                json = await urlResponse.text();
                            }
                        } else if (tag === 'cx') {
                            // 超星系统模式
                            if (window.location.href.search("/curriculum/schedule.html") === -1) {
                                let iframs = document.getElementsByTagName('iframe');
                                let src = '';
                                for (let index = 0; index < iframs.length; index++) {
                                    if (iframs[index].src.search("/curriculum/schedule.html") !== -1) {
                                        src = iframs[index].src;
                                    }
                                }
                                if (src.length === 0) {
                                    return JSON.stringify({
                                        error: '请先跳转到课表页面',
                                        redirect: 'https://kb.chaoxing.com/res/pc/curriculum/schedule.html'
                                    });
                                } else {
                                    return JSON.stringify({
                                        error: '请先跳转到课表页面',
                                        redirect: src
                                    });
                                }
                            }
                            
                            // 获取最大周数
                            let resResponse = await fetch("/pc/curriculum/getMyLessons?curTime=" + new Date().getTime());
                            let resJson = await resResponse.json();
                            let maxWeek = resJson.data.curriculum.maxWeek || 25;
                            
                            console.log('📌 最大周数:', maxWeek);
                            
                            // 逐周获取
                            let allResultPromise = [];
                            for (let week = 1; week <= Number(maxWeek); week++) {
                                allResultPromise.push(
                                    fetch("/pc/curriculum/getMyLessons?curTime=" + new Date().getTime() + "&week=" + week)
                                        .then(v => v.json())
                                        .then(v => v)
                                        .catch(e => {
                                            console.error('获取第', week, '周失败:', e);
                                            return {data: {lessonArray: []}};
                                        })
                                );
                            }
                            
                            let allResultJson = await Promise.all(allResultPromise);
                            let allResult = [];
                            allResultJson.forEach(result => {
                                if (result.data && result.data.lessonArray) {
                                    allResult.push(...result.data.lessonArray);
                                }
                            });
                            
                            // 去重
                            let arr = [];
                            allResult = allResult.filter(res => {
                                let key = res.beginNumber + '+' + res.length + '+' + res.dayOfWeek + '+' + res.name + '+' + res.teacherNo + '+' + res.location + '+' + res.weeks;
                                let isNew = arr.indexOf(key) === -1;
                                arr.push(key);
                                return isNew;
                            });
                            
                            json = JSON.stringify(allResult);
                        }
                        
                        console.log('✅ 获取完成');
                        return JSON.stringify({"data": JSON.parse(json), "xnxq": xnxq, "tag": tag});
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
            
            val tag = jsonObject.getString("tag")
            val dataArray = jsonObject.getJSONArray("data")
            
            when (tag) {
                "jw" -> {
                    courses.addAll(parseJwMode(dataArray))
                }
                "cx" -> {
                    courses.addAll(parseCxMode(dataArray))
                }
                else -> {
                    throw Exception("未知的数据格式: $tag")
                }
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
     * 解析教务系统模式
     */
    private fun parseJwMode(dataArray: JSONArray): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        
        for (i in 0 until dataArray.length()) {
            val con = dataArray.getJSONObject(i)
            
            // 提取课程名（从HTML中提取）
            val kcmcHtml = con.optString("kcmc", "")
            val nameMatch = Regex("(?<=\">).*?(?=</)").find(kcmcHtml)
            val courseName = nameMatch?.value ?: ""
            
            if (courseName.isEmpty()) continue
            
            // 提取教师
            val tmcHtml = con.optString("tmc", "")
            val teacherMatch = Regex("(?<=\">).*?(?=</)").find(tmcHtml)
            val teacher = teacherMatch?.value ?: ""
            
            // 提取教室
            val croommcHtml = con.optString("croommc", "")
            val classroomMatch = Regex("(?<=\">).*?(?=</)").find(croommcHtml)
            val classroom = classroomMatch?.value ?: ""
            
            val dayOfWeek = con.optInt("xingqi", 1)
            val zc = con.optString("zc", "")
            val weeks = parseWeeks(zc)
            val djc = con.optInt("djc", 1)
            
            courses.add(ParsedCourse(
                courseName = courseName.trim(),
                teacher = teacher.trim(),
                classroom = classroom.trim(),
                dayOfWeek = dayOfWeek,
                startSection = djc,
                sectionCount = 1,
                weeks = weeks,
                weekExpression = weeks.joinToString(",") + "周"
            ))
        }
        
        return courses
    }
    
    /**
     * 解析超星系统模式
     */
    private fun parseCxMode(dataArray: JSONArray): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        
        for (i in 0 until dataArray.length()) {
            val con = dataArray.getJSONObject(i)
            
            val courseName = con.optString("name", "")
            if (courseName.isEmpty()) continue
            
            val teacher = con.optString("teacherName", "")
            val classroom = con.optString("location", "")
            val dayOfWeek = con.optInt("dayOfWeek", 1)
            val beginNumber = con.optInt("beginNumber", 1)
            val length = con.optInt("length", 2)
            val weeksStr = con.optString("weeks", "")
            
            val weeks = if (weeksStr.isNotEmpty()) {
                weeksStr.split(",").mapNotNull { it.toIntOrNull() }
            } else {
                listOf(1)
            }
            
            val sections = (0 until length).map { beginNumber + it }
            
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
        
        return courses
    }
    
    /**
     * 解析周次字符串
     */
    private fun parseWeeks(weekStr: String): List<Int> {
        val weeks = mutableListOf<Int>()
        
        if (weekStr.isEmpty()) return listOf(1)
        
        var cleaned = weekStr.replace(Regex("[(){}|第\\[\\]]"), "").replace("到", "-")
        val weekParts = mutableListOf<String>()
        
        while (cleaned.contains("周") || cleaned.contains(Regex("\\s"))) {
            val index = minOf(
                cleaned.indexOf("周").let { if (it >= 0) it else Int.MAX_VALUE },
                cleaned.indexOfFirst { it.isWhitespace() }.let { if (it >= 0) it else Int.MAX_VALUE }
            )
            
            if (index < Int.MAX_VALUE) {
                val nextChar = cleaned.getOrNull(index + 1)
                if (nextChar == '单' || nextChar == '双') {
                    weekParts.add(cleaned.substring(0, index + 2).replace(Regex("[周\\s]"), ""))
                    cleaned = cleaned.substring(index + 2)
                } else {
                    weekParts.add(cleaned.substring(0, index + 1).replace(Regex("[周\\s]"), ""))
                    cleaned = cleaned.substring(index + 1)
                }
                
                val digitIndex = cleaned.indexOfFirst { it.isDigit() }
                cleaned = if (digitIndex >= 0) cleaned.substring(digitIndex) else ""
            } else {
                break
            }
        }
        
        if (cleaned.isNotEmpty()) {
            weekParts.add(cleaned)
        }
        
        for (part in weekParts.filter { it.isNotBlank() }) {
            val isDan = part.endsWith("单")
            val isShuang = part.endsWith("双")
            val cleanPart = part.replace("单", "").replace("双", "")
            
            val ranges = cleanPart.split(",")
            for (range in ranges) {
                val rangeParts = range.split("-")
                val start = rangeParts.getOrNull(0)?.toIntOrNull() ?: continue
                val end = rangeParts.getOrNull(1)?.toIntOrNull() ?: start
                
                for (week in start..end) {
                    when {
                        isDan && week % 2 == 1 -> weeks.add(week)
                        isShuang && week % 2 == 0 -> weeks.add(week)
                        !isDan && !isShuang -> weeks.add(week)
                    }
                }
            }
        }
        
        return weeks.distinct().sorted().ifEmpty { listOf(1) }
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

