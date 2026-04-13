package com.wind.ggbond.classtime.service.helper

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SmartRecognitionEngineTest {

    private lateinit var engine: SmartRecognitionEngine

    @Before
    fun setUp() {
        engine = SmartRecognitionEngine()
    }

    @Test
    fun `detectHeader - should detect standard header row`() {
        val sheetData = listOf(
            listOf("序号", "课程名称", "授课教师", "上课地点", "星期", "节次", "上课周次", "学分"),
            listOf("1", "高等数学", "张三", "教A101", "周一", "1-2", "1-16周", "4.0")
        )

        val result = engine.detectHeader(sheetData)

        assertEquals(0, result.headerRowIndex)
        assertTrue(result.confidence > 0.5f)
        assertTrue(result.fieldMapping.containsKey("courseName"))
        assertEquals(1, result.fieldMapping["courseName"])
        assertTrue(result.fieldMapping.containsKey("teacher"))
        assertTrue(result.fieldMapping.containsKey("classroom"))
        assertTrue(result.fieldMapping.containsKey("dayOfWeek"))
    }

    @Test
    fun `detectHeader - should detect header with offset rows`() {
        val sheetData = listOf(
            listOf("课程表导出"),
            listOf("学期：2024-2025"),
            listOf("课程名称", "教师", "教室", "星期", "节次", "周次"),
            listOf("高等数学", "张三", "教A101", "周一", "1-2", "1-16周")
        )

        val result = engine.detectHeader(sheetData)

        assertEquals(2, result.headerRowIndex)
        assertTrue(result.fieldMapping.containsKey("courseName"))
    }

    @Test
    fun `detectHeader - should return empty for no header`() {
        val sheetData = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6")
        )

        val result = engine.detectHeader(sheetData)

        assertEquals(-1, result.headerRowIndex)
        assertEquals(0f, result.confidence, 0.01f)
    }

    @Test
    fun `detectHeader - should handle empty sheet`() {
        val result = engine.detectHeader(emptyList())

        assertEquals(-1, result.headerRowIndex)
        assertEquals(0f, result.confidence, 0.01f)
    }

    @Test
    fun `buildFieldMapping - should map standard Chinese headers`() {
        val headerRow = listOf("课程名称", "授课教师", "上课地点", "星期", "节次", "上课周次", "学分")

        val (mapping, confidence) = engine.buildFieldMapping(headerRow)

        assertTrue(mapping.containsKey("courseName"))
        assertEquals(0, mapping["courseName"])
        assertTrue(mapping.containsKey("teacher"))
        assertEquals(1, mapping["teacher"])
        assertTrue(mapping.containsKey("classroom"))
        assertEquals(2, mapping["classroom"])
        assertTrue(confidence > 0.5f)
    }

    @Test
    fun `buildFieldMapping - should map English headers`() {
        val headerRow = listOf("course", "teacher", "room", "day", "section", "weeks", "credit")

        val (mapping, confidence) = engine.buildFieldMapping(headerRow)

        assertTrue(mapping.containsKey("courseName"))
        assertTrue(mapping.containsKey("teacher"))
        assertTrue(mapping.containsKey("classroom"))
        assertTrue(confidence > 0.3f)
    }

    @Test
    fun `buildFieldMapping - should use template-specific patterns`() {
        val headerRow = listOf("课程名称", "授课教师", "上课地点", "星期", "节次", "上课周次", "学分", "课程代码")

        val (mapping, _) = engine.buildFieldMapping(headerRow, EducationalSystemTemplate.ZHENGFANG)

        assertTrue(mapping.containsKey("courseName"))
        assertTrue(mapping.containsKey("teacher"))
    }

    @Test
    fun `determineConfidence - should return HIGH for high confidence`() {
        assertEquals(ImportConfidence.HIGH, engine.determineConfidence(0.9f))
        assertEquals(ImportConfidence.HIGH, engine.determineConfidence(0.8f))
    }

    @Test
    fun `determineConfidence - should return MEDIUM for medium confidence`() {
        assertEquals(ImportConfidence.MEDIUM, engine.determineConfidence(0.7f))
        assertEquals(ImportConfidence.MEDIUM, engine.determineConfidence(0.5f))
    }

    @Test
    fun `determineConfidence - should return LOW for low confidence`() {
        assertEquals(ImportConfidence.LOW, engine.determineConfidence(0.3f))
        assertEquals(ImportConfidence.LOW, engine.determineConfidence(0.1f))
    }

    @Test
    fun `analyzeSheet - should return HIGH confidence for well-structured data`() {
        val sheetData = listOf(
            listOf("课程名称", "授课教师", "上课地点", "星期", "节次", "上课周次", "学分"),
            listOf("高等数学", "张三", "教A101", "周一", "1-2", "1-16周", "4.0")
        )

        val decision = engine.analyzeSheet(sheetData)

        assertEquals(ImportConfidence.HIGH, decision.confidence)
        assertFalse(decision.needsConfirmation)
        assertTrue(decision.fieldMapping.containsKey("courseName"))
    }

    @Test
    fun `analyzeSheet - should return LOW confidence for poorly structured data`() {
        val sheetData = listOf(
            listOf("A", "B", "C"),
            listOf("1", "2", "3")
        )

        val decision = engine.analyzeSheet(sheetData)

        assertEquals(ImportConfidence.LOW, decision.confidence)
        assertTrue(decision.needsConfirmation)
    }

    @Test
    fun `guessTemplate - should identify zhengfang template`() {
        val sheetData = listOf(
            listOf("正方教务管理系统"),
            listOf("课程名称", "授课教师", "上课地点", "星期", "节次", "上课周次")
        )

        val template = engine.guessTemplate(sheetData)

        assertEquals(EducationalSystemTemplate.ZHENGFANG, template)
    }

    @Test
    fun `guessTemplate - should return null for unknown template`() {
        val sheetData = listOf(
            listOf("数据A", "数据B"),
            listOf("123", "456")
        )

        val template = engine.guessTemplate(sheetData)

        assertNull(template)
    }

    @Test
    fun `detectHeader - should identify unmapped headers`() {
        val sheetData = listOf(
            listOf("课程名称", "备注", "教师", "自定义列", "星期"),
            listOf("数学", "重要", "张三", "xxx", "周一")
        )

        val result = engine.detectHeader(sheetData)

        assertTrue(result.unmappedHeaders.isNotEmpty())
    }

    @Test
    fun `analyzeSheet - should compute correct dataStartRow`() {
        val sheetData = listOf(
            listOf("课程名称", "教师", "星期"),
            listOf("数学", "张三", "周一")
        )

        val decision = engine.analyzeSheet(sheetData)

        assertEquals(1, decision.dataStartRowIndex)
    }

    @Test
    fun `buildFieldMapping - should handle partial match headers`() {
        val headerRow = listOf("课程名称", "教师名", "地点")

        val (mapping, _) = engine.buildFieldMapping(headerRow)

        assertTrue(mapping.containsKey("courseName"))
        assertTrue(mapping.containsKey("teacher"))
        assertTrue(mapping.containsKey("classroom"))
    }
}
