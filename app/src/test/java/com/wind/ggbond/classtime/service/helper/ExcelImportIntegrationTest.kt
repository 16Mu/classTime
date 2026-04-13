package com.wind.ggbond.classtime.service.helper

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ExcelImportIntegrationTest {

    private lateinit var excelParser: ExcelParser
    private lateinit var smartRecognitionEngine: SmartRecognitionEngine
    private lateinit var templateMatcher: TemplateMatcher

    @Before
    fun setUp() {
        val xlsxParser = LightweightXlsxParser()
        val xlsParser = LightweightXlsParser()
        excelParser = ExcelParser(xlsxParser, xlsParser)
        smartRecognitionEngine = SmartRecognitionEngine()
        templateMatcher = TemplateMatcher()
    }

    private fun createZhengfangExcel(): ByteArrayInputStream {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("课程表")

        val headerRow = sheet.createRow(0)
        listOf("课程名称", "授课教师", "上课地点", "星期", "节次", "上课周次", "学分", "课程代码")
            .forEachIndexed { idx, value -> headerRow.createCell(idx).setCellValue(value) }

        val data = listOf(
            listOf("高等数学", "张三", "教A101", "周一", "1-2", "1-16周", "4.0", "MATH001"),
            listOf("大学英语", "李四", "教B201", "周二", "3-4", "1-16周", "3.0", "ENG001"),
            listOf("线性代数", "王五", "教C301", "周三", "5-6", "1-16周(单)", "3.0", "MATH002"),
            listOf("大学物理", "赵六", "实验楼101", "周四", "1-2", "1-18周", "4.0", "PHY001"),
            listOf("程序设计", "钱七", "机房201", "周五", "3-4", "1-14周", "3.0", "CS001")
        )

        data.forEachIndexed { rowIdx, rowData ->
            val row = sheet.createRow(rowIdx + 1)
            rowData.forEachIndexed { colIdx, value -> row.createCell(colIdx).setCellValue(value) }
        }

        val baos = ByteArrayOutputStream()
        wb.write(baos)
        wb.close()
        return ByteArrayInputStream(baos.toByteArray())
    }

    private fun createQingguoExcel(): ByteArrayInputStream {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("课表")

        val headerRow = sheet.createRow(0)
        listOf("课程名", "任课老师", "教室", "星期", "节次", "周次", "学分")
            .forEachIndexed { idx, value -> headerRow.createCell(idx).setCellValue(value) }

        val data = listOf(
            listOf("数据结构", "孙八", "教D101", "周一", "5-6", "1-16周", "3.5"),
            listOf("操作系统", "周九", "教E201", "周三", "1-2", "1-18周", "4.0")
        )

        data.forEachIndexed { rowIdx, rowData ->
            val row = sheet.createRow(rowIdx + 1)
            rowData.forEachIndexed { colIdx, value -> row.createCell(colIdx).setCellValue(value) }
        }

        val baos = ByteArrayOutputStream()
        wb.write(baos)
        wb.close()
        return ByteArrayInputStream(baos.toByteArray())
    }

    @Test
    fun `full pipeline - zhengfang template auto-detection and parsing`() {
        val inputStream = createZhengfangExcel()
        val preview = excelParser.getSheetPreview(inputStream, "test.xlsx", 0, 20)

        assertTrue(preview.isNotEmpty())

        val templateMatch = templateMatcher.matchTemplate(preview[0])
        assertNotNull(templateMatch)
        assertEquals("ZHENGFANG", templateMatch!!.templateName)

        val headerResult = smartRecognitionEngine.detectHeader(preview)
        assertTrue(headerResult.confidence > 0.5f)
        assertTrue(headerResult.fieldMapping.containsKey("courseName"))

        val config = ExcelImportConfig(
            headerRowIndex = headerResult.headerRowIndex,
            dataStartRowIndex = headerResult.headerRowIndex + 1,
            fieldMapping = headerResult.fieldMapping
        )

        val freshStream = createZhengfangExcel()
        val parseResult = excelParser.parseExcel(freshStream, "test.xlsx", config)

        assertEquals(5, parseResult.courses.size)

        val math = parseResult.courses[0]
        assertEquals("高等数学", math.courseName)
        assertEquals("张三", math.teacher)
        assertEquals("教A101", math.classroom)
        assertEquals(1, math.dayOfWeek)
        assertEquals(1, math.startSection)
        assertEquals(2, math.sectionCount)
        assertEquals(4.0f, math.credit, 0.01f)
        assertEquals("MATH001", math.courseCode)
        assertTrue(math.weeks.contains(1))
        assertTrue(math.weeks.contains(16))
    }

    @Test
    fun `full pipeline - qingguo template detection and parsing`() {
        val inputStream = createQingguoExcel()
        val preview = excelParser.getSheetPreview(inputStream, "test.xlsx", 0, 20)

        val templateMatch = templateMatcher.matchTemplate(preview[0])
        assertNotNull(templateMatch)
        assertEquals("QINGGUO", templateMatch!!.templateName)

        val config = ExcelImportConfig(
            headerRowIndex = 0,
            dataStartRowIndex = 1,
            fieldMapping = templateMatch.fieldMapping
        )

        val freshStream = createQingguoExcel()
        val parseResult = excelParser.parseExcel(freshStream, "test.xlsx", config)

        assertEquals(2, parseResult.courses.size)
        assertEquals("数据结构", parseResult.courses[0].courseName)
        assertEquals("操作系统", parseResult.courses[1].courseName)
    }

    @Test
    fun `full pipeline - smart recognition for unknown format`() {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("课程表")
        val headerRow = sheet.createRow(0)
        listOf("课程名称", "教师", "教室", "星期", "节次", "周次")
            .forEachIndexed { idx, value -> headerRow.createCell(idx).setCellValue(value) }

        val dataRow = sheet.createRow(1)
        listOf("体育", "陈老师", "操场", "周五", "7-8", "1-16周")
            .forEachIndexed { idx, value -> dataRow.createCell(idx).setCellValue(value) }

        val baos = ByteArrayOutputStream()
        wb.write(baos)
        wb.close()
        val inputStream = ByteArrayInputStream(baos.toByteArray())

        val preview = excelParser.getSheetPreview(inputStream, "test.xlsx", 0, 20)
        val analysis = smartRecognitionEngine.analyzeSheet(preview)

        assertTrue(analysis.confidence == ImportConfidence.HIGH || analysis.confidence == ImportConfidence.MEDIUM)
        assertTrue(analysis.fieldMapping.containsKey("courseName"))

        val config = ExcelImportConfig(
            headerRowIndex = analysis.headerRowIndex,
            dataStartRowIndex = analysis.dataStartRowIndex,
            fieldMapping = analysis.fieldMapping
        )

        val freshStream = ByteArrayInputStream(baos.toByteArray())
        val parseResult = excelParser.parseExcel(freshStream, "test.xlsx", config)

        assertEquals(1, parseResult.courses.size)
        assertEquals("体育", parseResult.courses[0].courseName)
        assertEquals(5, parseResult.courses[0].dayOfWeek)
    }

    @Test
    fun `full pipeline - odd week parsing`() {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("课程表")
        val headerRow = sheet.createRow(0)
        listOf("课程名称", "星期", "节次", "周次")
            .forEachIndexed { idx, value -> headerRow.createCell(idx).setCellValue(value) }

        val dataRow = sheet.createRow(1)
        listOf("实验课", "周四", "5-6", "1-16周(单)")
            .forEachIndexed { idx, value -> dataRow.createCell(idx).setCellValue(value) }

        val baos = ByteArrayOutputStream()
        wb.write(baos)
        wb.close()

        val config = ExcelImportConfig(
            headerRowIndex = 0,
            dataStartRowIndex = 1,
            fieldMapping = mapOf("courseName" to 0, "dayOfWeek" to 1, "section" to 2, "weeks" to 3)
        )

        val inputStream = ByteArrayInputStream(baos.toByteArray())
        val result = excelParser.parseExcel(inputStream, "test.xlsx", config)

        assertEquals(1, result.courses.size)
        val course = result.courses[0]
        assertEquals(4, course.dayOfWeek)
        assertEquals(5, course.startSection)
        assertEquals(2, course.sectionCount)
        assertTrue(course.weeks.contains(1))
        assertTrue(course.weeks.contains(3))
        assertTrue(course.weeks.contains(15))
        assertFalse(course.weeks.contains(2))
        assertFalse(course.weeks.contains(4))
    }

    @Test
    fun `full pipeline - merged cells in course name`() {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("课程表")
        val headerRow = sheet.createRow(0)
        listOf("课程名称", "星期", "节次")
            .forEachIndexed { idx, value -> headerRow.createCell(idx).setCellValue(value) }

        val row1 = sheet.createRow(1)
        row1.createCell(0).setCellValue("大学英语")
        row1.createCell(1).setCellValue("周一")
        row1.createCell(2).setCellValue("1-2")

        val row2 = sheet.createRow(2)
        row2.createCell(0).setCellValue("大学英语")
        row2.createCell(1).setCellValue("周三")
        row2.createCell(2).setCellValue("3-4")

        sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(1, 2, 0, 0))

        val baos = ByteArrayOutputStream()
        wb.write(baos)
        wb.close()

        val config = ExcelImportConfig(
            headerRowIndex = 0,
            dataStartRowIndex = 1,
            fieldMapping = mapOf("courseName" to 0, "dayOfWeek" to 1, "section" to 2)
        )

        val inputStream = ByteArrayInputStream(baos.toByteArray())
        val result = excelParser.parseExcel(inputStream, "test.xlsx", config)

        assertEquals(2, result.courses.size)
        assertEquals("大学英语", result.courses[0].courseName)
        assertEquals("大学英语", result.courses[1].courseName)
        assertEquals(1, result.courses[0].dayOfWeek)
        assertEquals(3, result.courses[1].dayOfWeek)
    }

    @Test
    fun `template matcher - all three templates should be distinguishable`() {
        val zhengfangHeaders = listOf("课程名称", "授课教师", "上课地点", "星期", "节次", "上课周次", "学分", "课程代码")
        val qingguoHeaders = listOf("课程名", "任课老师", "教室", "星期", "节次", "周次", "学分")
        val urpHeaders = listOf("课程名", "任课教师", "教室", "星期", "上课时间", "上课周次", "学分", "课序号")

        val zfResult = templateMatcher.matchTemplate(zhengfangHeaders)
        assertNotNull(zfResult)
        assertEquals("ZHENGFANG", zfResult!!.templateName)

        val qgResult = templateMatcher.matchTemplate(qingguoHeaders)
        assertNotNull(qgResult)
        assertEquals("QINGGUO", qgResult!!.templateName)

        val urpResult = templateMatcher.matchTemplate(urpHeaders)
        assertNotNull(urpResult)
        assertEquals("URP", urpResult!!.templateName)
    }
}
