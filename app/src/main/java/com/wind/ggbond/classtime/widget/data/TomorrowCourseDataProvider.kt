package com.wind.ggbond.classtime.widget.data

import android.content.Context
import androidx.room.Room
import com.wind.ggbond.classtime.data.local.database.CourseDatabase
import com.wind.ggbond.classtime.data.local.database.Migrations
import com.wind.ggbond.classtime.util.DateUtils
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class TomorrowCourseDataProvider(private val context: Context) {

    companion object {
        private const val DATABASE_TIMEOUT_MS = 5000L
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("M月d日")
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

        @Volatile
        private var databaseInstance: CourseDatabase? = null

        private fun getDatabase(context: Context): CourseDatabase {
            return databaseInstance ?: synchronized(this) {
                databaseInstance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CourseDatabase::class.java,
                    CourseDatabase.DATABASE_NAME
                )
                    .addMigrations(*Migrations.getAllMigrations())
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { databaseInstance = it }
            }
        }
    }

    suspend fun getSmartCourses(): TomorrowCourseDisplayData {
        return try {
            withTimeoutOrNull(DATABASE_TIMEOUT_MS) {
                getSmartCoursesInternal()
            } ?: run {
                TomorrowCourseDisplayData.empty("数据加载超时")
            }
        } catch (e: Exception) {
            TomorrowCourseDisplayData.empty("数据加载失败")
        }
    }

    private suspend fun getSmartCoursesInternal(): TomorrowCourseDisplayData {
        val db = getDatabase(context)

        val schedule = db.scheduleDao().getCurrentSchedule()
            ?: return TomorrowCourseDisplayData.empty("未设置课表")

        val now = LocalTime.now()
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val currentWeekNumber = DateUtils.calculateWeekNumber(schedule.startDate, today)
        val tomorrowWeekNumber = DateUtils.calculateWeekNumber(schedule.startDate, tomorrow)

        val todayDayOfWeek = today.dayOfWeek.value
        val tomorrowDayOfWeek = tomorrow.dayOfWeek.value

        val allCourses = db.courseDao().getAllCourses()
            .filter { it.scheduleId == schedule.id }

        val adjustments = db.courseAdjustmentDao().getAdjustmentsByScheduleSync(schedule.id)

        val movedAwayToday = adjustments.filter {
            it.originalWeekNumber == currentWeekNumber &&
                    it.originalDayOfWeek == todayDayOfWeek
        }.map { it.originalCourseId to it.originalStartSection }.toSet()

        val movedInToday = adjustments.filter {
            it.newWeekNumber == currentWeekNumber &&
                    it.newDayOfWeek == todayDayOfWeek
        }

        val movedInTomorrow = adjustments.filter {
            it.newWeekNumber == tomorrowWeekNumber &&
                    it.newDayOfWeek == tomorrowDayOfWeek
        }

        val classTimes = db.classTimeDao().getClassTimesByConfigSync("default")
        val classTimeMap = classTimes.associateBy { it.sectionNumber }

        val todayCourses = allCourses.filter { course ->
            course.dayOfWeek == todayDayOfWeek &&
                    currentWeekNumber in course.weeks &&
                    (course.id to course.startSection) !in movedAwayToday
        }.toMutableList()

        movedInToday.forEach { adj ->
            val originalCourse = allCourses.find { it.id == adj.originalCourseId }
            if (originalCourse != null) {
                todayCourses.add(
                    originalCourse.copy(
                        dayOfWeek = adj.newDayOfWeek,
                        startSection = adj.newStartSection,
                        sectionCount = adj.newSectionCount
                    )
                )
            }
        }

        todayCourses.sortBy { it.startSection }

        val todayItems = todayCourses.map { course ->
            val startTime = classTimeMap[course.startSection]?.startTime
            val endSection = course.startSection + course.sectionCount - 1
            val endTime = classTimeMap[endSection]?.endTime
            WidgetCourseItem(
                courseName = course.courseName,
                classroom = course.classroom,
                teacher = course.teacher,
                startSection = course.startSection,
                sectionCount = course.sectionCount,
                startTimeText = startTime?.format(TIME_FORMATTER) ?: "",
                endTimeText = endTime?.format(TIME_FORMATTER) ?: "",
                color = course.color,
                isOngoing = isOngoing(startTime, endTime)
            )
        }

        val hasOngoingOrUpcoming = todayItems.any { item ->
            if (item.endTimeText.isEmpty()) return@any false
            try {
                val endTime = LocalTime.parse(item.endTimeText, TIME_FORMATTER)
                now.isBefore(endTime) || item.isOngoing
            } catch (e: Exception) { false }
        }

        if (todayItems.isNotEmpty() && hasOngoingOrUpcoming) {
            val finishedCount = todayItems.count { item ->
                if (item.endTimeText.isEmpty()) return@count false
                try {
                    val endTime = LocalTime.parse(item.endTimeText, TIME_FORMATTER)
                    now.isAfter(endTime)
                } catch (e: Exception) { false }
            }

            val progressText = if (finishedCount > 0 && finishedCount < todayItems.size) {
                "已上${finishedCount}节/共${todayItems.size}节"
            } else ""

            return TomorrowCourseDisplayData(
                dateText = today.format(DATE_FORMATTER),
                dayOfWeekText = DateUtils.getDayOfWeekName(todayDayOfWeek),
                weekNumberText = "第${currentWeekNumber}周",
                courseItems = todayItems,
                isShowingToday = true,
                statusLabel = "",
                emptyMessage = null,
                progressText = progressText
            )
        }

        val tomorrowCourses = allCourses.filter { course ->
            course.dayOfWeek == tomorrowDayOfWeek &&
                    tomorrowWeekNumber in course.weeks
        }.toMutableList()

        movedInTomorrow.forEach { adj ->
            val originalCourse = allCourses.find { it.id == adj.originalCourseId }
            if (originalCourse != null) {
                tomorrowCourses.add(
                    originalCourse.copy(
                        dayOfWeek = adj.newDayOfWeek,
                        startSection = adj.newStartSection,
                        sectionCount = adj.newSectionCount
                    )
                )
            }
        }

        tomorrowCourses.sortBy { it.startSection }

        val tomorrowItems = tomorrowCourses.map { course ->
            val startTime = classTimeMap[course.startSection]?.startTime
            val endSection = course.startSection + course.sectionCount - 1
            val endTime = classTimeMap[endSection]?.endTime
            WidgetCourseItem(
                courseName = course.courseName,
                classroom = course.classroom,
                teacher = course.teacher,
                startSection = course.startSection,
                sectionCount = course.sectionCount,
                startTimeText = startTime?.format(TIME_FORMATTER) ?: "",
                endTimeText = endTime?.format(TIME_FORMATTER) ?: "",
                color = course.color,
                isOngoing = false
            )
        }

        val statusLabel = when {
            todayItems.isNotEmpty() -> "今日课程已结束"
            else -> "今日无课"
        }

        return TomorrowCourseDisplayData(
            dateText = tomorrow.format(DATE_FORMATTER),
            dayOfWeekText = DateUtils.getDayOfWeekName(tomorrowDayOfWeek),
            weekNumberText = "第${tomorrowWeekNumber}周",
            courseItems = tomorrowItems,
            isShowingToday = false,
            statusLabel = statusLabel,
            emptyMessage = if (tomorrowItems.isEmpty()) "明日也无课程" else null,
            progressText = ""
        )
    }

    private fun isOngoing(startTime: LocalTime?, endTime: LocalTime?): Boolean {
        if (startTime == null || endTime == null) return false
        val now = LocalTime.now()
        return now.isAfter(startTime) && now.isBefore(endTime)
    }
}

data class TomorrowCourseDisplayData(
    val dateText: String,
    val dayOfWeekText: String,
    val weekNumberText: String,
    val courseItems: List<WidgetCourseItem>,
    val isShowingToday: Boolean,
    val statusLabel: String,
    val emptyMessage: String?,
    val progressText: String = ""
) {
    companion object {
        fun empty(message: String) = TomorrowCourseDisplayData(
            dateText = LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日")),
            dayOfWeekText = DateUtils.getDayOfWeekName(LocalDate.now().dayOfWeek.value),
            weekNumberText = "",
            courseItems = emptyList(),
            isShowingToday = true,
            statusLabel = "",
            emptyMessage = message,
            progressText = ""
        )
    }
}
