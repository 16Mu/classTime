package com.wind.ggbond.classtime.widget.data

import android.content.Context
import com.wind.ggbond.classtime.util.DateUtils
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TodayCourseDataProvider(private val context: Context) {

    companion object {
        private const val DATABASE_TIMEOUT_MS = 5000L
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("M月d日")
    }

    suspend fun getTodayCourses(): WidgetDisplayData {
        return try {
            withTimeoutOrNull(DATABASE_TIMEOUT_MS) {
                getTodayCoursesInternal()
            } ?: WidgetDisplayData.empty("数据加载超时")
        } catch (e: Exception) {
            WidgetDisplayData.empty("数据加载失败")
        }
    }

    private suspend fun getTodayCoursesInternal(): WidgetDisplayData {
        val db = WidgetDatabaseProvider.getDatabase(context)
        val schedule = db.scheduleDao().getCurrentSchedule()
            ?: return WidgetDisplayData.empty("未设置课表")

        val today = LocalDate.now()
        val currentWeekNumber = DateUtils.calculateWeekNumber(schedule.startDate, today)
        val todayDayOfWeek = today.dayOfWeek.value

        val allCourses = db.courseDao().getAllCourses().filter { it.scheduleId == schedule.id }
        val adjustments = db.courseAdjustmentDao().getAdjustmentsByScheduleSync(schedule.id)
        val classTimeMap = db.classTimeDao().getClassTimesByConfigSync("default")
            .associateBy { it.sectionNumber }

        val result = CourseFilterHelper.filterCoursesForDate(
            allCourses, adjustments, classTimeMap, currentWeekNumber, todayDayOfWeek
        )

        val progressText = CourseFilterHelper.calculateProgressText(result.courseItems)

        return WidgetDisplayData(
            dateText = today.format(DATE_FORMATTER),
            dayOfWeekText = DateUtils.getDayOfWeekName(todayDayOfWeek),
            weekNumberText = "第${currentWeekNumber}周",
            courseItems = result.courseItems,
            emptyMessage = null,
            progressText = progressText
        )
    }
}
