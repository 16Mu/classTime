package com.wind.ggbond.classtime.service.helper

import com.wind.ggbond.classtime.util.MockDataFactory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

class ExportFormatterTest {

    private lateinit var formatter: ExportFormatter

    @Before
    fun setUp() {
        formatter = ExportFormatter()
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
        assertEquals("未知", formatter.getDayName(-1))
        assertEquals("未知", formatter.getDayName(100))
    }

    @Test
    fun `formatIcsDateTime - should format datetime correctly`() {
        val dateTime = LocalDateTime.of(2024, 9, 2, 8, 0, 0)
        val result = formatter.formatIcsDateTime(dateTime)

        assertTrue("ICS日期时间应以T分隔日期和时间", result.contains("T"))
        assertTrue("ICS日期时间应以Z结尾（UTC）", result.endsWith("Z"))
        assertTrue("ICS格式长度应正确", result.length >= 15)
    }

    @Test
    fun `getCurrentDateString - should return valid date string`() {
        val result = formatter.getCurrentDateString()

        assertTrue("当前日期字符串应包含下划线", result.contains("_"))
        assertTrue("当前日期字符串长度应合理", result.length >= 13)
    }

    @Test
    fun `buildIcsContent - should generate valid ICS format`() {
        val courses = MockDataFactory.createMockCourseList()
        val schedule = MockDataFactory.createMockSchedule()
        val classTimes = MockDataFactory.createMockClassTimes()

        val icsContent = formatter.buildIcsContent(courses, schedule, classTimes)

        assertTrue("ICS内容应以BEGIN:VCALENDAR开头", icsContent.startsWith("BEGIN:VCALENDAR"))
        assertTrue("ICS内容应以END:VCALENDAR结尾", icsContent.trimEnd().endsWith("END:VCALENDAR"))
        assertTrue("ICS应包含VERSION:2.0", icsContent.contains("VERSION:2.0"))
        assertTrue("ICS应包含课程名称", icsContent.contains("高等数学"))
    }

    @Test
    fun `buildCsvContent - should generate CSV with BOM and headers`() {
        val courses = MockDataFactory.createMockCourseList()
        val schedule = MockDataFactory.createMockSchedule()
        val classTimes = MockDataFactory.createMockClassTimes()

        val csvContent = formatter.buildCsvContent(courses, schedule, classTimes)

        assertTrue("CSV应以BOM开头", csvContent.startsWith("\uFEFF"))
        assertTrue("CSV应包含表头", csvContent.contains("序号"))
        assertTrue("CSV应包含课程数据", csvContent.contains("高等数学"))
    }
}
