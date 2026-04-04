package com.wind.ggbond.classtime.widget.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class WidgetDisplayData(
    val dateText: String,
    val dayOfWeekText: String,
    val weekNumberText: String,
    val courseItems: List<WidgetCourseItem>,
    val emptyMessage: String?,
    val progressText: String = ""
) {
    companion object {
        fun empty(message: String) = WidgetDisplayData(
            dateText = LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日")),
            dayOfWeekText = com.wind.ggbond.classtime.util.DateUtils.getDayOfWeekName(LocalDate.now().dayOfWeek.value),
            weekNumberText = "",
            courseItems = emptyList(),
            emptyMessage = message,
            progressText = ""
        )
    }
}

data class WidgetCourseItem(
    val courseName: String,
    val classroom: String,
    val teacher: String,
    val startSection: Int,
    val sectionCount: Int,
    val startTimeText: String,
    val endTimeText: String,
    val color: String,
    val isOngoing: Boolean
)

data class NextClassDisplayData(
    val hasNextClass: Boolean,
    val isOngoing: Boolean = false,
    val courseName: String = "",
    val classroom: String = "",
    val teacher: String = "",
    val timeText: String = "",
    val sectionText: String = "",
    val color: String = "",
    val minutesRemaining: Int = 0,
    val totalMinutes: Int = 0,
    val elapsedMinutes: Int = 0,
    val dayOfWeekText: String = "",
    val weekNumberText: String = "",
    val message: String? = null
)

data class WeekOverviewData(
    val weekNumberText: String,
    val dateRangeText: String,
    val todayDayOfWeek: Int,
    val days: List<DayCourseInfo>,
    val emptyMessage: String? = null
) {
    companion object {
        fun empty(message: String) = WeekOverviewData(
            weekNumberText = "",
            dateRangeText = "",
            todayDayOfWeek = LocalDate.now().dayOfWeek.value,
            days = emptyList(),
            emptyMessage = message
        )
    }
}

data class DayCourseInfo(
    val dayOfWeek: Int,
    val courseCount: Int,
    val courses: List<CourseBrief>
)

data class CourseBrief(
    val name: String,
    val startSection: Int,
    val endSection: Int
) {
    val sectionLabel: String get() = "$startSection-$endSection"

    val displayLabel: String get() = "$name($sectionLabel)"
}
