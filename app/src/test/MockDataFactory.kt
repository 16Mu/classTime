package com.wind.ggbond.classtime.util

import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.Schedule
import java.time.LocalDate
import java.time.LocalTime

object MockDataFactory {

    fun createMockCourse(
        name: String = "高等数学",
        classroom: String = "教A101",
        teacher: String = "张三",
        dayOfWeek: Int = 1,
        startSection: Int = 1,
        sectionCount: Int = 2,
        colorIndex: Int = 0,
        weeks: List<Int> = (1..16).toList(),
        scheduleId: Long = 1
    ): Course {
        return Course(
            courseName = name,
            classroom = classroom,
            teacher = teacher,
            dayOfWeek = dayOfWeek,
            startSection = startSection,
            sectionCount = sectionCount,
            color = when (colorIndex) {
                0 -> "#F44336"
                1 -> "#E91E63"
                2 -> "#9C27B0"
                3 -> "#673AB7"
                4 -> "#3F51B5"
                5 -> "#2196F3"
                6 -> "#03A9F4"
                7 -> "#00BCD4"
                8 -> "#009688"
                9 -> "#4CAF50"
                10 -> "#8BC34A"
                11 -> "#CDDC39"
                12 -> "#FFEB3B"
                13 -> "#FFC107"
                14 -> "#FF9800"
                15 -> "#FF5722"
                else -> "#607D8B"
            },
            weeks = weeks,
            scheduleId = scheduleId
        )
    }

    fun createMockSchedule(
        name: String = "测试课表",
        schoolName: String = "重庆电子工程职业学院",
        startDate: LocalDate = LocalDate.of(2024, 9, 2),
        totalWeeks: Int = 20,
        isCurrent: Boolean = true
    ): Schedule {
        return Schedule(
            name = name,
            schoolName = schoolName,
            startDate = startDate,
            endDate = startDate.plusWeeks(totalWeeks.toLong()),
            totalWeeks = totalWeeks,
            isCurrent = isCurrent
        )
    }

    fun createMockClassTimes(): List<ClassTime> {
        return listOf(
            ClassTime(1, LocalTime.of(8, 0), LocalTime.of(8, 45)),
            ClassTime(2, LocalTime.of(8, 55), LocalTime.of(9, 40)),
            ClassTime(3, LocalTime.of(10, 0), LocalTime.of(10, 45)),
            ClassTime(4, LocalTime.of(10, 55), LocalTime.of(11, 40)),
            ClassTime(5, LocalTime.of(14, 0), LocalTime.of(14, 45)),
            ClassTime(6, LocalTime.of(14, 55), LocalTime.of(15, 40)),
            ClassTime(7, LocalTime.of(16, 0), LocalTime.of(16, 45)),
            ClassTime(8, LocalTime.of(16, 55), LocalTime.of(17, 40)),
            ClassTime(9, LocalTime.of(19, 0), LocalTime.of(19, 45)),
            ClassTime(10, LocalTime.of(19, 55), LocalTime.of(20, 40))
        )
    }

    fun createMockCourseList(): List<Course> {
        return listOf(
            createMockCourse(name = "高等数学", dayOfWeek = 1, startSection = 1, sectionCount = 2, colorIndex = 0),
            createMockCourse(name = "大学英语", dayOfWeek = 1, startSection = 3, sectionCount = 2, colorIndex = 1),
            createMockCourse(name = "线性代数", dayOfWeek = 2, startSection = 1, sectionCount = 2, colorIndex = 2),
            createMockCourse(name = "物理实验", dayOfWeek = 3, startSection = 5, sectionCount = 3, colorIndex = 3),
            createMockCourse(name = "思想政治", dayOfWeek = 4, startSection = 1, sectionCount = 2, colorIndex = 4),
            createMockCourse(name = "体育", dayOfWeek = 5, startSection = 5, sectionCount = 2, colorIndex = 5)
        )
    }
}
