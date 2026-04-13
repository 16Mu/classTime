package com.wind.ggbond.classtime.service.helper

import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.util.AppLogger
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportParser @Inject constructor() {

    companion object {
        private const val TAG = "ImportParser"
        private val TEACHER_PATTERNS = listOf(
            Regex("教师[：:](\\S+)"), Regex("Teacher[：:](\\S+)"),
            Regex("任课教师[：:](\\S+)"), Regex("授课教师[：:](\\S+)")
        )
        private val SECTION_PATTERNS = listOf(
            Regex("第(\\d+)-(\\d+)节"), Regex("(\\d+)-(\\d+)节"),
            Regex("节次[：:](\\d+)-(\\d+)"), Regex("Section[：:](\\d+)-(\\d+)")
        )
        private val WEEK_PATTERNS = listOf(
            Regex("(\\d+)-(\\d+)周(?:\\(([单双])\\))?"),
            Regex("周次[：:](\\d+)-(\\d+)"), Regex("Week[：:](\\d+)-(\\d+)")
        )
        private val DAY_MAP = mapOf(
            "周一" to 1, "星期一" to 1, "Monday" to 1, "1" to 1,
            "周二" to 2, "星期二" to 2, "Tuesday" to 2, "2" to 2,
            "周三" to 3, "星期三" to 3, "Wednesday" to 3, "3" to 3,
            "周四" to 4, "星期四" to 4, "Thursday" to 4, "4" to 4,
            "周五" to 5, "星期五" to 5, "Friday" to 5, "5" to 5,
            "周六" to 6, "星期六" to 6, "Saturday" to 6, "6" to 6,
            "周日" to 7, "星期日" to 7, "Sunday" to 7, "7" to 7,
        )
    }

    data class IcsEventData(val courseName: String, val teacher: String, val classroom: String,
        val startDateTime: LocalDateTime, val endDateTime: LocalDateTime?, val rrule: String,
        val sectionInfo: Pair<Int, Int>?, val weekInfo: List<Int>?)

    fun parseIcsContentFull(content: String, scheduleId: Long, semesterStartDate: LocalDate,
        classTimes: List<ClassTime>, existingColors: MutableList<String>): List<Course> {
        val courseMap = parseIcsEvents(content)
        val courses = buildCoursesFromEvents(courseMap, scheduleId, semesterStartDate, classTimes, existingColors)
        AppLogger.d(TAG, "ICS解析完成: ${courses.size} 门课程")
        return courses
    }

    private fun parseIcsEvents(content: String): Map<String, MutableList<IcsEventData>> {
        val courseMap = mutableMapOf<String, MutableList<IcsEventData>>()
        content.split("BEGIN:VEVENT").drop(1).forEach { event ->
            try {
                val summary = extractIcsField(event, "SUMMARY")
                if (summary.isEmpty() || extractIcsField(event, "DTSTART").isEmpty()) return@forEach
                val startDateTime = parseIcsDateTime(extractIcsField(event, "DTSTART")) ?: return@forEach
                val teacher = extractTeacherFromDescription(extractIcsField(event, "DESCRIPTION"))
                val location = extractIcsField(event, "LOCATION")
                val description = extractIcsField(event, "DESCRIPTION")
                val key = "$summary|$location|$teacher"
                courseMap.getOrPut(key) { mutableListOf() }
                    .add(IcsEventData(summary, teacher, location, startDateTime,
                        extractIcsField(event, "DTEND").takeIf { it.isNotEmpty() }?.let { parseIcsDateTime(it) },
                        extractIcsField(event, "RRULE"),
                        extractSectionFromDescription(description),
                        extractWeekFromDescription(description)))
            } catch (e: Exception) { AppLogger.w(TAG, "解析事件失败: ${e.message}") }
        }
        return courseMap
    }

    private fun buildCoursesFromEvents(
        courseMap: Map<String, MutableList<IcsEventData>>,
        scheduleId: Long,
        semesterStartDate: LocalDate,
        classTimes: List<ClassTime>,
        existingColors: MutableList<String>
    ): List<Course> {
        val courses = mutableListOf<Course>()
        courseMap.forEach { (_, eventList) ->
            val timeSlotMap = groupEventsByTimeSlot(eventList)
            timeSlotMap.forEach { (_, events) ->
                val first = events.first()
                val (startSec, secCount) = first.sectionInfo ?: matchSectionByTime(first.startDateTime.toLocalTime(), first.endDateTime?.toLocalTime(), classTimes)
                val weeks = first.weekInfo ?: calculateWeeksFromEvents(events, semesterStartDate)
                if (weeks.isEmpty()) return@forEach
                val color = com.wind.ggbond.classtime.util.CourseColorPalette.getColorForCourse(first.courseName, existingColors).also { existingColors.add(it) }
                courses.add(Course(courseName = first.courseName, teacher = first.teacher, classroom = first.classroom,
                    dayOfWeek = first.startDateTime.dayOfWeek.value, startSection = startSec, sectionCount = secCount,
                    weeks = weeks, weekExpression = formatWeekExpression(weeks), color = color, scheduleId = scheduleId,
                    createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
            }
        }
        return courses
    }

    private fun groupEventsByTimeSlot(eventList: List<IcsEventData>): Map<String, MutableList<IcsEventData>> {
        val timeSlotMap = mutableMapOf<String, MutableList<IcsEventData>>()
        eventList.forEach { e ->
            timeSlotMap.getOrPut("${e.startDateTime.dayOfWeek.value}|${e.startDateTime.toLocalTime()}") { mutableListOf() }.add(e)
        }
        return timeSlotMap
    }

    fun extractIcsField(event: String, fieldName: String): String {
        var result = StringBuilder(); var capturing = false
        for (line in event.lines()) {
            val t = line.trim()
            if (t.startsWith("$fieldName:") || t.startsWith("$fieldName;")) {
                capturing = true; t.indexOf(':').takeIf { it >= 0 }?.let { result.append(t.substring(it + 1)) }
            } else if (capturing) { if (line.startsWith(' ') || line.startsWith('\t')) result.append(line.substring(1)) else break }
        }
        return result.toString().replace("\\n", "\n").replace("\\,", ",").replace("\\\\", "\\").trim()
    }

    fun parseIcsDateTime(dtString: String): LocalDateTime? = try {
        var s = dtString.let { if (it.contains(":")) it.substringAfter(":") else it }
        val utc = s.endsWith("Z"); s = s.removeSuffix("Z")
        val dt = LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
        if (utc) dt.atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime() else dt
    } catch (e: Exception) { AppLogger.w(TAG, "日期解析失败: $dtString, ${e.message}"); null }

    fun extractTeacherFromDescription(description: String): String =
        TEACHER_PATTERNS.mapNotNull { it.find(description)?.groupValues?.get(1)?.trim() }.firstOrNull() ?: ""

    fun extractSectionFromDescription(description: String): Pair<Int, Int>? =
        SECTION_PATTERNS.mapNotNull { p -> p.find(description)?.let { m ->
            m.groupValues[1].toIntOrNull()?.let { s -> m.groupValues[2].toIntOrNull()?.let { e -> Pair(s, e - s + 1) } }
        }}.firstOrNull()

    fun extractWeekFromDescription(description: String): List<Int>? =
        WEEK_PATTERNS.mapNotNull { p -> p.find(description)?.let { m ->
            m.groupValues[1].toIntOrNull()?.let { s -> m.groupValues[2].toIntOrNull()?.let { e ->
                val odd = m.groupValues.getOrNull(3); (s..e).filter { w -> when (odd) { "单" -> w % 2 == 1; "双" -> w % 2 == 0; else -> true } }.toList()
            }}
        }}.firstOrNull()

    fun matchSectionByTime(startTime: LocalTime, endTime: LocalTime?, classTimes: List<ClassTime>): Pair<Int, Int> {
        if (classTimes.isEmpty()) return Pair(1, 2)
        var startSec = 1; var minDiff = Long.MAX_VALUE
        classTimes.forEach { ct -> kotlin.math.abs(ChronoUnit.MINUTES.between(startTime, ct.startTime)).also { d -> if (d < minDiff) { minDiff = d; startSec = ct.sectionNumber } } }
        var count = 2; endTime?.let { et ->
            minDiff = Long.MAX_VALUE; var endSec = startSec
            classTimes.forEach { ct -> kotlin.math.abs(ChronoUnit.MINUTES.between(et, ct.endTime)).also { d -> if (d < minDiff) { minDiff = d; endSec = ct.sectionNumber } } }
            count = maxOf(1, endSec - startSec + 1)
        }
        return Pair(startSec, count)
    }

    fun calculateWeeksFromEvents(events: List<IcsEventData>, semesterStartDate: LocalDate): List<Int> {
        val weeks = mutableSetOf<Int>(); val monday = semesterStartDate.with(DayOfWeek.MONDAY)
        events.forEach { e -> ChronoUnit.WEEKS.between(monday, e.startDateTime.toLocalDate().with(DayOfWeek.MONDAY)).toInt().let { w -> if (w + 1 in 1..25) weeks.add(w + 1) } }
        return weeks.sorted()
    }

    fun formatWeekExpression(weeks: List<Int>): String {
        if (weeks.isEmpty()) return ""
        val sorted = weeks.sorted(); val ranges = mutableListOf<String>(); var rs = sorted[0]; var re = sorted[0]
        for (i in 1 until sorted.size) { if (sorted[i] == re + 1) re = sorted[i] else { ranges.add(if (rs == re) "$rs" else "$rs-$re"); rs = sorted[i]; re = sorted[i] }}
        ranges.add(if (rs == re) "$rs" else "$rs-$re"); return ranges.joinToString(",") + "周"
    }

    fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        var cur = StringBuilder()
        var q = false
        line.forEach { c ->
            when {
                c == '"' -> q = !q
                c == ',' && !q -> {
                    fields.add(cur.toString())
                    cur = StringBuilder()
                }
                else -> cur.append(c)
            }
        }
        fields.add(cur.toString())
        return fields
    }

    fun parseDayOfWeek(dayStr: String): Int = DAY_MAP[dayStr.trim()] ?: dayStr.trim().toIntOrNull() ?: 1
    fun parseSection(sectionStr: String): Int = Regex("\\d+").find(sectionStr)?.value?.toIntOrNull() ?: 1

    fun parseWeeks(expr: String): List<Int> = try {
        when {
            expr.contains("-") && expr.contains("周") -> expr.replace("周", "").split("-").let { p ->
                if (p.size == 2) (IntRange(p[0].toIntOrNull() ?: 1, p[1].toIntOrNull() ?: 16)).toList() else emptyList()
            }
            expr.matches(Regex("\\d+")) -> listOf(expr.toInt())
            else -> emptyList()
        }
    } catch (_: Exception) { emptyList() }
}
