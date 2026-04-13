package com.wind.ggbond.classtime.util

import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.util.AppLogger
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HtmlScheduleParser @Inject constructor() {

    companion object {
        private const val TAG = "HtmlParser"

        private val SCHOOL_NAME_PATTERNS = listOf(
            Regex("""^[^\s]*高等专科学校\s*"""),
            Regex("""^[^\s]*职业技术大学\s*"""),
            Regex("""^[^\s]*职业技术学院\s*"""),
            Regex("""^[^\s]*专科学校\s*"""),
            Regex("""^[^\s]*职业大学\s*"""),
            Regex("""^[^\s]*大学\s*"""),
            Regex("""^[^\s]*学院\s*"""),
        )

        private val TEACHER_PREFIXES = listOf("教师", "任课教师", "授课教师")
        private val EXCLUDED_TEACHER_WORDS = listOf("教师", "学分", "节次", "上课", "地点", "考查", "考试", "理论", "实践")
        private val TEACHER_NAME_PATTERN = Regex("""([\u4e00-\u9fa5]{2,4})""")
        private val SECTION_RANGE_PATTERN = Regex("""(\d+)-(\d+)节""")
        private val WEEK_PATTERN = Regex("""第([0-9\-,]+)周""")
        private val COURSE_CLEANUP_PATTERNS = listOf(
            Regex("\\(\\d+-\\d+节\\).*"),
            Regex("\\d+-\\d+周.*"),
            Regex("[,;，；].*"),
        )
    }

    fun parseQiangzhiHtml(html: String): List<ParsedCourse> {
        return try {
            val table = Jsoup.parse(html).select("#timetable").firstOrNull()
                ?: run { AppLogger.w(TAG, "未找到课表表格（#timetable）"); return emptyList() }

            AppLogger.d(TAG, "找到强智系统课表表格，开始解析")
            val courses = mutableListOf<ParsedCourse>()

            table.select("tbody tr").filter { !it.text().contains("备注") }.forEachIndexed { _, row ->
                row.select("td").drop(1).forEachIndexed { dayIndex, cell ->
                    val itemBoxes = cell.select("div.item-box")
                    if (itemBoxes.isNotEmpty()) {
                        itemBoxes.forEach { box ->
                            parseQiangzhiCourse(box, dayIndex + 1)?.let {
                                AppLogger.d(TAG, "解析到课程: ${it.courseName}, 周${it.dayOfWeek}, 第${it.startSection}节")
                                courses.add(it)
                            }
                        }
                    }
                }
            }
            AppLogger.d(TAG, "强智系统解析完成，共 ${courses.size} 门课程")
            courses
        } catch (e: Exception) {
            AppLogger.e(TAG, "强智系统解析异常: ${e.message}", e)
            emptyList()
        }
    }

    private fun cleanCourseName(rawName: String): String {
        var cleaned = rawName.trim()
        for (pattern in SCHOOL_NAME_PATTERNS) {
            val before = cleaned
            cleaned = cleaned.replace(pattern, "").trim()
            if (cleaned != before && cleaned.isNotEmpty()) break
        }
        return cleaned.ifEmpty { rawName }
    }

    private fun parseQiangzhiCourse(itemBox: org.jsoup.nodes.Element, dayOfWeek: Int): ParsedCourse? {
        return try {
            var courseName = itemBox.select("p").firstOrNull()?.text()?.trim() ?: return null

            COURSE_CLEANUP_PATTERNS.forEach { pattern -> courseName = courseName.replace(pattern, "").trim() }
            courseName = cleanCourseName(courseName)

            val tchDiv = itemBox.select("div.tch-name").firstOrNull()
            val (teacher, credit, startSection, sectionCount) = parseTeacherInfo(tchDiv)

            val (classroom, weekExpression, weeks) = parseLocationAndWeeks(itemBox)

            ParsedCourse(courseName, teacher, classroom, dayOfWeek, startSection, sectionCount, weekExpression, weeks, credit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析强智课程失败: ${e.message}", e)
            null
        }
    }

    private data class TeacherInfo(
        val teacher: String = "",
        val credit: Float = 0f,
        val startSection: Int = 1,
        val sectionCount: Int = 2
    )

    private fun parseTeacherInfo(tchDiv: org.jsoup.nodes.Element?): TeacherInfo {
        var teacher = ""
        var credit = 0f
        var startSection = 1
        var sectionCount = 2

        tchDiv?.let { div ->
            div.select("span").forEach { span ->
                val text = span.text().trim()
                when {
                    TEACHER_PREFIXES.any { prefix -> text.startsWith(prefix) || text.startsWith("$prefix：") || text.startsWith("$prefix:") || text.startsWith("$prefix ：") || text.startsWith("$prefix :") } -> {
                        teacher = text.replace(Regex("^(教师|任课教师|授课教师)\\s*[：:]"), "")
                            .split("教学班")[0].trim()
                    }
                    text.startsWith("学分：") || text.startsWith("学分:") -> {
                        credit = text.replace(Regex("^学分[：:]"), "").trim().toFloatOrNull() ?: 0f
                    }
                    text.contains("~") && text.contains("节") -> {
                        Regex("""\d+""").findAll(text).map { it.value.toIntOrNull() ?: 0 }.filter { it > 0 }.toList()
                            .takeIf { it.isNotEmpty() }?.let { nums ->
                                startSection = nums.first()
                                sectionCount = nums.last() - nums.first() + 1
                            }
                    }
                    text.matches(Regex(".*\\(?\\d+-\\d+节\\)?.*")) -> {
                        SECTION_RANGE_PATTERN.find(text)?.let { m ->
                            startSection = m.groupValues[1].toIntOrNull() ?: 1
                            sectionCount = (m.groupValues[2].toIntOrNull() ?: startSection) - startSection + 1
                        }
                    }
                }
            }

            if (teacher.isEmpty()) {
                TEACHER_NAME_PATTERN.findAll(div.text())
                    .map { it.value }
                    .find { candidate -> !EXCLUDED_TEACHER_WORDS.any { candidate.contains(it) } }
                    ?.let { teacher = it }
            }
        }

        return TeacherInfo(teacher, credit, startSection, sectionCount)
    }

    private data class LocationWeekInfo(
        val classroom: String = "",
        val weekExpression: String = "",
        val weeks: List<Int> = emptyList()
    )

    private fun parseLocationAndWeeks(itemBox: org.jsoup.nodes.Element): LocationWeekInfo {
        var classroom = ""
        var weekExpression = ""
        var weeks = emptyList<Int>()

        itemBox.select("div").filter { it.select("img").isNotEmpty() && !it.hasClass("tch-name") }.forEach { div ->
            div.select("span").forEach { span ->
                val imgSrc = span.select("img").firstOrNull()?.attr("src") ?: ""
                val text = span.text().trim()
                when {
                    imgSrc.contains("item1.png") -> classroom = text
                    imgSrc.contains("item3.png") -> {
                        weekExpression = text
                        weeks = parseQiangzhiWeekExpression(text)
                    }
                }
            }
        }

        return LocationWeekInfo(classroom, weekExpression, weeks)
    }

    private fun parseQiangzhiWeekExpression(expression: String): List<Int> {
        return try {
            val weekMatch = WEEK_PATTERN.find(expression) ?: return emptyList()
            val weekStr = weekMatch.groupValues[1]
            val isOdd = expression.contains("(单周)")
            val isEven = expression.contains("(双周)")
            val weeks = mutableListOf<Int>()

            weekStr.split(",").forEach { range ->
                if (range.contains("-")) {
                    val parts = range.split("-")
                    if (parts.size == 2) {
                        val start = parts[0].toIntOrNull() ?: return@forEach
                        val end = parts[1].toIntOrNull() ?: return@forEach
                        (start..end).forEach { week ->
                            if ((!isOdd && !isEven) || (isOdd && week % 2 == 1) || (isEven && week % 2 == 0)) weeks.add(week)
                        }
                    }
                } else {
                    range.toIntOrNull()?.let { weeks.add(it) }
                }
            }
            weeks.distinct().sorted()
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析周次表达式失败: $expression, ${e.message}", e)
            emptyList()
        }
    }

    fun parseZhengfangHtml(html: String): List<ParsedCourse> {
        return try {
            parseFromJavaScriptVariable(html).takeIf { it.isNotEmpty() }
                ?: parseZhengfangTable(html)
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析异常: ${e.message}", e)
            emptyList()
        }
    }

    private fun parseFromJavaScriptVariable(html: String): List<ParsedCourse> {
        return try {
            val patterns = listOf(
                Regex("""var\s+kbList\s*=\s*(\[.*?\]);""", RegexOption.DOT_MATCHES_ALL),
                Regex("""var\s+courseList\s*=\s*(\[.*?\]);""", RegexOption.DOT_MATCHES_ALL),
                Regex("""var\s+scheduleData\s*=\s*(\[.*?\]);""", RegexOption.DOT_MATCHES_ALL),
                Regex("""var\s+kbData\s*=\s*(\[.*?\]);""", RegexOption.DOT_MATCHES_ALL),
            )
            patterns.forEach { pattern ->
                pattern.find(html)?.let {
                    AppLogger.d(TAG, "找到课表数据变量: ${it.groupValues[1].take(500)}")
                    return emptyList()
                }
            }
            emptyList()
        } catch (e: Exception) {
            AppLogger.e(TAG, "parseFromJavaScriptVariable 异常", e)
            emptyList()
        }
    }

    private fun parseZhengfangTable(html: String): List<ParsedCourse> {
        val doc = Jsoup.parse(html)
        val table = doc.select("#kbtable table").firstOrNull()
            ?: doc.select("#kbtable").firstOrNull()
            ?: doc.select("table").firstOrNull { it.select("div.kbcontent").isNotEmpty() }
            ?: doc.select("table").firstOrNull()
            ?: run { AppLogger.w(TAG, "未找到课表表格"); return emptyList() }

        AppLogger.d(TAG, "找到课表表格，开始解析")
        val courses = mutableListOf<ParsedCourse>()

        table.select("tr").drop(1).forEachIndexed { rowIndex, row ->
            row.select("td").drop(1).forEachIndexed { colIndex, cell ->
                parseCourseCell(cell.html(), colIndex + 1, rowIndex)?.let {
                    AppLogger.d(TAG, "解析到课程: ${it.courseName}")
                    courses.add(it)
                }
            }
        }
        AppLogger.d(TAG, "总共解析到 ${courses.size} 门课程")
        return courses
    }

    private fun parseCourseCell(cellHtml: String, dayOfWeek: Int, rowIndex: Int): ParsedCourse? {
        return try {
            val cell = Jsoup.parse(cellHtml)
            val courseDiv = cell.select("div.kbcontent").firstOrNull()

            if (courseDiv != null) {
                val courseName = cleanCourseName(
                    courseDiv.select("div.kcmc, .kcmc").text().trim().ifEmpty { return null }
                )
                val startSection = (rowIndex - 1) * 2 + 1
                if (startSection > Constants.Course.MAX_SECTION_NUMBER) {
                    AppLogger.w(TAG, "节次超出范围: rowIndex=$rowIndex, startSection=$startSection，跳过课程: $courseName")
                    return null
                }
                val weekRaw = courseDiv.select("div.zcd, .zcd").text().trim()
                return ParsedCourse(
                    courseName,
                    courseDiv.select("div.jshi, .jshi").text().trim(),
                    courseDiv.select("div.jxdd, .jxdd").text().trim(),
                    dayOfWeek, startSection, 2,
                    weekRaw,
                    WeekParser.parseWeekExpression(weekRaw)
                )
            }

            val lines = cell.text().trim().split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size < 3) return null

            val courseName = cleanCourseName(lines[0])
            val startSection = (rowIndex - 1) * 2 + 1
            if (startSection > Constants.Course.MAX_SECTION_NUMBER) {
                AppLogger.w(TAG, "节次超出范围: rowIndex=$rowIndex, startSection=$startSection，跳过课程: $courseName")
                return null
            }
            val weekRaw2 = lines.getOrNull(3) ?: ""
            ParsedCourse(courseName, lines.getOrNull(1) ?: "", lines.getOrNull(2) ?: "",
                dayOfWeek, startSection, 2, weekRaw2, WeekParser.parseWeekExpression(weekRaw2))
        } catch (e: Exception) {
            AppLogger.e(TAG, "parseCourseCell 异常", e)
            null
        }
    }

    fun isScheduleHtml(html: String): Boolean {
        return try {
            val doc = Jsoup.parse(html)
            doc.select("#kbtable").isNotEmpty() ||
                doc.select("div.kbcontent").isNotEmpty() ||
                doc.select("div.kcmc").isNotEmpty() ||
                html.contains("课程表", ignoreCase = true)
        } catch (_: Exception) { false }
    }

    fun parseHtmlAuto(html: String): List<ParsedCourse> {
        AppLogger.d(TAG, "开始自动检测HTML类型")
        return try {
            val doc = Jsoup.parse(html)

            val isQiangzhi = doc.select("#timetable").isNotEmpty() ||
                doc.select("div.item-box").isNotEmpty() ||
                doc.select("div.tch-name").isNotEmpty() ||
                html.contains("qiangzhi", ignoreCase = true) ||
                html.contains("强智", ignoreCase = true)

            if (isQiangzhi) {
                AppLogger.d(TAG, "检测到强智系统特征，尝试强智解析")
                return parseQiangzhiHtml(html).takeIf { it.isNotEmpty() }
                    ?.also { AppLogger.d(TAG, "强智系统解析成功，共 ${it.size} 门课程") }
                    ?: emptyList()
            }

            val isZhengfang = doc.select("#kbtable").isNotEmpty() ||
                doc.select("div.kbcontent").isNotEmpty() ||
                doc.select("div.kcmc").isNotEmpty() ||
                html.contains("zfsoft", ignoreCase = true) ||
                html.contains("正方", ignoreCase = true) ||
                html.contains("var kbList", ignoreCase = false)

            if (isZhengfang) {
                AppLogger.d(TAG, "检测到正方系统特征，尝试正方解析")
                return parseZhengfangHtml(html).takeIf { it.isNotEmpty() }
                    ?.also { AppLogger.d(TAG, "正方系统解析成功，共 ${it.size} 门课程") }
                    ?: emptyList()
            }

            AppLogger.d(TAG, "未检测到明确系统特征，依次尝试解析")

            parseQiangzhiHtml(html).takeIf { it.isNotEmpty() }?.also {
                AppLogger.d(TAG, "强智系统解析成功（通用尝试），共 ${it.size} 门课程")
            } ?: parseZhengfangHtml(html).takeIf { it.isNotEmpty() }?.also {
                AppLogger.d(TAG, "正方系统解析成功（通用尝试），共 ${it.size} 门课程")
            } ?: parseGenericTableHtml(html).also {
                if (it.isNotEmpty()) AppLogger.d(TAG, "通用表格解析成功，共 ${it.size} 门课程")
                else AppLogger.w(TAG, "所有解析方法均未成功")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "自动解析异常: ${e.message}", e)
            emptyList()
        }
    }

    private fun parseGenericTableHtml(html: String): List<ParsedCourse> {
        return try {
            val tables = Jsoup.parse(html).select("table")
            val courses = mutableListOf<ParsedCourse>()

            for (table in tables) {
                table.select("tr").drop(1).forEachIndexed { rowIndex, row ->
                    row.select("td, th").drop(1).forEachIndexed { colIndex, cell ->
                        val text = cell.text().trim()
                        if (text.length > 2) {
                            val lines = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
                            if (lines.isNotEmpty()) {
                                val courseName = cleanCourseName(lines[0])
                                var teacher = ""
                                var classroom = ""
                                lines.drop(1).forEach { line ->
                                    when {
                                        line.contains("老师") || line.contains("教师") -> teacher = line
                                        line.matches(Regex(".*[A-Z]\\d+.*")) -> classroom = line
                                    }
                                }
                                val startSection = rowIndex * 2 + 1
                                if (startSection <= Constants.Course.MAX_SECTION_NUMBER) {
                                    courses.add(ParsedCourse(courseName, teacher, classroom,
                                        colIndex + 1, startSection, 2, "1-16", (1..16).toList()))
                                }
                            }
                        }
                    }
                }
                if (courses.isNotEmpty()) break
            }
            courses
        } catch (e: Exception) {
            AppLogger.e(TAG, "parseGenericTableHtml 异常", e)
            emptyList()
        }
    }
}
