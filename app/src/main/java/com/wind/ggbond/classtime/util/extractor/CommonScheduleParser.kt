package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.util.HtmlScheduleParser
import com.wind.ggbond.classtime.util.WeekParser
import com.wind.ggbond.classtime.util.AppLogger
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommonScheduleParser @Inject constructor(
    private val htmlScheduleParser: HtmlScheduleParser
) {

    companion object {
        private const val TAG = "CommonScheduleParser"
    }

    fun cleanJsonData(jsonData: String): String {
        return jsonData.trim()
            .removePrefix("\"").removeSuffix("\"")
            .replace("\\\"", "\"")
            .replace("\\n", "")
            .replace("\\r", "")
    }

    fun parseCoursesFromJson(jsonData: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        try {
            val cleanJson = cleanJsonData(jsonData)
            val jsonObject = JSONObject(cleanJson)

            if (jsonObject.has("error")) {
                throw Exception("提取失败: ${jsonObject.getString("error")}")
            }

            val coursesArray = jsonObject.getJSONArray("courses")
            for (i in 0 until coursesArray.length()) {
                val courseObj = coursesArray.getJSONObject(i)
                parseZfsoftCourse(courseObj)?.let { courses.add(it) }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "JSON解析失败", e)
            throw e
        }
        return courses
    }

    fun parseZfsoftCourse(courseObj: JSONObject): ParsedCourse? {
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

    fun parseSections(sectionsStr: String): List<Int> {
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
            AppLogger.e(TAG, "解析节次失败: $sectionsStr", e)
            return listOf(1)
        }
        return sections.sorted()
    }

    fun parseHtmlSchedule(html: String): List<ParsedCourse> {
        return htmlScheduleParser.parseHtmlAuto(html)
    }

    fun parseQiangzhiHtml(html: String): List<ParsedCourse> {
        return htmlScheduleParser.parseQiangzhiHtml(html)
    }

    fun parseZhengfangHtml(html: String): List<ParsedCourse> {
        return htmlScheduleParser.parseZhengfangHtml(html)
    }

    fun isScheduleHtml(html: String): Boolean {
        return htmlScheduleParser.isScheduleHtml(html)
    }
}
