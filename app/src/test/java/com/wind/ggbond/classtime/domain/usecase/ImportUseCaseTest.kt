package com.wind.ggbond.classtime.domain.usecase

import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.service.helper.FormatDetector
import com.wind.ggbond.classtime.service.helper.ImportParser
import com.wind.ggbond.classtime.service.helper.ImportValidator
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ImportUseCaseTest {

    private lateinit var importUseCase: ImportUseCase
    private val formatDetector: FormatDetector = FormatDetector()
    private val importParser: ImportParser = mockk(relaxed = true)
    private val importValidator: ImportValidator = ImportValidator()

    @Before
    fun setUp() {
        importUseCase = ImportUseCase(formatDetector, importParser, importValidator)
    }

    @Test
    fun `detectFormat - should detect JSON format`() {
        val content = """{"courses":[]}"""
        val result = importUseCase.detectFormat(content)
        assertEquals(FormatDetector.DetectedFormat.JSON, result.format)
    }

    @Test
    fun `detectFormat - should detect ICS format`() {
        val content = """BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VEVENT
SUMMARY:高等数学
END:VEVENT
END:VCALENDAR"""
        val result = importUseCase.detectFormat(content)
        assertEquals(FormatDetector.DetectedFormat.ICS, result.format)
    }

    @Test
    fun `detectFormat - should detect CSV format`() {
        val content = """"# 课程表导出数据"
序号,课程名称,教师,教室,星期,节次
1,"高等数学","张三","教A101","周一","第1-2节"
"""
        val result = importUseCase.detectFormat(content)
        assertEquals(FormatDetector.DetectedFormat.CSV, result.format)
    }

    @Test
    fun `detectFormat - should detect HTML format`() {
        val content = """<!DOCTYPE html><html><head><title>课程表</title></head><body></body></html>"""
        val result = importUseCase.detectFormat(content)
        assertEquals(FormatDetector.DetectedFormat.HTML, result.format)
    }

    @Test
    fun `detectFormat - should detect EXCEL by extension`() {
        val content = "binary content"
        val result = importUseCase.detectFormat(content, "schedule.xlsx")
        assertEquals(FormatDetector.DetectedFormat.EXCEL, result.format)
    }

    @Test
    fun `detectFormat - should return UNKNOWN for unrecognized content`() {
        val content = "This is just plain text"
        val result = importUseCase.detectFormat(content)
        assertEquals(FormatDetector.DetectedFormat.UNKNOWN, result.format)
    }

    @Test
    fun `detectFormat - should detect JSON by file extension`() {
        val content = "some content"
        val result = importUseCase.detectFormat(content, "schedule.json")
        assertEquals(FormatDetector.DetectedFormat.JSON, result.format)
    }

    @Test
    fun `parseJsonToParsedCourses - should parse complete export format`() {
        val json = """{
            "exportTime": "2026-01-01",
            "version": "1.0",
            "courses": [
                {
                    "courseName": "高等数学",
                    "teacher": "张老师",
                    "classroom": "教A101",
                    "dayOfWeek": 1,
                    "startSection": 1,
                    "sectionCount": 2,
                    "weekExpression": "1-16周",
                    "weeks": [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16],
                    "credit": 4.0,
                    "courseCode": "MATH101"
                }
            ]
        }"""
        val result = importUseCase.parseJsonToParsedCourses(json)
        assertEquals(1, result.courses.size)
        assertEquals("高等数学", result.courses[0].courseName)
        assertEquals(1, result.courses[0].dayOfWeek)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `parseJsonToParsedCourses - should parse simplified format`() {
        val json = """[
            {
                "courseName": "线性代数",
                "teacher": "李老师",
                "classroom": "教B201",
                "dayOfWeek": 2,
                "startSection": 3,
                "sectionCount": 2,
                "weekExpression": "1-12周",
                "weeks": [1,2,3,4,5,6,7,8,9,10,11,12]
            }
        ]"""
        val result = importUseCase.parseJsonToParsedCourses(json)
        assertEquals(1, result.courses.size)
        assertEquals("线性代数", result.courses[0].courseName)
    }

    @Test
    fun `parseJsonToParsedCourses - should filter invalid courses`() {
        val json = """[
            {
                "courseName": "有效课程",
                "dayOfWeek": 1,
                "startSection": 1,
                "sectionCount": 2,
                "weeks": [1,2,3]
            },
            {
                "courseName": "",
                "dayOfWeek": 1,
                "startSection": 1,
                "sectionCount": 2,
                "weeks": [1,2,3]
            },
            {
                "courseName": "无效星期",
                "dayOfWeek": 8,
                "startSection": 1,
                "sectionCount": 2,
                "weeks": [1,2,3]
            },
            {
                "courseName": "空周次",
                "dayOfWeek": 1,
                "startSection": 1,
                "sectionCount": 2,
                "weeks": []
            }
        ]"""
        val result = importUseCase.parseJsonToParsedCourses(json)
        assertEquals(1, result.courses.size)
        assertEquals("有效课程", result.courses[0].courseName)
    }

    @Test
    fun `parseJsonToParsedCourses - should return empty list for invalid JSON`() {
        val json = "not a valid json"
        val result = importUseCase.parseJsonToParsedCourses(json)
        assertTrue(result.courses.isEmpty())
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `parseJsonToParsedCourses - should return empty list for empty JSON`() {
        val json = """[]"""
        val result = importUseCase.parseJsonToParsedCourses(json)
        assertTrue(result.courses.isEmpty())
    }

    @Test
    fun `convertParsedCoursesToCourses - should convert with color assignment`() {
        val parsedCourses = listOf(
            ParsedCourse(
                courseName = "高等数学",
                teacher = "张老师",
                classroom = "教A101",
                dayOfWeek = 1,
                startSection = 1,
                sectionCount = 2,
                weekExpression = "1-16周",
                weeks = (1..16).toList()
            )
        )
        val result = importUseCase.convertParsedCoursesToCourses(parsedCourses, scheduleId = 1)
        assertEquals(1, result.courses.size)
        assertEquals("高等数学", result.courses[0].courseName)
        assertEquals(1L, result.courses[0].scheduleId)
        assertTrue(result.courses[0].color.isNotEmpty())
    }

    @Test
    fun `convertParsedCoursesToCourses - should resolve weeks from expression when weeks empty`() {
        every { importParser.parseWeeks("1-8周") } returns (1..8).toList()

        val parsedCourses = listOf(
            ParsedCourse(
                courseName = "英语",
                dayOfWeek = 3,
                startSection = 5,
                sectionCount = 2,
                weekExpression = "1-8周",
                weeks = emptyList()
            )
        )
        val result = importUseCase.convertParsedCoursesToCourses(parsedCourses, scheduleId = 1)
        assertEquals(1, result.courses.size)
    }

    @Test
    fun `convertParsedCoursesToCourses - should skip invalid courses`() {
        val parsedCourses = listOf(
            ParsedCourse(
                courseName = "",
                dayOfWeek = 1,
                startSection = 1,
                sectionCount = 2,
                weeks = (1..16).toList()
            )
        )
        val result = importUseCase.convertParsedCoursesToCourses(parsedCourses, scheduleId = 1)
        assertEquals(0, result.validCount)
        assertTrue(result.skippedCount > 0)
    }

    @Test
    fun `convertParsedCoursesToCourses - should handle multiple courses with distinct colors`() {
        val parsedCourses = listOf(
            ParsedCourse(
                courseName = "高等数学",
                dayOfWeek = 1,
                startSection = 1,
                sectionCount = 2,
                weeks = (1..16).toList()
            ),
            ParsedCourse(
                courseName = "线性代数",
                dayOfWeek = 2,
                startSection = 3,
                sectionCount = 2,
                weeks = (1..16).toList()
            )
        )
        val result = importUseCase.convertParsedCoursesToCourses(parsedCourses, scheduleId = 1)
        assertEquals(2, result.courses.size)
        assertNotEquals(result.courses[0].color, result.courses[1].color)
    }

    @Test
    fun `resolveWeeksForCourse - should return existing weeks when not empty`() {
        val parsed = ParsedCourse(
            courseName = "测试",
            dayOfWeek = 1,
            startSection = 1,
            sectionCount = 2,
            weeks = listOf(1, 2, 3)
        )
        val (weeks, weekExpr) = importUseCase.resolveWeeksForCourse(parsed)
        assertEquals(listOf(1, 2, 3), weeks)
        assertTrue(weekExpr.isNotEmpty())
    }

    @Test
    fun `resolveWeeksForCourse - should parse from expression when weeks empty`() {
        every { importParser.parseWeeks("1-8周") } returns (1..8).toList()

        val parsed = ParsedCourse(
            courseName = "测试",
            dayOfWeek = 1,
            startSection = 1,
            sectionCount = 2,
            weekExpression = "1-8周",
            weeks = emptyList()
        )
        val (weeks, _) = importUseCase.resolveWeeksForCourse(parsed)
        assertEquals((1..8).toList(), weeks)
    }

    @Test
    fun `resolveWeeksForCourse - should default to 1-16 when both empty`() {
        every { importParser.parseWeeks(any()) } returns emptyList()

        val parsed = ParsedCourse(
            courseName = "测试",
            dayOfWeek = 1,
            startSection = 1,
            sectionCount = 2,
            weekExpression = "",
            weeks = emptyList()
        )
        val (weeks, _) = importUseCase.resolveWeeksForCourse(parsed)
        assertEquals((1..16).toList(), weeks)
    }

    @Test
    fun `parseWeekExpression - should parse valid expression`() {
        val result = importUseCase.parseWeekExpression("1-16周")
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `parseWeekExpression - should return 1-16 for empty expression`() {
        val result = importUseCase.parseWeekExpression("")
        assertEquals((1..16).toList(), result)
    }
}
