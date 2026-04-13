package com.wind.ggbond.classtime.service.helper

import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GridScheduleParser @Inject constructor(
    private val smartFieldIdentifier: SmartFieldIdentifier
) {

    companion object {
        private const val TAG = "GridScheduleParser"

        private val DAY_KEYWORDS = mapOf(
            "星期一" to 1, "周一" to 1, "Monday" to 1, "一" to 1,
            "星期二" to 2, "周二" to 2, "Tuesday" to 2, "二" to 2,
            "星期三" to 3, "周三" to 3, "Wednesday" to 3, "三" to 3,
            "星期四" to 4, "周四" to 4, "Thursday" to 4, "四" to 4,
            "星期五" to 5, "周五" to 5, "Friday" to 5, "五" to 5,
            "星期六" to 6, "周六" to 6, "Saturday" to 6, "六" to 6,
            "星期日" to 7, "周日" to 7, "Sunday" to 7, "星期天" to 7, "日" to 7,
        )

        private val SECTION_KEYWORDS = mapOf(
            "一" to 1, "二" to 2, "三" to 3, "四" to 4,
            "五" to 5, "六" to 6, "七" to 7, "八" to 8,
            "九" to 9, "十" to 10, "十一" to 11, "十二" to 12,
            "1" to 1, "2" to 2, "3" to 3, "4" to 4,
            "5" to 5, "6" to 6, "7" to 7, "8" to 8,
            "9" to 9, "10" to 10, "11" to 11, "12" to 12,
        )

        private val SECTION_LABELS = setOf("上午", "下午", "晚上", "晚间", "午间")

        private val DAY_PARTIAL_KEYWORDS = listOf("星期", "周")

        private val SECTION_RANGE_PATTERN = Regex("""\(?(\d+)\s*[-–—]\s*(\d+)\s*节?\)?""")
        private val WEEKS_RANGE_PATTERN = Regex("""(\d+)\s*[-–—]\s*(\d+)\s*周?\s*(?:\(([单双])\))?""")
        private val SINGLE_WEEK_PATTERN = Regex("""(\d+)\s*周""")
        private val LOCATION_PATTERN = Regex("""[/\\]\s*([^/\\]*?(?:楼|室|厅|区|层|栋|座|园|场|馆|院|中心|基地|实验室|教室|机房|多)[^/\\]*?)\s*(?:[/\\]|$)""")
        private val TEACHER_PATTERN = Regex("""(?:教师|讲师|老师|授课教师|任课教师|任课老师|授课老师|导员|导师|教授|辅导)\s*[:：]?\s*([\u4e00-\u9fa5]{2,4})""")
        private val TEACHER_SLASH_PATTERN = Regex("""[/\\]\s*([\u4e00-\u9fa5]{2,4})\s*(?:[/\\]|$)""")

        private val COURSE_CELL_PATTERNS = listOf(
            Regex("""^(.+?)/\((\d+)[-–—](\d+)节\)\s*(.+?)\s*/\s*(.+?)(?:/\s*(.+?))?(?:/\s*(.+?))?(?:/\s*(.+?))?(?:/\s*(.+?))?$""", RegexOption.DOT_MATCHES_ALL),
            Regex("""^(.+?)[/\s]\(?(\d+)[-–—](\d+)节?\)?[/\s](.+?)[/\s](.+?)(?:[/\s](.+?))?(?:[/\s](.+?))?$""", RegexOption.DOT_MATCHES_ALL),
            Regex("""^(.+?)/\s*(\d+)[-–—](\d+)\s*节\s*/\s*(.+?)\s*/\s*(.+?)(?:/\s*(.+?))?$""", RegexOption.DOT_MATCHES_ALL),
        )

        private val SECTION_EXTRACT_PATTERN = Regex("""\((\d+)\s*[-–—~～]\s*(\d+)\s*节\)""")
    }

    fun isGridSchedule(sheetData: List<List<String>>): Boolean {
        if (sheetData.size < 3) return false

        val headerRows = sheetData.take(minOf(8, sheetData.size))
        var dayCount = 0
        var sectionCount = 0
        var hasDayPartial = false

        for (row in headerRows) {
            for (cell in row) {
                val trimmed = cell.trim()
                for ((keyword, _) in DAY_KEYWORDS) {
                    if (trimmed.equals(keyword, ignoreCase = true)) {
                        dayCount++
                        break
                    }
                }
                if (trimmed in SECTION_KEYWORDS) sectionCount++
                for (partial in DAY_PARTIAL_KEYWORDS) {
                    if (trimmed.contains(partial) && trimmed.length <= 4) {
                        hasDayPartial = true
                    }
                }
            }
        }

        if (dayCount >= 3 && sectionCount >= 2) return true
        if (dayCount >= 2 && hasDayPartial && sectionCount >= 1) return true
        if (dayCount >= 4) return true

        return false
    }

    fun parseGridSchedule(sheetData: List<List<String>>): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()

        val dayColumnMap = mutableMapOf<Int, Int>()
        val sectionRowMap = mutableMapOf<Int, Int>()

        for (rowIdx in sheetData.indices) {
            val row = sheetData[rowIdx]
            for (colIdx in row.indices) {
                val cell = row[colIdx].trim()

                DAY_KEYWORDS[cell]?.let { day ->
                    if (!dayColumnMap.containsValue(day)) {
                        dayColumnMap[colIdx] = day
                    }
                }

                SECTION_KEYWORDS[cell]?.let { section ->
                    if (!sectionRowMap.containsValue(section)) {
                        sectionRowMap[section] = rowIdx
                    }
                }
            }
        }

        if (dayColumnMap.isEmpty()) {
            AppLogger.d(TAG, "未检测到星期列")
            return emptyList()
        }

        AppLogger.d(TAG, "检测到星期列映射: $dayColumnMap")
        AppLogger.d(TAG, "检测到节次行映射: $sectionRowMap")

        val rowSectionCache = mutableMapOf<Int, Int>()

        for (rowIdx in sheetData.indices) {
            val row = sheetData[rowIdx]
            var rowSection = 0
            for (colIdx in row.indices) {
                val cell = row[colIdx].trim()
                SECTION_KEYWORDS[cell]?.let { rowSection = it }
            }
            if (rowSection > 0) {
                rowSectionCache[rowIdx] = rowSection
            }
        }

        for (rowIdx in sheetData.indices) {
            val row = sheetData[rowIdx]
            var rowSection = rowSectionCache[rowIdx] ?: inferSectionFromRow(rowIdx, sectionRowMap)

            for ((colIdx, dayOfWeek) in dayColumnMap) {
                if (colIdx >= row.size) continue
                val cellContent = row[colIdx].trim()
                if (cellContent.isBlank()) continue
                if (isHeaderCell(cellContent)) continue
                if (cellContent.length < 2) continue

                val parsedCourses = parseCourseCell(cellContent, dayOfWeek, rowSection)
                courses.addAll(parsedCourses)
            }
        }

        val result = courses.distinctBy { "${it.courseName}_${it.dayOfWeek}_${it.startSection}_${it.weekExpression}" }
        AppLogger.d(TAG, "网格课表解析完成: 共${result.size}门课程")
        return result
    }

    private fun inferSectionFromRow(rowIdx: Int, sectionRowMap: Map<Int, Int>): Int {
        val sorted = sectionRowMap.entries.sortedBy { it.value }
        for (i in sorted.indices) {
            if (rowIdx >= sorted[i].value) {
                if (i + 1 < sorted.size && rowIdx < sorted[i + 1].value) {
                    return sorted[i].key
                }
                if (i == sorted.lastIndex) {
                    return sorted[i].key
                }
            }
        }
        return 0
    }

    private fun isHeaderCell(cell: String): Boolean {
        if (cell in DAY_KEYWORDS) return true
        if (cell in SECTION_KEYWORDS) return true
        if (cell in SECTION_LABELS) return true
        if (DAY_PARTIAL_KEYWORDS.any { cell.contains(it) } && cell.length <= 4) return true
        return false
    }

    internal fun parseCourseCell(cellContent: String, dayOfWeek: Int, fallbackSection: Int): List<ParsedCourse> {
        val results = mutableListOf<ParsedCourse>()

        val normalizedContent = cellContent
            .replace("_x000D_\n", "\n")
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        val parts = normalizedContent.split("\n").map { it.trim() }.filter { it.isNotBlank() && it.length >= 2 }

        for (part in parts) {
            val course = parseSingleCourseEntry(part, dayOfWeek, fallbackSection)
            if (course != null) results.add(course)
        }

        if (results.isEmpty()) {
            val course = parseSingleCourseEntry(normalizedContent.replace("\n", ""), dayOfWeek, fallbackSection)
            if (course != null) results.add(course)
        }

        if (results.isEmpty()) {
            val course = parseMinimalCourseEntry(normalizedContent.replace("\n", ""), dayOfWeek, fallbackSection)
            if (course != null) results.add(course)
        }

        return results
    }

    private fun parseSingleCourseEntry(entry: String, dayOfWeek: Int, fallbackSection: Int): ParsedCourse? {
        val smartResult = parseBySmartIdentification(entry, dayOfWeek, fallbackSection)
        if (smartResult != null && smartResult.courseName.length >= 2) {
            AppLogger.d(TAG, "智能识别成功: ${smartResult.courseName}")
            return smartResult
        }

        for ((idx, pattern) in COURSE_CELL_PATTERNS.withIndex()) {
            val match = pattern.find(entry)
            if (match != null) {
                AppLogger.d(TAG, "模式$idx 回退匹配成功: ${entry.take(30)}...")
                return buildCourseFromMatch(match, dayOfWeek, fallbackSection)
            }
        }

        return parseFlexibleCourseEntry(entry, dayOfWeek, fallbackSection)
    }

    private fun parseBySmartIdentification(entry: String, dayOfWeek: Int, fallbackSection: Int): ParsedCourse? {
        val (sectionInfo, remaining) = extractSectionInfo(entry)

        val segments = remaining.split("/")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (segments.isEmpty()) return null

        val allSegments = if (sectionInfo != null) {
            listOf(sectionInfo) + segments
        } else {
            segments
        }

        val result = smartFieldIdentifier.identifyFields(allSegments)

        val courseNameAssignment = result.assignments[SmartFieldIdentifier.FieldType.COURSE_NAME]
        if (courseNameAssignment == null || courseNameAssignment.confidence < 0.3f) return null

        val courseName = courseNameAssignment.value

        val sectionAssignment = result.assignments[SmartFieldIdentifier.FieldType.SECTION]
        val (startSection, sectionCount) = if (sectionAssignment != null) {
            parseSectionFromValue(sectionAssignment.value, fallbackSection)
        } else {
            Pair(fallbackSection, 1)
        }

        val weeksAssignment = result.assignments[SmartFieldIdentifier.FieldType.WEEKS]
        val (weeks, weekExpression) = if (weeksAssignment != null) {
            parseWeeksString(weeksAssignment.value)
        } else {
            Pair(emptyList<Int>(), "")
        }

        val classroom = result.assignments[SmartFieldIdentifier.FieldType.CLASSROOM]?.value ?: ""
        val teacher = result.assignments[SmartFieldIdentifier.FieldType.TEACHER]?.value ?: ""

        val className = result.assignments[SmartFieldIdentifier.FieldType.CLASS_NAME]?.value ?: ""
        val courseNature = result.assignments[SmartFieldIdentifier.FieldType.COURSE_NATURE]?.value ?: ""
        val studentCount = result.assignments[SmartFieldIdentifier.FieldType.STUDENT_COUNT]?.value?.toIntOrNull() ?: 0
        val selectedCount = result.assignments[SmartFieldIdentifier.FieldType.SELECTED_COUNT]?.value?.toIntOrNull() ?: 0
        val courseHours = result.assignments[SmartFieldIdentifier.FieldType.COURSE_HOURS]?.value ?: ""

        return ParsedCourse(
            courseName = courseName,
            teacher = teacher,
            classroom = classroom,
            dayOfWeek = dayOfWeek,
            startSection = startSection,
            sectionCount = sectionCount,
            weeks = weeks,
            weekExpression = weekExpression,
            credit = 0f,
            courseCode = "",
            className = className,
            courseNature = courseNature,
            studentCount = studentCount,
            selectedCount = selectedCount,
            courseHours = courseHours
        )
    }

    private fun extractSectionInfo(entry: String): Pair<String?, String> {
        val parenPattern = Regex("""\((\d+)\s*[-–—~～]\s*(\d+)\s*节\)""")
        val parenMatch = parenPattern.find(entry)
        if (parenMatch != null) {
            val sectionStr = "${parenMatch.groupValues[1]}-${parenMatch.groupValues[2]}节"
            val remaining = entry.replace(parenMatch.value, "")
            return Pair(sectionStr, remaining)
        }
        val barePattern = Regex("""(\d+)\s*[-–—~～]\s*(\d+)\s*节""")
        val bareMatch = barePattern.find(entry)
        if (bareMatch != null) {
            val sectionStr = "${bareMatch.groupValues[1]}-${bareMatch.groupValues[2]}节"
            val remaining = entry.replace(bareMatch.value, "")
            return Pair(sectionStr, remaining)
        }
        return Pair(null, entry)
    }

    private fun parseSectionFromValue(value: String, fallback: Int): Pair<Int, Int> {
        val match = Regex("""(\d+)\s*[-–—~～]\s*(\d+)""").find(value)
        if (match != null) {
            val start = match.groupValues[1].toIntOrNull() ?: fallback
            val end = match.groupValues[2].toIntOrNull() ?: start
            return Pair(start, end - start + 1)
        }
        return Pair(fallback, 1)
    }

    private fun buildCourseFromMatch(match: MatchResult, dayOfWeek: Int, fallbackSection: Int): ParsedCourse? {
        val courseName = match.groupValues[1].trim()
        val startSection = match.groupValues[2].toIntOrNull() ?: fallbackSection
        val endSection = match.groupValues[3].toIntOrNull() ?: startSection
        val weeksStr = match.groupValues[4].trim()

        val location = extractLocation(match.groupValues.drop(5).joinToString("/") { it.trim() })
        val teacher = extractTeacher(match.groupValues.drop(5).joinToString("/") { it.trim() })

        val (weeks, weekExpression) = parseWeeksString(weeksStr)

        return ParsedCourse(
            courseName = courseName,
            teacher = teacher,
            classroom = location,
            dayOfWeek = dayOfWeek,
            startSection = startSection,
            sectionCount = endSection - startSection + 1,
            weekExpression = weekExpression,
            weeks = weeks,
            credit = 0f,
            courseCode = "",
            className = "",
            courseNature = "",
            studentCount = 0,
            selectedCount = 0,
            courseHours = ""
        )
    }

    private fun parseFlexibleCourseEntry(entry: String, dayOfWeek: Int, fallbackSection: Int): ParsedCourse? {
        val sectionMatch = SECTION_RANGE_PATTERN.find(entry)
        val startSection = sectionMatch?.groupValues?.get(1)?.toIntOrNull() ?: fallbackSection
        val endSection = sectionMatch?.groupValues?.get(2)?.toIntOrNull() ?: startSection

        var courseName = entry.substringBefore("/").substringBefore("(").trim()
        if (courseName.length < 2) return null

        val (weeks, weekExpression) = parseWeeksString(entry)

        val location = extractLocation(entry)
        val teacher = extractTeacher(entry)

        return ParsedCourse(
            courseName = courseName,
            teacher = teacher,
            classroom = location,
            dayOfWeek = dayOfWeek,
            startSection = startSection,
            sectionCount = endSection - startSection + 1,
            weekExpression = weekExpression,
            weeks = weeks,
            credit = 0f,
            courseCode = "",
            className = "",
            courseNature = "",
            studentCount = 0,
            selectedCount = 0,
            courseHours = ""
        )
    }

    private fun parseMinimalCourseEntry(entry: String, dayOfWeek: Int, fallbackSection: Int): ParsedCourse? {
        val courseName = entry.trim()
        if (courseName.length < 2) return null
        if (courseName.all { it.isDigit() }) return null
        if (courseName in DAY_KEYWORDS) return null
        if (courseName in SECTION_KEYWORDS) return null

        AppLogger.d(TAG, "最小化解析课程: $courseName, day=$dayOfWeek, section=$fallbackSection")

        return ParsedCourse(
            courseName = courseName,
            teacher = "",
            classroom = "",
            dayOfWeek = dayOfWeek,
            startSection = fallbackSection,
            sectionCount = 2,
            weekExpression = "",
            weeks = emptyList(),
            credit = 0f,
            courseCode = "",
            className = "",
            courseNature = "",
            studentCount = 0,
            selectedCount = 0,
            courseHours = ""
        )
    }

    private fun extractLocation(text: String): String {
        val match = LOCATION_PATTERN.find(text)
        return match?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun extractTeacher(text: String): String {
        val keywordMatch = TEACHER_PATTERN.find(text)
        if (keywordMatch != null) {
            val name = keywordMatch.groupValues[1].trim()
            val excludeWords = listOf("必修", "选修", "限选", "任选", "实践", "讲课", "实验", "上机", "周", "节")
            if (name.length in 2..4 && excludeWords.none { name.contains(it) }) {
                return name
            }
        }

        val matches = TEACHER_SLASH_PATTERN.findAll(text).toList()
        if (matches.isEmpty()) return ""
        for (match in matches) {
            val name = match.groupValues[1].trim()
            val excludeWords = listOf("必修", "选修", "限选", "任选", "实践", "讲课", "实验", "上机", "周", "节")
            if (name.length in 2..4 && excludeWords.none { name.contains(it) }) {
                return name
            }
        }
        return ""
    }

    private fun parseWeeksString(weeksStr: String): Pair<List<Int>, String> {
        val weeks = mutableSetOf<Int>()

        WEEKS_RANGE_PATTERN.findAll(weeksStr).forEach { match ->
            val start = match.groupValues[1].toIntOrNull() ?: return@forEach
            val end = match.groupValues[2].toIntOrNull() ?: return@forEach
            val oddEven = match.groupValues.getOrNull(3)
            (start..end).filter { w ->
                when (oddEven) {
                    "单" -> w % 2 == 1
                    "双" -> w % 2 == 0
                    else -> true
                }
            }.forEach { weeks.add(it) }
        }

        if (weeks.isEmpty()) {
            val rangeNoWeek = Regex("""(\d+)\s*[-–—]\s*(\d+)""")
            rangeNoWeek.findAll(weeksStr).forEach { match ->
                val start = match.groupValues[1].toIntOrNull() ?: return@forEach
                val end = match.groupValues[2].toIntOrNull() ?: return@forEach
                if (start in 1..30 && end in 1..30 && end >= start) {
                    (start..end).forEach { weeks.add(it) }
                }
            }
        }

        if (weeks.isEmpty()) {
            SINGLE_WEEK_PATTERN.findAll(weeksStr).mapNotNull { it.groupValues[1].toIntOrNull() }
                .filter { it in 1..30 }.forEach { weeks.add(it) }
        }

        if (weeks.isEmpty()) {
            Regex("""(\d+)""").findAll(weeksStr).mapNotNull { it.groupValues[1].toIntOrNull() }
                .filter { it in 1..30 }.forEach { weeks.add(it) }
        }

        val sortedWeeks = weeks.sorted()
        val weekExpr = if (sortedWeeks.isNotEmpty()) {
            formatWeekExpression(sortedWeeks)
        } else ""

        return Pair(sortedWeeks, weekExpr)
    }

    private fun formatWeekExpression(weeks: List<Int>): String {
        if (weeks.isEmpty()) return ""
        val ranges = mutableListOf<String>()
        var rs = weeks[0]
        var re = weeks[0]
        for (i in 1 until weeks.size) {
            if (weeks[i] == re + 1) {
                re = weeks[i]
            } else {
                ranges.add(if (rs == re) "$rs" else "$rs-$re")
                rs = weeks[i]
                re = weeks[i]
            }
        }
        ranges.add(if (rs == re) "$rs" else "$rs-$re")
        return ranges.joinToString(",") + "周"
    }
}
