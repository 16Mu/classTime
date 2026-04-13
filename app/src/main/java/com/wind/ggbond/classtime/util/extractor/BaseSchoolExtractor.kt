package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.util.WeekParser
import com.wind.ggbond.classtime.util.AppLogger
import org.json.JSONObject

abstract class BaseSchoolExtractor : SchoolScheduleExtractor {

    protected open val tag: String = schoolId

    override val aliases: List<String> = emptyList()
    override val supportedUrls: List<String> = emptyList()

    override fun isSchedulePage(html: String, url: String): Boolean {
        return url.contains("xskbcx") ||
                url.contains("courseTableForStd") ||
                html.contains("ajaxForm")
    }

    override fun getLoginUrl(): String? = null
    override fun getScheduleUrl(): String? = null

    override fun parseCourses(jsonData: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        try {
            AppLogger.d(tag, "开始解析 $schoolName 课程数据...")

            val cleanJson = jsonData.trim()
                .removePrefix("\"").removeSuffix("\"")
                .replace("\\\"", "\"")
                .replace("\\n", "")
                .replace("\\r", "")

            val jsonObject = JSONObject(cleanJson)

            if (jsonObject.has("error")) {
                throw Exception("提取失败: ${jsonObject.getString("error")}")
            }

            val coursesArray = jsonObject.getJSONArray("courses")
            AppLogger.d(tag, "找到 ${coursesArray.length()} 门课程")

            for (i in 0 until coursesArray.length()) {
                val courseObj = coursesArray.getJSONObject(i)
                val course = parseSingleCourse(courseObj)
                if (course != null) {
                    courses.add(course)
                }
            }

            AppLogger.d(tag, "解析完成，共 ${courses.size} 门课程")
        } catch (e: Exception) {
            AppLogger.e(tag, "解析课程数据失败", e)
            throw e
        }
        return courses
    }

    protected open fun parseSingleCourse(courseObj: JSONObject): ParsedCourse? {
        val courseName = courseObj.optString("courseName", "").trim()
        val teacher = courseObj.optString("teacher", "").trim()
        val classroom = courseObj.optString("classroom", "").trim()
        val dayOfWeek = courseObj.optInt("dayOfWeek", courseObj.optInt("day", 1))
        val weekExpression = courseObj.optString("weekExpression", courseObj.optString("weeks", ""))
        val sectionsStr = courseObj.optString("sections", "")

        val weeks = if (weekExpression.isNotEmpty()) {
            WeekParser.parseWeekExpression(weekExpression)
        } else {
            emptyList()
        }

        val sections = parseSections(sectionsStr)
        val startSection = sections.minOrNull() ?: 1
        val sectionCount = sections.size

        if (courseName.isNotEmpty() && weeks.isNotEmpty()) {
            return ParsedCourse(
                courseName = courseName,
                teacher = teacher,
                classroom = classroom,
                dayOfWeek = dayOfWeek,
                startSection = startSection,
                sectionCount = sectionCount,
                weeks = weeks,
                credit = 0f,
                weekExpression = weekExpression
            )
        }
        return null
    }

    protected open fun parseSections(sectionsStr: String): List<Int> {
        val sections = mutableListOf<Int>()
        try {
            val cleanStr = sectionsStr.replace("节", "").trim()
            if (cleanStr.isEmpty()) return listOf(1)

            cleanStr.split(",").forEach { part ->
                if (part.contains("-")) {
                    val range = part.split("-")
                    val start = range[0].trim().toIntOrNull() ?: 1
                    val end = range.getOrNull(1)?.trim()?.toIntOrNull() ?: start
                    for (i in start..end) {
                        sections.add(i)
                    }
                } else {
                    part.trim().toIntOrNull()?.let { sections.add(it) }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(tag, "解析节次失败: $sectionsStr", e)
            return listOf(1)
        }
        return sections.sorted()
    }

    override fun parseSemesterInfo(jsonData: String): com.wind.ggbond.classtime.data.model.ImportedSemesterInfo? = null
}
