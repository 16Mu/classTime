package com.wind.ggbond.classtime.widget.data

import android.content.Context
import androidx.room.Room
import com.wind.ggbond.classtime.data.local.database.CourseDatabase
import com.wind.ggbond.classtime.data.local.database.Migrations
import com.wind.ggbond.classtime.util.DateUtils
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class WeekOverviewDataProvider(private val context: Context) {

    companion object {
        private const val DATABASE_TIMEOUT_MS = 5000L
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("M月d日")

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

    suspend fun getWeekOverview(): WeekOverviewData {
        return try {
            withTimeoutOrNull(DATABASE_TIMEOUT_MS) {
                getWeekOverviewInternal()
            } ?: run {
                WeekOverviewData.empty("数据加载超时")
            }
        } catch (e: Exception) {
            WeekOverviewData.empty("数据加载失败")
        }
    }

    private suspend fun getWeekOverviewInternal(): WeekOverviewData {
        val db = getDatabase(context)

        val schedule = db.scheduleDao().getCurrentSchedule()
            ?: return WeekOverviewData.empty("未设置课表")

        val today = LocalDate.now()
        val currentWeekNumber = DateUtils.calculateWeekNumber(schedule.startDate, today)
        val todayDayOfWeek = today.dayOfWeek.value

        val allCourses = db.courseDao().getAllCourses()
            .filter { it.scheduleId == schedule.id }

        val days = (1..7).map { dayOfWeek ->
            val dayCourses = allCourses.filter { course ->
                course.dayOfWeek == dayOfWeek && currentWeekNumber in course.weeks
            }.sortedBy { it.startSection }

            val courses = dayCourses.map { course ->
                CourseBrief(
                    name = course.courseName,
                    startSection = course.startSection,
                    endSection = course.startSection + course.sectionCount - 1
                )
            }

            DayCourseInfo(
                dayOfWeek = dayOfWeek,
                courseCount = courses.size,
                courses = courses
            )
        }

        val weekStart = today.minusDays(todayDayOfWeek.toLong() - 1)
        val weekEnd = weekStart.plusDays(6)
        val dateRangeText = "${weekStart.format(DATE_FORMATTER)} - ${weekEnd.format(DATE_FORMATTER)}"

        return WeekOverviewData(
            weekNumberText = "第${currentWeekNumber}周",
            dateRangeText = dateRangeText,
            todayDayOfWeek = todayDayOfWeek,
            days = days,
            emptyMessage = null
        )
    }
}
