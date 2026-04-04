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

class TodayCourseDataProvider(private val context: Context) {

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

    suspend fun getTodayCourses(): WidgetDisplayData {
        return try {
            withTimeoutOrNull(DATABASE_TIMEOUT_MS) {
                getTodayCoursesInternal()
            } ?: run {
                WidgetDisplayData.empty("数据加载超时")
            }
        } catch (e: Exception) {
            WidgetDisplayData.empty("数据加载失败")
        }
    }

    private suspend fun getTodayCoursesInternal(): WidgetDisplayData {
        val db = getDatabase(context)
        val schedule = db.scheduleDao().getCurrentSchedule()
            ?: return WidgetDisplayData.empty("未设置课表")

        val now = LocalTime.now()
        val today = LocalDate.now()
        val currentWeekNumber = DateUtils.calculateWeekNumber(schedule.startDate, today)
        val todayDayOfWeek = today.dayOfWeek.value

        val allCourses = db.courseDao().getAllCourses()
            .filter { it.scheduleId == schedule.id }

        val adjustments = db.courseAdjustmentDao().getAdjustmentsByScheduleSync(schedule.id)

        val movedAwayCourses = adjustments.filter {
            it.originalWeekNumber == currentWeekNumber &&
                    it.originalDayOfWeek == todayDayOfWeek
        }.map { it.originalCourseId to it.originalStartSection }
            .toSet()

        val movedInAdjustments = adjustments.filter {
            it.newWeekNumber == currentWeekNumber &&
                    it.newDayOfWeek == todayDayOfWeek
        }

        val todayCourses = allCourses.filter { course ->
            course.dayOfWeek == todayDayOfWeek &&
                    currentWeekNumber in course.weeks &&
                    (course.id to course.startSection) !in movedAwayCourses
        }.toMutableList()

        movedInAdjustments.forEach { adjustment ->
            val originalCourse = allCourses.find { it.id == adjustment.originalCourseId }
            if (originalCourse != null) {
                todayCourses.add(
                    originalCourse.copy(
                        dayOfWeek = adjustment.newDayOfWeek,
                        startSection = adjustment.newStartSection,
                        sectionCount = adjustment.newSectionCount
                    )
                )
            }
        }

        todayCourses.sortBy { it.startSection }

        val classTimes = db.classTimeDao().getClassTimesByConfigSync("default")
        val classTimeMap = classTimes.associateBy { it.sectionNumber }

        val courseItems = todayCourses.map { course ->
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

        val finishedCount = courseItems.count { item ->
            if (item.endTimeText.isEmpty()) return@count false
            try {
                val endTime = LocalTime.parse(item.endTimeText, TIME_FORMATTER)
                now.isAfter(endTime)
            } catch (e: Exception) { false }
        }

        val progressText = if (courseItems.isNotEmpty()) {
            "已上${finishedCount}节/共${courseItems.size}节"
        } else ""

        return WidgetDisplayData(
            dateText = today.format(DATE_FORMATTER),
            dayOfWeekText = DateUtils.getDayOfWeekName(todayDayOfWeek),
            weekNumberText = "第${currentWeekNumber}周",
            courseItems = courseItems,
            emptyMessage = null,
            progressText = progressText
        )
    }

    private fun isOngoing(startTime: LocalTime?, endTime: LocalTime?): Boolean {
        if (startTime == null || endTime == null) return false
        val now = LocalTime.now()
        return now.isAfter(startTime) && now.isBefore(endTime)
    }
}
