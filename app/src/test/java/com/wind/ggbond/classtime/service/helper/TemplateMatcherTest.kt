package com.wind.ggbond.classtime.service.helper

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TemplateMatcherTest {

    private lateinit var matcher: TemplateMatcher

    @Before
    fun setUp() {
        matcher = TemplateMatcher()
    }

    @Test
    fun `matchTemplate - should match zhengfang headers`() {
        val headers = listOf("课程名称", "授课教师", "上课地点", "星期", "节次", "上课周次", "学分", "课程代码")

        val result = matcher.matchTemplate(headers)

        assertNotNull(result)
        assertEquals("ZHENGFANG", result!!.templateName)
        assertTrue(result.confidence > 0.5f)
        assertTrue(result.fieldMapping.containsKey("courseName"))
        assertTrue(result.fieldMapping.containsKey("teacher"))
    }

    @Test
    fun `matchTemplate - should match qingguo headers`() {
        val headers = listOf("课程名", "任课老师", "教室", "星期", "节次", "周次", "学分")

        val result = matcher.matchTemplate(headers)

        assertNotNull(result)
        assertEquals("QINGGUO", result!!.templateName)
        assertTrue(result.fieldMapping.containsKey("courseName"))
    }

    @Test
    fun `matchTemplate - should match URP headers`() {
        val headers = listOf("课程名", "任课教师", "教室", "星期", "上课时间", "上课周次", "学分", "课序号")

        val result = matcher.matchTemplate(headers)

        assertNotNull(result)
        assertEquals("URP", result!!.templateName)
        assertTrue(result.fieldMapping.containsKey("courseName"))
    }

    @Test
    fun `matchTemplate - should return null for no match`() {
        val headers = listOf("A", "B", "C", "D")

        val result = matcher.matchTemplate(headers)

        assertNull(result)
    }

    @Test
    fun `matchAllTemplates - should return sorted results`() {
        val headers = listOf("课程名称", "授课教师", "上课地点", "星期", "节次", "上课周次")

        val results = matcher.matchAllTemplates(headers)

        assertTrue(results.isNotEmpty())
        for (i in 1 until results.size) {
            assertTrue(results[i - 1].confidence >= results[i].confidence)
        }
    }

    @Test
    fun `matchByKeywords - should identify zhengfang by keywords`() {
        val sheetData = listOf(
            listOf("正方教务管理系统"),
            listOf("课程名称", "授课教师", "上课地点", "星期", "节次", "上课周次")
        )

        val result = matcher.matchByKeywords(sheetData)

        assertNotNull(result)
        assertEquals("ZHENGFANG", result!!.templateName)
    }

    @Test
    fun `matchByKeywords - should return null for no keywords`() {
        val sheetData = listOf(
            listOf("课程名称", "教师"),
            listOf("数学", "张三")
        )

        val result = matcher.matchByKeywords(sheetData)

        assertNull(result)
    }

    @Test
    fun `getTemplateByName - should return correct template`() {
        val template = matcher.getTemplateByName("ZHENGFANG")
        assertNotNull(template)
        assertEquals("正方教务", template!!.displayName)

        val qingguo = matcher.getTemplateByName("QINGGUO")
        assertNotNull(qingguo)
        assertEquals("青果教务", qingguo!!.displayName)

        val urp = matcher.getTemplateByName("URP")
        assertNotNull(urp)
        assertEquals("URP教务", urp!!.displayName)
    }

    @Test
    fun `getTemplateByName - should return null for unknown name`() {
        assertNull(matcher.getTemplateByName("UNKNOWN"))
    }

    @Test
    fun `getAllTemplates - should return all three templates`() {
        val templates = matcher.getAllTemplates()
        assertEquals(3, templates.size)
    }
}

class ZhengfangTemplateTest {

    private lateinit var template: ZhengfangTemplate

    @Before
    fun setUp() {
        template = ZhengfangTemplate()
    }

    @Test
    fun `matchHeaders - should match standard zhengfang headers`() {
        val headers = listOf("课程名称", "授课教师", "上课地点", "星期", "节次", "上课周次", "学分", "课程代码")

        val result = template.matchHeaders(headers)

        assertTrue(result.confidence > 0.5f)
        assertTrue(result.fieldMapping.containsKey("courseName"))
        assertEquals(0, result.fieldMapping["courseName"])
        assertTrue(result.fieldMapping.containsKey("teacher"))
        assertEquals(1, result.fieldMapping["teacher"])
    }

    @Test
    fun `buildConfig - should create correct config`() {
        val config = template.buildConfig(0, mapOf("courseName" to 0, "teacher" to 1))

        assertEquals(0, config.headerRowIndex)
        assertEquals(1, config.dataStartRowIndex)
        assertEquals(2, config.fieldMapping.size)
    }
}

class QingguoTemplateTest {

    private lateinit var template: QingguoTemplate

    @Before
    fun setUp() {
        template = QingguoTemplate()
    }

    @Test
    fun `matchHeaders - should match standard qingguo headers`() {
        val headers = listOf("课程名", "任课老师", "教室", "星期", "节次", "周次")

        val result = template.matchHeaders(headers)

        assertTrue(result.confidence > 0.3f)
        assertTrue(result.fieldMapping.containsKey("courseName"))
    }
}

class UrpTemplateTest {

    private lateinit var template: UrpTemplate

    @Before
    fun setUp() {
        template = UrpTemplate()
    }

    @Test
    fun `matchHeaders - should match standard URP headers`() {
        val headers = listOf("课程名", "任课教师", "教室", "星期", "上课时间", "上课周次")

        val result = template.matchHeaders(headers)

        assertTrue(result.confidence > 0.3f)
        assertTrue(result.fieldMapping.containsKey("courseName"))
    }
}
