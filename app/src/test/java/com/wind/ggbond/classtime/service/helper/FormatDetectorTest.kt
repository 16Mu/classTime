package com.wind.ggbond.classtime.service.helper

import com.wind.ggbond.classtime.data.local.entity.Course
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FormatDetectorTest {

    private lateinit var detector: FormatDetector

    @Before
    fun setUp() {
        detector = FormatDetector()
    }

    @Test
    fun `detectFormat - should detect JSON format by content`() {
        val content = """{"meta":{"version":"2.0"},"courses":[]}"""
        val result = detector.detectFormat(content)
        assertEquals(FormatDetector.DetectedFormat.JSON, result.format)
        assertTrue(result.confidence > 0.5f)
    }

    @Test
    fun `detectFormat - should detect JSON array format`() {
        val content = """[{"courseName":"数学","dayOfWeek":1}]"""
        val result = detector.detectFormat(content)
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
        val result = detector.detectFormat(content)
        assertEquals(FormatDetector.DetectedFormat.ICS, result.format)
    }

    @Test
    fun `detectFormat - should detect CSV format`() {
        val content = """"# 课程表导出数据"
"# 应用版本","v1.2.1"
序号,课程名称,教师,教室,星期,节次
1,"高等数学","张三","教A101","周一","第1-2节"
"""
        val result = detector.detectFormat(content)
        assertEquals(FormatDetector.DetectedFormat.CSV, result.format)
    }

    @Test
    fun `detectFormat - should detect HTML format`() {
        val content = """<!DOCTYPE html><html><head><title>课程表</title></head><body></body></html>"""
        val result = detector.detectFormat(content)
        assertEquals(FormatDetector.DetectedFormat.HTML, result.format)
    }

    @Test
    fun `detectFormat - should detect by file extension`() {
        val content = "some content"
        val result = detector.detectFormat(content, "schedule.json")
        assertEquals(FormatDetector.DetectedFormat.JSON, result.format)
    }

    @Test
    fun `detectFormat - should detect external JSON source`() {
        val content = """[{"name":"数学","day":1}]"""
        val result = detector.detectFormat(content)
        assertTrue(result.isExternalSource)
    }

    @Test
    fun `detectFormat - should detect app export JSON`() {
        val content = """{"meta":{"format":"CourseScheduleExport"},"courses":[]}"""
        val result = detector.detectFormat(content)
        assertFalse(result.isExternalSource)
    }

    @Test
    fun `detectFormat - should extract JSON version`() {
        val content = """{"meta":{"exportVersion":"3.0"},"courses":[]}"""
        val result = detector.detectFormat(content)
        assertEquals("3.0", result.version)
    }

    @Test
    fun `detectFormat - should detect external ICS source`() {
        val content = """BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//Google Inc//Google Calendar 70.9054//EN
BEGIN:VEVENT
SUMMARY:Meeting
END:VEVENT
END:VCALENDAR"""
        val result = detector.detectFormat(content)
        assertTrue(result.isExternalSource)
    }

    @Test
    fun `detectFormat - should return UNKNOWN for unrecognized content`() {
        val content = "This is just plain text with no structure"
        val result = detector.detectFormat(content)
        assertEquals(FormatDetector.DetectedFormat.UNKNOWN, result.format)
    }
}
