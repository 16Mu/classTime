package com.wind.ggbond.classtime.widget.data

import android.content.Context
import com.wind.ggbond.classtime.util.DateUtils
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TomorrowCourseDataProvider(private val context: Context) {

    companion object {
        private const val DATABASE_TIMEOUT_MS = 5000L
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("M月d日")
    }

    suspend fun getSmartCourses(): TomorrowCourseDisplayData {
        return try {
            withTimeoutOrNull(DATABASE_TIMEOUT_MS) {
                getSmartCoursesInternal()
            } ?: TomorrowCourseDisplayData.empty("数据加载超时")
        } catch (e: Exception) {
            TomorrowCourseDisplayData.empty("数据加载失败")
        }
    }

    private suspend fun getSmartCoursesInternal(): TomorrowCourseDisplayData {
        val db = WidgetDatabaseProvider.getDatabase(context)
        val schedule = db.scheduleDao().getCurrentSchedule()
            ?: return TomorrowCourseDisplayData.empty("未设置课表")

        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val currentWeekNumber = DateUtils.calculateWeekNumber(schedule.startDate, today)
        val tomorrowWeekNumber = DateUtils.calculateWeekNumber(schedule.startDate, tomorrow)
        val todayDayOfWeek = today.dayOfWeek.value
        val tomorrowDayOfWeek = tomorrow.dayOfWeek.value

        val allCourses = db.courseDao().getAllCourses().filter { it.scheduleId == schedule.id }
        val adjustments = db.courseAdjustmentDao().getAdjustmentsByScheduleSync(schedule.id)
        val classTimeMap = db.classTimeDao().getClassTimesByConfigSync("default")
            .associateBy { it.sectionNumber }

        val todayResult = CourseFilterHelper.filterCoursesForDate(
            allCourses, adjustments, classTimeMap, currentWeekNumber, todayDayOfWeek
        )

        if (todayResult.courseItems.isNotEmpty() &&
            CourseFilterHelper.hasOngoingOrUpcoming(todayResult.courseItems)
        ) {
            val progressText = CourseFilterHelper.calculateProgressText(todayResult.courseItems)
            return TomorrowCourseDisplayData(
                dateText = today.format(DATE_FORMATTER),
                dayOfWeekText = DateUtils.getDayOfWeekName(todayDayOfWeek),
                weekNumberText = "第${currentWeekNumber}周",
                courseItems = todayResult.courseItems,
                isShowingToday = true,
                statusLabel = "",
                emptyMessage = null,
                progressText = progressText
            )
        }

        val tomorrowResult = CourseFilterHelper.filterCoursesForDate(
            allCourses, adjustments, classTimeMap, tomorrowWeekNumber, tomorrowDayOfWeek,
            checkOngoing = false
        )

        val statusLabel = when {
            todayResult.courseItems.isNotEmpty() -> "今日课程已结束"
            else -> "今日无课"
        }

        return TomorrowCourseDisplayData(
            dateText = tomorrow.format(DATE_FORMATTER),
            dayOfWeekText = DateUtils.getDayOfWeekName(tomorrowDayOfWeek),
            weekNumberText = "第${tomorrowWeekNumber}周",
            courseItems = tomorrowResult.courseItems,
            isShowingToday = false,
            statusLabel = statusLabel,
            emptyMessage = if (tomorrowResult.courseItems.isEmpty()) "明日也无课程" else null,
            progressText = ""
        )
    }
}
