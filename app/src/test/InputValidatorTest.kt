package com.wind.ggbond.classtime.util

import io.mockk.mockkStatic
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class InputValidatorTest {

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
    }

    @Test
    fun `sanitizeXSS - should escape HTML special characters`() {
        val input = "<script>alert('xss')</script>"
        val result = InputValidator.sanitizeXSS(input)

        assertFalse("应转义HTML标签", result.contains("<script"))
        assertTrue("应包含转义后的内容", result.contains("&lt;") || result.contains("&gt;"))
    }

    @Test
    fun `sanitizeXSS - should escape ampersand`() {
        val input = "Tom & Jerry"
        val result = InputValidator.sanitizeXSS(input)

        assertTrue("应转义&符号", result.contains("&amp;"))
        assertFalse("不应包含未转义的&", result.contains("Tom & Jerry"))
    }

    @Test
    fun `validateCourseName - should accept valid course name`() {
        assertTrue("有效课程名称应通过验证", InputValidator.validateCourseName("高等数学"))
        assertTrue("带空格的名称应通过验证", InputValidator.validateCourseName("大学英语 (A类)"))
    }

    @Test
    fun `validateCourseName - should reject blank name`() {
        assertFalse("空白课程名称应失败", InputValidator.validateCourseName(""))
        assertFalse("纯空格课程名称应失败", InputValidator.validateCourseName("   "))
    }

    @Test
    fun `validateCourseName - should reject XSS attempts`() {
        assertFalse("包含script标签的名称应失败", 
            InputValidator.validateCourseName("<script>alert(1)</script>"))
    }

    @Test
    fun `validateTeacher - should accept valid teacher name`() {
        assertTrue("有效教师姓名应通过", InputValidator.validateTeacher("张三"))
        assertTrue("英文教师姓名应通过", InputValidator.validateTeacher("John Smith"))
    }

    @Test
    fun `validateTeacher - should reject overly long names`() {
        val longName = "A".repeat(200)
        assertFalse("过长的教师姓名应失败", InputValidator.validateTeacher(longName))
    }

    @Test
    fun `validateClassroom - should accept valid classroom name`() {
        assertTrue("有效教室名称应通过", InputValidator.validateClassroom("教A101"))
        assertTrue("带楼层的教室应通过", InputValidator.validateClassroom("教学楼3-205"))
    }

    @Test
    fun `detectSqlInjection - should detect common SQL injection patterns`() {
        assertTrue("应检测到OR注入", InputValidator.detectSqlInjection("' OR '1'='1"))
        assertTrue("应检测到DROP TABLE", InputValidator.detectSqlInjection("'; DROP TABLE users"))
        assertTrue("应检测到UNION SELECT", InputValidator.detectSqlInjection("' UNION SELECT *"))
    }

    @Test
    fun `detectSqlInjection - should return false for safe input`() {
        assertFalse("安全输入不应触发检测", InputValidator.detectSqlInjection("正常文本内容"))
        assertFalse("教室名称不应触发检测", InputValidator.detectSqlInjection("教A101"))
    }

    @Test
    fun `validateUrl - should accept valid http URLs`() {
        assertTrue("HTTP URL应通过", InputValidator.validateUrl("http://example.com"))
        assertTrue("HTTPS URL应通过", InputValidator.validateUrl("https://example.com/path"))
    }

    @Test
    fun `validateUrl - should reject invalid protocols`() {
        assertFalse("javascript协议应失败", InputValidator.validateUrl("javascript:alert(1)"))
        assertFalse("data协议应失败", InputValidator.validateUrl("data:text/html,<h1>xss</h1>"))
        assertFalse("file协议应失败", InputValidator.validateUrl("file:///etc/passwd"))
        assertFalse("空URL应失败", InputValidator.validateUrl(""))
    }
}
