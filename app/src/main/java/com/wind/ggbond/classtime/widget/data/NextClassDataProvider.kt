package com.wind.ggbond.classtime.widget.data

import android.content.Context
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class NextClassDataProvider(private val context: Context) {

    companion object {
        private const val DATABASE_TIMEOUT_MS = 5000L
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
    }

    suspend fun getNextClass(): NextClassDisplayData {
        return try {
            withTimeoutOrNull(DATABASE_TIMEOUT_MS) {
                getNextClassInternal()
            } ?: run {
                NextClassDisplayData(hasNextClass = false, message = "数据加载超时")
            }
        } catch (e: Exception) {
            NextClassDisplayData(hasNextClass = false, message = "数据加载失败")
        }
    }

    private suspend fun getNextClassInternal(): NextClassDisplayData {
        val todayCourseProvider = TodayCourseDataProvider(context)
        val todayData = todayCourseProvider.getTodayCourses()

        if (todayData.courseItems.isEmpty()) {
            return NextClassDisplayData(
                hasNextClass = false,
                dayOfWeekText = todayData.dayOfWeekText,
                weekNumberText = todayData.weekNumberText,
                message = "今日无课程"
            )
        }

        val now = LocalTime.now()
        val ongoingCourse = todayData.courseItems.find { it.isOngoing }

        val nextCourse = todayData.courseItems.firstOrNull { item ->
            if (item.startTimeText.isEmpty()) return@firstOrNull false
            try {
                val startTime = LocalTime.parse(item.startTimeText, TIME_FORMATTER)
                startTime.isAfter(now)
            } catch (e: Exception) { false }
        }

        if (ongoingCourse != null && nextCourse != null) {
            return buildOngoingWithNextData(ongoingCourse, nextCourse, todayData)
        }

        if (ongoingCourse != null && nextCourse == null) {
            return buildOngoingLastData(ongoingCourse, todayData)
        }

        if (nextCourse != null) {
            return buildUpcomingData(nextCourse, todayData)
        }

        return NextClassDisplayData(
            hasNextClass = false,
            dayOfWeekText = todayData.dayOfWeekText,
            weekNumberText = todayData.weekNumberText,
            message = getTomorrowPreview()
        )
    }

    private fun buildOngoingWithNextData(
        ongoingCourse: WidgetCourseItem,
        nextCourse: WidgetCourseItem,
        todayData: WidgetDisplayData
    ): NextClassDisplayData {
        val (startTime, endTime) = parseTimes(ongoingCourse)
        val minutesRemaining = calculateMinutesRemaining(endTime)
        val totalMinutes = calculateTotalMinutes(startTime, endTime)
        val elapsedMinutes = calculateElapsedMinutes(startTime)

        return NextClassDisplayData(
            hasNextClass = true,
            isOngoing = true,
            courseName = nextCourse.courseName,
            classroom = nextCourse.classroom,
            teacher = nextCourse.teacher,
            timeText = "${nextCourse.startTimeText} - ${nextCourse.endTimeText}",
            sectionText = "${nextCourse.startSection}-${nextCourse.startSection + nextCourse.sectionCount - 1}节",
            color = nextCourse.color,
            minutesRemaining = minutesRemaining,
            totalMinutes = totalMinutes,
            elapsedMinutes = elapsedMinutes.coerceIn(0, totalMinutes),
            dayOfWeekText = todayData.dayOfWeekText,
            weekNumberText = todayData.weekNumberText,
            message = "正在上课"
        )
    }

    private fun buildOngoingLastData(
        ongoingCourse: WidgetCourseItem,
        todayData: WidgetDisplayData
    ): NextClassDisplayData {
        val (startTime, endTime) = parseTimes(ongoingCourse)
        val minutesRemaining = calculateMinutesRemaining(endTime)
        val totalMinutes = calculateTotalMinutes(startTime, endTime)
        val elapsedMinutes = calculateElapsedMinutes(startTime)

        return NextClassDisplayData(
            hasNextClass = true,
            isOngoing = true,
            courseName = ongoingCourse.courseName,
            classroom = ongoingCourse.classroom,
            teacher = ongoingCourse.teacher,
            timeText = "${ongoingCourse.startTimeText} - ${ongoingCourse.endTimeText}",
            sectionText = "${ongoingCourse.startSection}-${ongoingCourse.startSection + ongoingCourse.sectionCount - 1}节",
            color = ongoingCourse.color,
            minutesRemaining = minutesRemaining,
            totalMinutes = totalMinutes,
            elapsedMinutes = elapsedMinutes.coerceIn(0, totalMinutes),
            dayOfWeekText = todayData.dayOfWeekText,
            weekNumberText = todayData.weekNumberText,
            message = "今日最后一节"
        )
    }

    private fun buildUpcomingData(
        nextCourse: WidgetCourseItem,
        todayData: WidgetDisplayData
    ): NextClassDisplayData {
        val startTime = try {
            LocalTime.parse(nextCourse.startTimeText, TIME_FORMATTER)
        } catch (e: Exception) { null }
        val minutesRemaining = if (startTime != null) {
            java.time.Duration.between(LocalTime.now(), startTime).toMinutes().toInt()
        } else { 0 }

        return NextClassDisplayData(
            hasNextClass = true,
            isOngoing = false,
            courseName = nextCourse.courseName,
            classroom = nextCourse.classroom,
            teacher = nextCourse.teacher,
            timeText = "${nextCourse.startTimeText} - ${nextCourse.endTimeText}",
            sectionText = "${nextCourse.startSection}-${nextCourse.startSection + nextCourse.sectionCount - 1}节",
            color = nextCourse.color,
            minutesRemaining = minutesRemaining,
            dayOfWeekText = todayData.dayOfWeekText,
            weekNumberText = todayData.weekNumberText,
            message = null
        )
    }

    private suspend fun getTomorrowPreview(): String {
        return try {
            val todayCourseProvider = TodayCourseDataProvider(context)
            val todayData = todayCourseProvider.getTodayCourses()
            "今日课程已结束"
        } catch (e: Exception) {
            "今日课程已结束"
        }
    }

    private fun parseTimes(course: WidgetCourseItem): Pair<LocalTime?, LocalTime?> {
        val startTime = try {
            LocalTime.parse(course.startTimeText, TIME_FORMATTER)
        } catch (e: Exception) { null }
        val endTime = try {
            LocalTime.parse(course.endTimeText, TIME_FORMATTER)
        } catch (e: Exception) { null }
        return Pair(startTime, endTime)
    }

    private fun calculateMinutesRemaining(endTime: LocalTime?): Int {
        return if (endTime != null) {
            java.time.Duration.between(LocalTime.now(), endTime).toMinutes().toInt()
        } else { 0 }
    }

    private fun calculateTotalMinutes(startTime: LocalTime?, endTime: LocalTime?): Int {
        return if (startTime != null && endTime != null) {
            java.time.Duration.between(startTime, endTime).toMinutes().toInt()
        } else { 0 }
    }

    private fun calculateElapsedMinutes(startTime: LocalTime?): Int {
        return if (startTime != null) {
            java.time.Duration.between(startTime, LocalTime.now()).toMinutes().toInt()
        } else { 0 }
    }
}
