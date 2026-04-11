package com.wind.ggbond.classtime.service.helper

import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.Schedule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ExportFormatterTest {

    private lateinit var formatter: ExportFormatter
    private lateinit var mockCourses: List<Course>
    private lateinit var mockSchedule: Schedule
    private lateinit var mockClassTimes: List<ClassTime>

    @Before
    fun setUp() {
        formatter = ExportFormatter()
        mockSchedule = Schedule(
            name = "测试课表",
            schoolName = "测试大学",
            startDate = LocalDate.of(2024, 9, 2),
            endDate = LocalDate.of(2025, 1, 15),
            totalWeeks = 20,
            isCurrent = true
        )
        mockClassTimes = listOf(
            ClassTime(sectionNumber = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(8, 45)),
            ClassTime(sectionNumber = 2, startTime = LocalTime.of(8, 55), endTime = LocalTime.of(9, 40)),
            ClassTime(sectionNumber = 3, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(10, 45)),
            ClassTime(sectionNumber = 4, startTime = LocalTime.of(10, 55), endTime = LocalTime.of(11, 40)),
            ClassTime(sectionNumber = 5, startTime = LocalTime.of(14, 0), endTime = LocalTime.of(14, 45))
        )
        mockCourses = listOf(
            Course(courseName = "高等数学", teacher = "张三", classroom = "教A101",
                dayOfWeek = 1, startSection = 1, sectionCount = 2, color = "#F44336",
                weeks = (1..16).toList(), weekExpression = "1-16周", credit = 4f),
            Course(courseName = "大学英语", teacher = "李四", classroom = "教B201",
                dayOfWeek = 2, startSection = 3, sectionCount = 2, color = "#E91E63",
                weeks = (1..16).toList(), weekExpression = "1-16周", credit = 3f)
        )
    }

    @Test
    fun `getDayName - should return correct Chinese day names`() {
        assertEquals("周一", formatter.getDayName(1))
        assertEquals("周二", formatter.getDayName(2))
        assertEquals("周三", formatter.getDayName(3))
        assertEquals("周四", formatter.getDayName(4))
        assertEquals("周五", formatter.getDayName(5))
        assertEquals("周六", formatter.getDayName(6))
        assertEquals("周日", formatter.getDayName(7))
    }

    @Test
    fun `getDayName - should return unknown for invalid input`() {
        assertEquals("未知", formatter.getDayName(0))
        assertEquals("未知", formatter.getDayName(8))
    }

    @Test
    fun `buildIcsContent - should generate valid ICS format`() {
        val icsContent = formatter.buildIcsContent(mockCourses, mockSchedule, mockClassTimes)
        assertTrue(icsContent.startsWith("BEGIN:VCALENDAR"))
        assertTrue(icsContent.trimEnd().endsWith("END:VCALENDAR"))
        assertTrue(icsContent.contains("VERSION:2.0"))
        assertTrue(icsContent.contains("高等数学"))
    }

    @Test
    fun `buildIcsContent - should include export version metadata`() {
        val icsContent = formatter.buildIcsContent(mockCourses, mockSchedule, mockClassTimes)
        assertTrue(icsContent.contains("X-EXPORT-VERSION:"))
        assertTrue(icsContent.contains("X-APP-VERSION:"))
        assertTrue(icsContent.contains("X-EXPORT-TIME:"))
    }

    @Test
    fun `buildCsvContent - should generate CSV with BOM and headers`() {
        val csvContent = formatter.buildCsvContent(mockCourses, mockSchedule, mockClassTimes)
        assertTrue(csvContent.startsWith("\uFEFF"))
        assertTrue(csvContent.contains("序号"))
        assertTrue(csvContent.contains("高等数学"))
    }

    @Test
    fun `buildCsvContent - should include version metadata`() {
        val csvContent = formatter.buildCsvContent(mockCourses, mockSchedule, mockClassTimes)
        assertTrue(csvContent.contains("应用版本"))
        assertTrue(csvContent.contains("导出格式版本"))
        assertTrue(csvContent.contains("导出时间"))
    }

    @Test
    fun `buildHtmlContent - should include version metadata in meta tags`() {
        val htmlContent = formatter.buildHtmlContent(mockCourses, mockSchedule, mockClassTimes)
        assertTrue(htmlContent.contains("name='generator'"))
        assertTrue(htmlContent.contains("name='export-version'"))
        assertTrue(htmlContent.contains("name='export-time'"))
    }

    @Test
    fun `buildHtmlContent - should include version in footer`() {
        val htmlContent = formatter.buildHtmlContent(mockCourses, mockSchedule, mockClassTimes)
        assertTrue(htmlContent.contains("应用版本"))
        assertTrue(htmlContent.contains("导出格式版本"))
    }

    @Test
    fun `buildTextContent - should include version info`() {
        val textContent = formatter.buildTextContent(mockCourses, mockSchedule, mockClassTimes)
        assertTrue(textContent.contains("应用版本"))
        assertTrue(textContent.contains("格式版本"))
    }
}
