package com.wind.ggbond.classtime.service.helper

import android.util.Log
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
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

    private val TAG = "ImportParser"

    data class IcsEventData(
        val courseName: String,
        val teacher: String,
        val classroom: String,
        val startDateTime: LocalDateTime,
        val endDateTime: LocalDateTime?,
        val rrule: String,
        val sectionInfo: Pair<Int, Int>?,
        val weekInfo: List<Int>?
    )

    fun parseIcsContentFull(
        content: String,
        scheduleId: Long,
        semesterStartDate: LocalDate,
        classTimes: List<ClassTime>,
        existingColors: MutableList<String>
    ): List<Course> {
        val courseMap = mutableMapOf<String, MutableList<IcsEventData>>()
        val events = content.split("BEGIN:VEVENT")

        Log.d(TAG, "解析ICS文件，共 ${events.size - 1} 个事件")

        events.drop(1).forEach { event ->
            try {
                val summary = extractIcsField(event, "SUMMARY")
                val location = extractIcsField(event, "LOCATION")
                val description = extractIcsField(event, "DESCRIPTION")
                val dtStart = extractIcsField(event, "DTSTART")
                val dtEnd = extractIcsField(event, "DTEND")
                val rrule = extractIcsField(event, "RRULE")

                if (summary.isEmpty() || dtStart.isEmpty()) {
                    Log.w(TAG, "跳过无效事件: summary=$summary, dtStart=$dtStart")
                    return@forEach
                }

                val startDateTime = parseIcsDateTime(dtStart)
                val endDateTime = if (dtEnd.isNotEmpty()) parseIcsDateTime(dtEnd) else null

                if (startDateTime == null) {
                    Log.w(TAG, "无法解析开始时间: $dtStart")
                    return@forEach
                }

                val teacher = extractTeacherFromDescription(description)
                val sectionInfo = extractSectionFromDescription(description)
                val weekInfo = extractWeekFromDescription(description)

                val eventData = IcsEventData(
                    courseName = summary,
                    teacher = teacher,
                    classroom = location,
                    startDateTime = startDateTime,
                    endDateTime = endDateTime,
                    rrule = rrule,
                    sectionInfo = sectionInfo,
                    weekInfo = weekInfo
                )

                val key = "$summary|$teacher|$location"
                courseMap.getOrPut(key) { mutableListOf() }.add(eventData)

            } catch (e: Exception) {
                Log.w(TAG, "解析事件失败: ${e.message}")
            }
        }

        Log.d(TAG, "分组后共 ${courseMap.size} 门不同课程")

        val courses = mutableListOf<Course>()

        courseMap.forEach { (_, eventList) ->
            val timeSlotMap = mutableMapOf<String, MutableList<IcsEventData>>()

            eventList.forEach { event ->
                val dayOfWeek = event.startDateTime.dayOfWeek.value
                val startTime = event.startDateTime.toLocalTime()
                val key = "$dayOfWeek|$startTime"
                timeSlotMap.getOrPut(key) { mutableListOf() }.add(event)
            }

            timeSlotMap.forEach { (_, events) ->
                val firstEvent = events.first()

                val dayOfWeek = firstEvent.startDateTime.dayOfWeek.value

                val startTime = firstEvent.startDateTime.toLocalTime()
                val endTime = firstEvent.endDateTime?.toLocalTime()
                val (startSection, sectionCount) = if (firstEvent.sectionInfo != null) {
                    firstEvent.sectionInfo
                } else {
                    matchSectionByTime(startTime, endTime, classTimes)
                }

                val weeks = if (firstEvent.weekInfo != null) {
                    firstEvent.weekInfo
                } else {
                    calculateWeeksFromEvents(events, semesterStartDate)
                }

                if (weeks.isEmpty()) {
                    Log.w(TAG, "课程 ${firstEvent.courseName} 周次为空，跳过")
                    return@forEach
                }

                val weekExpression = formatWeekExpression(weeks)

                val assignedColor = com.wind.ggbond.classtime.util.CourseColorPalette.getColorForCourse(firstEvent.courseName, existingColors)
                existingColors.add(assignedColor)

                val course = Course(
                    courseName = firstEvent.courseName,
                    teacher = firstEvent.teacher,
                    classroom = firstEvent.classroom,
                    dayOfWeek = dayOfWeek,
                    startSection = startSection,
                    sectionCount = sectionCount,
                    weeks = weeks,
                    weekExpression = weekExpression,
                    color = assignedColor,
                    scheduleId = scheduleId,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                courses.add(course)
                Log.d(TAG, "创建课程: ${course.courseName}, 周${course.dayOfWeek} 第${course.startSection}-${course.startSection + course.sectionCount - 1}节, ${course.weekExpression}")
            }
        }

        return courses
    }

    fun extractIcsField(event: String, fieldName: String): String {
        val lines = event.lines()
        var result = StringBuilder()
        var isCapturing = false

        for (line in lines) {
            val trimmedLine = line.trim()

            if (trimmedLine.startsWith("$fieldName:") || trimmedLine.startsWith("$fieldName;")) {
                isCapturing = true
                val colonIndex = trimmedLine.indexOf(':')
                if (colonIndex >= 0) {
                    result.append(trimmedLine.substring(colonIndex + 1))
                }
            } else if (isCapturing) {
                if (line.startsWith(" ") || line.startsWith("\t")) {
                    result.append(line.substring(1))
                } else {
                    break
                }
            }
        }

        return result.toString()
            .replace("\\n", "\n")
            .replace("\\,", ",")
            .replace("\\\\", "\\")
            .trim()
    }

    fun parseIcsDateTime(dtString: String): LocalDateTime? {
        return try {
            var dateTimeStr = dtString

            if (dateTimeStr.contains(":")) {
                dateTimeStr = dateTimeStr.substringAfter(":")
            }

            val isUtc = dateTimeStr.endsWith("Z")
            dateTimeStr = dateTimeStr.removeSuffix("Z")

            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
            var dateTime = LocalDateTime.parse(dateTimeStr, formatter)

            if (isUtc) {
                val utcZone = ZoneId.of("UTC")
                val localZone = ZoneId.systemDefault()
                dateTime = dateTime.atZone(utcZone).withZoneSameInstant(localZone).toLocalDateTime()
            }

            dateTime
        } catch (e: Exception) {
            Log.w(TAG, "解析日期时间失败: $dtString, ${e.message}")
            null
        }
    }

    fun extractTeacherFromDescription(description: String): String {
        val patterns = listOf(
            Regex("教师[：:](\\S+)"),
            Regex("Teacher[：:](\\S+)"),
            Regex("任课教师[：:](\\S+)"),
            Regex("授课教师[：:](\\S+)")
        )

        for (pattern in patterns) {
            val match = pattern.find(description)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }

        return ""
    }

    fun extractSectionFromDescription(description: String): Pair<Int, Int>? {
        val patterns = listOf(
            Regex("第(\\d+)-(\\d+)节"),
            Regex("(\\d+)-(\\d+)节"),
            Regex("节次[：:](\\d+)-(\\d+)"),
            Regex("Section[：:](\\d+)-(\\d+)")
        )

        for (pattern in patterns) {
            val match = pattern.find(description)
            if (match != null) {
                val start = match.groupValues[1].toIntOrNull() ?: continue
                val end = match.groupValues[2].toIntOrNull() ?: continue
                return Pair(start, end - start + 1)
            }
        }

        return null
    }

    fun extractWeekFromDescription(description: String): List<Int>? {
        val patterns = listOf(
            Regex("(\\d+)-(\\d+)周(?:\\(([单双])\\))?"),
            Regex("周次[：:](\\d+)-(\\d+)"),
            Regex("Week[：:](\\d+)-(\\d+)")
        )

        for (pattern in patterns) {
            val match = pattern.find(description)
            if (match != null) {
                val start = match.groupValues[1].toIntOrNull() ?: continue
                val end = match.groupValues[2].toIntOrNull() ?: continue
                val oddEven = match.groupValues.getOrNull(3)

                val weeks = mutableListOf<Int>()
                for (week in start..end) {
                    when (oddEven) {
                        "单" -> if (week % 2 == 1) weeks.add(week)
                        "双" -> if (week % 2 == 0) weeks.add(week)
                        else -> weeks.add(week)
                    }
                }
                return weeks
            }
        }

        return null
    }

    fun matchSectionByTime(
        startTime: LocalTime,
        endTime: LocalTime?,
        classTimes: List<ClassTime>
    ): Pair<Int, Int> {
        if (classTimes.isEmpty()) {
            return Pair(1, 2)
        }

        var startSection = 1
        var minDiff = Long.MAX_VALUE

        for (classTime in classTimes) {
            val diff = kotlin.math.abs(ChronoUnit.MINUTES.between(startTime, classTime.startTime))
            if (diff < minDiff) {
                minDiff = diff
                startSection = classTime.sectionNumber
            }
        }

        var sectionCount = 2

        if (endTime != null) {
            var endSection = startSection
            minDiff = Long.MAX_VALUE

            for (classTime in classTimes) {
                val diff = kotlin.math.abs(ChronoUnit.MINUTES.between(endTime, classTime.endTime))
                if (diff < minDiff) {
                    minDiff = diff
                    endSection = classTime.sectionNumber
                }
            }

            sectionCount = maxOf(1, endSection - startSection + 1)
        }

        return Pair(startSection, sectionCount)
    }

    fun calculateWeeksFromEvents(
        events: List<IcsEventData>,
        semesterStartDate: LocalDate
    ): List<Int> {
        val weeks = mutableSetOf<Int>()

        val semesterStartMonday = semesterStartDate.with(DayOfWeek.MONDAY)

        events.forEach { event ->
            val eventDate = event.startDateTime.toLocalDate()
            val eventMonday = eventDate.with(DayOfWeek.MONDAY)

            val weeksBetween = ChronoUnit.WEEKS.between(semesterStartMonday, eventMonday).toInt() + 1

            if (weeksBetween in 1..25) {
                weeks.add(weeksBetween)
            }
        }

        return weeks.sorted()
    }

    fun formatWeekExpression(weeks: List<Int>): String {
        if (weeks.isEmpty()) return ""

        val sortedWeeks = weeks.sorted()
        val ranges = mutableListOf<String>()
        var rangeStart = sortedWeeks[0]
        var rangeEnd = sortedWeeks[0]

        for (i in 1 until sortedWeeks.size) {
            if (sortedWeeks[i] == rangeEnd + 1) {
                rangeEnd = sortedWeeks[i]
            } else {
                ranges.add(if (rangeStart == rangeEnd) "$rangeStart" else "$rangeStart-$rangeEnd")
                rangeStart = sortedWeeks[i]
                rangeEnd = sortedWeeks[i]
            }
        }

        ranges.add(if (rangeStart == rangeEnd) "$rangeStart" else "$rangeStart-$rangeEnd")

        return ranges.joinToString(",") + "周"
    }

    fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        var currentField = StringBuilder()
        var inQuotes = false

        line.forEach { char ->
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    fields.add(currentField.toString())
                    currentField = StringBuilder()
                }
                else -> currentField.append(char)
            }
        }
        fields.add(currentField.toString())

        return fields
    }

    fun parseDayOfWeek(dayStr: String): Int {
        return when (dayStr) {
            "周一", "星期一", "Monday" -> 1
            "周二", "星期二", "Tuesday" -> 2
            "周三", "星期三", "Wednesday" -> 3
            "周四", "星期四", "Thursday" -> 4
            "周五", "星期五", "Friday" -> 5
            "周六", "星期六", "Saturday" -> 6
            "周日", "星期日", "Sunday" -> 7
            else -> dayStr.toIntOrNull() ?: 1
        }
    }

    fun parseSection(sectionStr: String): Int {
        val match = Regex("\\d+").find(sectionStr)
        return match?.value?.toIntOrNull() ?: 1
    }

    fun parseWeeks(weekExpression: String): List<Int> {
        val weeks = mutableListOf<Int>()

        try {
            if (weekExpression.contains("-") && weekExpression.contains("周")) {
                val parts = weekExpression.replace("周", "").split("-")
                if (parts.size == 2) {
                    val start = parts[0].toIntOrNull() ?: 1
                    val end = parts[1].toIntOrNull() ?: 16
                    for (i in start..end) {
                        weeks.add(i)
                    }
                }
            } else if (weekExpression.matches(Regex("\\d+"))) {
                weekExpression.toIntOrNull()?.let { weeks.add(it) }
            } else {
                for (i in 1..16) {
                    weeks.add(i)
                }
            }
        } catch (e: Exception) {
            for (i in 1..16) {
                weeks.add(i)
            }
        }

        return weeks
    }
}
