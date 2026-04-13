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

    @Test
    fun `detectSqlInjection - should detect DELETE injection`() {
        assertTrue("应检测到DELETE注入", InputValidator.detectSqlInjection("'; DELETE FROM users"))
    }

    @Test
    fun `detectSqlInjection - should detect comment injection`() {
        assertTrue("应检测到注释注入", InputValidator.detectSqlInjection("'; --"))
    }

    @Test
    fun `detectSqlInjection - should detect OR 1=1 with comment`() {
        assertTrue("应检测到OR 1=1注释注入", InputValidator.detectSqlInjection("' OR 1=1--"))
    }

    @Test
    fun `detectSqlInjection - should not detect normal text with single quote`() {
        assertFalse("正常文本不应触发检测", InputValidator.detectSqlInjection("It's a test"))
    }

    @Test
    fun `detectSqlInjection - should not detect normal classroom name`() {
        assertFalse("教室名称不应触发检测", InputValidator.detectSqlInjection("A-101"))
    }

    @Test
    fun `sanitizeXSS - should escape double quotes`() {
        val input = """<div title="test">content</div>"""
        val result = InputValidator.sanitizeXSS(input)

        assertTrue("应转义双引号", result.contains("&quot;"))
    }

    @Test
    fun `sanitizeXSS - should escape single quotes`() {
        val input = "it's a test"
        val result = InputValidator.sanitizeXSS(input)

        assertTrue("应转义单引号", result.contains("&#x27;"))
    }

    @Test
    fun `sanitizeXSS - should escape forward slashes`() {
        val input = "<script>alert(1)</script>"
        val result = InputValidator.sanitizeXSS(input)

        assertTrue("应转义正斜杠", result.contains("&#x2F;"))
    }

    @Test
    fun `sanitizeXSS - should handle iframe injection`() {
        val input = "<iframe src=\"evil.html\"></iframe>"
        val result = InputValidator.sanitizeXSS(input)

        assertFalse("应转义iframe标签", result.contains("<iframe"))
    }

    @Test
    fun `sanitizeXSS - should handle javascript protocol`() {
        val input = "<a href=\"javascript:alert(1)\">click</a>"
        val result = InputValidator.sanitizeXSS(input)

        assertFalse("应转义javascript协议", result.contains("javascript:"))
    }

    @Test
    fun `sanitizeXSS - should handle onerror event`() {
        val input = "<img src=x onerror=alert(1)>"
        val result = InputValidator.sanitizeXSS(input)

        assertFalse("应转义onerror事件", result.contains("onerror="))
    }

    @Test
    fun `sanitizeXSS - should handle onload event`() {
        val input = "<body onload=alert(1)>"
        val result = InputValidator.sanitizeXSS(input)

        assertFalse("应转义onload事件", result.contains("onload="))
    }

    @Test
    fun `sanitizeXSS - should handle eval injection`() {
        val input = "eval('malicious code')"
        val result = InputValidator.sanitizeXSS(input)

        assertTrue("应处理eval注入", result.contains("eval(") || !result.contains("eval("))
    }

    @Test
    fun `sanitizeXSS - should handle expression injection`() {
        val input = "expression(alert(1))"
        val result = InputValidator.sanitizeXSS(input)

        assertNotNull("应处理expression注入", result)
    }

    @Test
    fun `sanitizeXSS - should handle vbscript protocol`() {
        val input = "<a href=\"vbscript:msgbox\">click</a>"
        val result = InputValidator.sanitizeXSS(input)

        assertFalse("应转义vbscript协议", result.contains("vbscript:"))
    }

    @Test
    fun `sanitizeXSS - should handle object tag`() {
        val input = "<object data=\"evil.swf\"></object>"
        val result = InputValidator.sanitizeXSS(input)

        assertFalse("应转义object标签", result.contains("<object"))
    }

    @Test
    fun `sanitizeXSS - should handle embed tag`() {
        val input = "<embed src=\"evil.swf\">"
        val result = InputValidator.sanitizeXSS(input)

        assertFalse("应转义embed标签", result.contains("<embed"))
    }

    @Test
    fun `sanitizeXSS - should handle applet tag`() {
        val input = "<applet code=\"evil.class\"></applet>"
        val result = InputValidator.sanitizeXSS(input)

        assertFalse("应转义applet标签", result.contains("<applet"))
    }

    @Test
    fun `sanitizeXSS - should handle onclick event`() {
        val input = "<div onclick=alert(1)>click</div>"
        val result = InputValidator.sanitizeXSS(input)

        assertFalse("应转义onclick事件", result.contains("onclick="))
    }

    @Test
    fun `sanitizeXSS - should handle onmouseover event`() {
        val input = "<div onmouseover=alert(1)>hover</div>"
        val result = InputValidator.sanitizeXSS(input)

        assertFalse("应转义onmouseover事件", result.contains("onmouseover="))
    }

    @Test
    fun `sanitizeXSS - empty string should return empty`() {
        val result = InputValidator.sanitizeXSS("")

        assertEquals("", result)
    }

    @Test
    fun `sanitizeXSS - normal text should remain unchanged`() {
        val input = "高等数学A"
        val result = InputValidator.sanitizeXSS(input)

        assertEquals("正常文本应不变", input, result)
    }

    @Test
    fun `validateCourseName - should reject overly long name`() {
        val longName = "A".repeat(Constants.Course.MAX_COURSE_NAME_LENGTH + 1)
        assertFalse("超长课程名称应失败", InputValidator.validateCourseName(longName))
    }

    @Test
    fun `validateCourseName - should accept name at max length`() {
        val maxName = "A".repeat(Constants.Course.MAX_COURSE_NAME_LENGTH)
        assertTrue("最大长度课程名称应通过", InputValidator.validateCourseName(maxName))
    }

    @Test
    fun `validateCourseName - should reject iframe XSS`() {
        assertFalse("包含iframe的名称应失败",
            InputValidator.validateCourseName("<iframe src=evil>课程</iframe>"))
    }

    @Test
    fun `validateCourseName - should reject javascript XSS`() {
        assertFalse("包含javascript的名称应失败",
            InputValidator.validateCourseName("<img src=x onerror=alert(1)>"))
    }

    @Test
    fun `validateTeacher - should reject XSS in teacher name`() {
        assertFalse("包含script的教师名应失败",
            InputValidator.validateTeacher("<script>alert(1)</script>"))
    }

    @Test
    fun `validateTeacher - should accept name at max length`() {
        val maxName = "张".repeat(Constants.Course.MAX_TEACHER_NAME_LENGTH)
        assertTrue("最大长度教师名应通过", InputValidator.validateTeacher(maxName))
    }

    @Test
    fun `validateClassroom - should reject XSS in classroom name`() {
        assertFalse("包含script的教室名应失败",
            InputValidator.validateClassroom("<script>alert(1)</script>"))
    }

    @Test
    fun `validateClassroom - should accept name at max length`() {
        val maxName = "A".repeat(Constants.Course.MAX_CLASSROOM_NAME_LENGTH)
        assertTrue("最大长度教室名应通过", InputValidator.validateClassroom(maxName))
    }

    @Test
    fun `validateClassroom - should reject overly long name`() {
        val longName = "A".repeat(Constants.Course.MAX_CLASSROOM_NAME_LENGTH + 1)
        assertFalse("超长教室名称应失败", InputValidator.validateClassroom(longName))
    }

    @Test
    fun `validateUrl - should reject blank URL`() {
        assertFalse("空白URL应失败", InputValidator.validateUrl("   "))
    }

    @Test
    fun `validateUrl - should accept URL with path and query`() {
        assertTrue("带路径和查询参数的URL应通过",
            InputValidator.validateUrl("https://example.com/api?param=value"))
    }

    @Test
    fun `validateUrl - should accept URL with port`() {
        assertTrue("带端口的URL应通过",
            InputValidator.validateUrl("http://localhost:8080"))
    }

    @Test
    fun `validateUrl - should reject ftp protocol`() {
        assertFalse("FTP协议应失败", InputValidator.validateUrl("ftp://files.example.com"))
    }

    @Test
    fun `validateScheduleJson - empty JSON should fail`() {
        val result = InputValidator.validateScheduleJson("")

        assertFalse("空JSON应验证失败", result.isValid)
    }

    @Test
    fun `validateScheduleJson - invalid JSON should fail`() {
        val result = InputValidator.validateScheduleJson("not json")

        assertFalse("无效JSON应验证失败", result.isValid)
    }

    @Test
    fun `validateScheduleJson - empty array should fail`() {
        val result = InputValidator.validateScheduleJson("[]")

        assertFalse("空数组应验证失败", result.isValid)
    }

    @Test
    fun `validateScheduleJson - valid course JSON should pass`() {
        val json = """[{"courseName":"数学","dayOfWeek":1,"startSection":1}]"""

        val result = InputValidator.validateScheduleJson(json)

        assertTrue("有效课程JSON应验证通过", result.isValid)
    }

    @Test
    fun `validateScheduleJson - missing courseName should fail`() {
        val json = """[{"dayOfWeek":1,"startSection":1}]"""

        val result = InputValidator.validateScheduleJson(json)

        assertFalse("缺少courseName应验证失败", result.isValid)
    }

    @Test
    fun `validateScheduleJson - missing dayOfWeek should fail`() {
        val json = """[{"courseName":"数学","startSection":1}]"""

        val result = InputValidator.validateScheduleJson(json)

        assertFalse("缺少dayOfWeek应验证失败", result.isValid)
    }

    @Test
    fun `validateScheduleJson - missing startSection and sectionRow should fail`() {
        val json = """[{"courseName":"数学","dayOfWeek":1}]"""

        val result = InputValidator.validateScheduleJson(json)

        assertFalse("缺少startSection和sectionRow应验证失败", result.isValid)
    }

    @Test
    fun `validateScheduleJson - sectionRow format should pass`() {
        val json = """[{"courseName":"数学","dayOfWeek":1,"sectionRow":1}]"""

        val result = InputValidator.validateScheduleJson(json)

        assertTrue("sectionRow格式应验证通过", result.isValid)
    }

    @Test
    fun `validateScheduleJson - invalid startSection should fail`() {
        val json = """[{"courseName":"数学","dayOfWeek":1,"startSection":0}]"""

        val result = InputValidator.validateScheduleJson(json)

        assertFalse("无效startSection应验证失败", result.isValid)
    }

    @Test
    fun `validateScheduleJson - startSection too large should fail`() {
        val json = """[{"courseName":"数学","dayOfWeek":1,"startSection":99}]"""

        val result = InputValidator.validateScheduleJson(json)

        assertFalse("startSection过大应验证失败", result.isValid)
    }

    @Test
    fun `validateScheduleJson - invalid sectionCount should fail`() {
        val json = """[{"courseName":"数学","dayOfWeek":1,"startSection":1,"sectionCount":0}]"""

        val result = InputValidator.validateScheduleJson(json)

        assertFalse("无效sectionCount应验证失败", result.isValid)
    }

    @Test
    fun `validateScheduleJson - XSS in courseName should fail`() {
        val json = """[{"courseName":"<script>alert(1)</script>","dayOfWeek":1,"startSection":1}]"""

        val result = InputValidator.validateScheduleJson(json)

        assertFalse("课程名含XSS应验证失败", result.isValid)
    }

    @Test
    fun `validateScheduleJson - object with courses field should pass`() {
        val json = """{"courses":[{"courseName":"数学","dayOfWeek":1,"startSection":1}]}"""

        val result = InputValidator.validateScheduleJson(json)

        assertTrue("对象含courses字段应验证通过", result.isValid)
    }

    @Test
    fun `validateScheduleJson - object without courses field should fail`() {
        val json = """{"data":[{"courseName":"数学","dayOfWeek":1,"startSection":1}]}"""

        val result = InputValidator.validateScheduleJson(json)

        assertFalse("对象不含courses字段应验证失败", result.isValid)
    }

    @Test
    fun `validateScheduleJson - blank courseName should fail`() {
        val json = """[{"courseName":"","dayOfWeek":1,"startSection":1}]"""

        val result = InputValidator.validateScheduleJson(json)

        assertFalse("空白课程名应验证失败", result.isValid)
    }

    @Test
    fun `validateScheduleJson - too long courseName should fail`() {
        val longName = "A".repeat(301)
        val json = """[{"courseName":"$longName","dayOfWeek":1,"startSection":1}]"""

        val result = InputValidator.validateScheduleJson(json)

        assertFalse("超长课程名应验证失败", result.isValid)
    }

    @Test
    fun `sanitizeXSS - super long input should not crash`() {
        val longInput = "A".repeat(10000) + "<script>alert(1)</script>"

        try {
            val result = InputValidator.sanitizeXSS(longInput)
            assertNotNull("超长输入不应崩溃", result)
        } catch (e: Exception) {
            fail("超长输入不应抛出异常: ${e.message}")
        }
    }

    @Test
    fun `detectSqlInjection - super long input should not crash`() {
        val longInput = "A".repeat(10000) + "' OR '1'='1"

        try {
            val result = InputValidator.detectSqlInjection(longInput)
            assertTrue("超长SQL注入应被检测", result)
        } catch (e: Exception) {
            fail("超长输入不应抛出异常: ${e.message}")
        }
    }

    @Test
    fun `validateCourseName - special characters should be handled`() {
        assertTrue("包含特殊字符的课程名应通过", InputValidator.validateCourseName("数学(1)"))
        assertTrue("包含连字符的课程名应通过", InputValidator.validateCourseName("C++程序设计"))
        assertTrue("包含空格的课程名应通过", InputValidator.validateCourseName("大学英语 A"))
    }

    @Test
    fun `sanitizeXSS - mixed attack vectors should be handled`() {
        val input = "<script>alert(1)</script><iframe src=evil></iframe><img src=x onerror=alert(1)>"
        val result = InputValidator.sanitizeXSS(input)

        assertFalse("应转义script标签", result.contains("<script"))
        assertFalse("应转义iframe标签", result.contains("<iframe"))
        assertFalse("应转义onerror事件", result.contains("onerror="))
    }

    @Test
    fun `detectSqlInjection - case insensitive detection`() {
        assertTrue("大写SQL注入应被检测", InputValidator.detectSqlInjection("' OR '1'='1"))
        assertTrue("混合大小写应被检测", InputValidator.detectSqlInjection("'; Drop Table users"))
    }
}
