package com.wind.ggbond.classtime.service.helper

import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.util.MockDataFactory
import io.mockk.mockkStatic
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ImportParserTest {

    private lateinit var parser: ImportParser

    @Before
    fun setUp() {
        parser = ImportParser()
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
    }

    @Test
    fun `extractIcsField - should extract simple field value`() {
        val event = """
            BEGIN:VEVENT
            SUMMARY:高等数学
            LOCATION:教A101
            END:VEVENT
        """.trimIndent()

        assertEquals("高等数学", parser.extractIcsField(event, "SUMMARY"))
        assertEquals("教A101", parser.extractIcsField(event, "LOCATION"))
    }

    @Test
    fun `extractIcsField - should return empty for missing field`() {
        val event = """
            BEGIN:VEVENT
            SUMMARY:测试课程
            END:VEVENT
        """.trimIndent()

        assertEquals("", parser.extractIcsField(event, "NONEXISTENT"))
    }

    @Test
    fun `parseIcsDateTime - should parse valid datetime string`() {
        val result = parser.parseIcsDateTime("20240902T080000")

        assertNotNull("应成功解析有效的日期时间字符串", result)
        if (result != null) {
            assertEquals(2024, result.year)
            assertEquals(9, result.monthValue)
            assertEquals(2, result.dayOfMonth)
            assertEquals(8, result.hour)
            assertEquals(0, result.minute)
        }
    }

    @Test
    fun `parseIcsDateTime - should handle UTC timezone suffix`() {
        val result = parser.parseIcsDateTime("20240902T080000Z")

        assertNotNull("应能处理UTC时间戳", result)
    }

    @Test
    fun `parseIcsDateTime - should return null for invalid format`() {
        val result = parser.parseIcsDateTime("invalid-date")

        assertNull("无效格式应返回null", result)
    }

    @Test
    fun `extractTeacherFromDescription - should extract teacher name`() {
        val description = "教师：张三\\n教室：教A101"

        assertEquals("张三", parser.extractTeacherFromDescription(description))
    }

    @Test
    fun `extractTeacherFromDescription - should support English format`() {
        val description = "Teacher:John Smith\\nRoom:A101"

        assertEquals("John Smith", parser.extractTeacherFromDescription(description))
    }

    @Test
    fun `extractSectionFromDescription - should parse section range`() {
        val description = "节次：1-2节\\n学分：4"

        val result = parser.extractSectionFromDescription(description)

        assertNotNull("应解析出节次信息", result)
        if (result != null) {
            assertEquals(1, result.first)
            assertEquals(2, result.second)
        }
    }

    @Test
    fun `extractWeekFromDescription - should parse week range`() {
        val description = "周次：1-16周"

        val weeks = parser.extractWeekFromDescription(description)

        assertNotNull("应解析出周次列表", weeks)
        if (weeks != null) {
            assertEquals(16, weeks.size)
            assertEquals(1, weeks.first())
            assertEquals(16, weeks.last())
        }
    }

    @Test
    fun `extractWeekFromDescription - should handle odd weeks only`() {
        val description = "周次：1-16周(单)"

        val weeks = parser.extractWeekFromDescription(description)

        assertNotNull("应处理单双周标记", weeks)
        if (weeks != null) {
            assertTrue("所有周次应为奇数", weeks.all { it % 2 == 1 })
        }
    }

    @Test
    fun `matchSectionByTime - should match correct section by time`() {
        val classTimes = MockDataFactory.createMockClassTimes()
        val startTime = LocalTime.of(8, 0)
        val endTime = LocalTime.of(8, 45)

        val (startSection, sectionCount) = parser.matchSectionByTime(startTime, endTime, classTimes)

        assertEquals("8:00开始应匹配第1节", 1, startSection)
        assertTrue("节数应至少为1", sectionCount >= 1)
    }

    @Test
    fun `matchSectionByTime - should return default when class times empty`() {
        val (startSection, sectionCount) = parser.matchSectionByTime(
            LocalTime.of(10, 0),
            LocalTime.of(10, 45),
            emptyList()
        )

        assertEquals("空时间表应默认第1节", 1, startSection)
        assertEquals("空时间表应默认2节课", 2, sectionCount)
    }

    @Test
    fun `formatWeekExpression - should format consecutive weeks as range`() {
        val weeks = listOf(1, 2, 3, 4, 5, 6, 7, 8)

        val expression = parser.formatWeekExpression(weeks)

        assertTrue("连续周次应格式化为范围", expression.contains("1-8"))
        assertTrue("应以'周'结尾", expression.endsWith("周"))
    }

    @Test
    fun `formatWeekExpression - should handle non-consecutive weeks`() {
        val weeks = listOf(1, 2, 3, 5, 6, 7, 9, 10)

        val expression = parser.formatWeekExpression(weeks)

        assertTrue("不连续周次应包含多个范围", expression.contains(",") || expression.contains("-"))
    }

    @Test
    fun `parseDayOfWeek - should parse Chinese day names`() {
        assertEquals(1, parser.parseDayOfWeek("周一"))
        assertEquals(2, parser.parseDayOfWeek("星期二"))
        assertEquals(3, parser.parseDayOfWeek("Wednesday"))
        assertEquals(5, parser.parseDayOfWeek("周五"))
        assertEquals(7, parser.parseDayOfWeek("周日"))
    }

    @Test
    fun `parseDayOfWeek - should fallback to numeric parsing`() {
        assertEquals(3, parser.parseDayOfWeek("3"))
        assertEquals(1, parser.parseDayOfWeek("invalid"))
    }

    @Test
    fun `parseCsvLine - should correctly parse quoted CSV fields`() {
        val line = "\"高等数学\",\"张三\",\"教A101\",\"周一\",\"第1-2节\""

        val fields = parser.parseCsvLine(line)

        assertEquals("应解析出5个字段", 5, fields.size)
        assertEquals("高等数学", fields[0])
        assertEquals("张三", fields[1])
        assertEquals("教A101", fields[2])
    }

    @Test
    fun `parseCsvLine - should handle commas within quotes`() {
        val line = "\"课程A, B\",\"教师X\""

        val fields = parser.parseCsvLine(line)

        assertEquals(2, fields.size)
        assertEquals("课程A, B", fields[0])
    }
}
