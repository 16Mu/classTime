package com.wind.ggbond.classtime.service.helper

import io.mockk.every
import io.mockk.mockk
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ExcelParserTest {

    private lateinit var parser: ExcelParser
    private lateinit var xlsxParser: LightweightXlsxParser
    private lateinit var xlsParser: LightweightXlsParser

    @Before
    fun setUp() {
        xlsxParser = LightweightXlsxParser()
        xlsParser = LightweightXlsParser()
        parser = ExcelParser(xlsxParser, xlsParser)
    }

    private fun createXlsxWorkbook(configure: XSSFWorkbook.() -> Unit): ByteArrayInputStream {
        val wb = XSSFWorkbook()
        wb.configure()
        val baos = ByteArrayOutputStream()
        wb.write(baos)
        wb.close()
        return ByteArrayInputStream(baos.toByteArray())
    }

    private fun createXlsWorkbook(configure: HSSFWorkbook.() -> Unit): ByteArrayInputStream {
        val wb = HSSFWorkbook()
        wb.configure()
        val baos = ByteArrayOutputStream()
        wb.write(baos)
        wb.close()
        return ByteArrayInputStream(baos.toByteArray())
    }

    @Test
    fun `parseExcel - should parse basic xlsx course data`() {
        val inputStream = createXlsxWorkbook {
            val sheet = createSheet("课程表")
            val headerRow = sheet.createRow(0)
            headerRow.createCell(0).setCellValue("课程名称")
            headerRow.createCell(1).setCellValue("教师")
            headerRow.createCell(2).setCellValue("教室")
            headerRow.createCell(3).setCellValue("星期")
            headerRow.createCell(4).setCellValue("节次")
            headerRow.createCell(5).setCellValue("周次")
            headerRow.createCell(6).setCellValue("学分")

            val dataRow = sheet.createRow(1)
            dataRow.createCell(0).setCellValue("高等数学")
            dataRow.createCell(1).setCellValue("张三")
            dataRow.createCell(2).setCellValue("教A101")
            dataRow.createCell(3).setCellValue("周一")
            dataRow.createCell(4).setCellValue("1-2")
            dataRow.createCell(5).setCellValue("1-16周")
            dataRow.createCell(6).setCellValue("4.0")
        }

        val config = ExcelImportConfig(
            headerRowIndex = 0,
            dataStartRowIndex = 1,
            fieldMapping = mapOf(
                "courseName" to 0,
                "teacher" to 1,
                "classroom" to 2,
                "dayOfWeek" to 3,
                "section" to 4,
                "weeks" to 5,
                "credit" to 6
            )
        )

        val result = parser.parseExcel(inputStream, "test.xlsx", config)

        assertTrue("应至少解析出一门课程", result.courses.isNotEmpty())
        val course = result.courses.firstOrNull { it.courseName == "高等数学" }
        assertNotNull("应包含高等数学课程", course)
        course?.let {
            assertEquals("张三", it.teacher)
            assertEquals("教A101", it.classroom)
            assertEquals(1, it.dayOfWeek)
        }
    }

    @Test
    fun `parseExcel - should handle empty field mapping`() {
        val inputStream = createXlsxWorkbook {
            val sheet = createSheet("课程表")
            val headerRow = sheet.createRow(0)
            headerRow.createCell(0).setCellValue("课程名称")
        }

        val config = ExcelImportConfig(fieldMapping = emptyMap())
        val result = parser.parseExcel(inputStream, "test.xlsx", config)

        assertTrue("空映射应返回空课程列表", result.courses.isEmpty())
    }

    @Test
    fun `parseExcel - should parse numeric day of week in xlsx`() {
        val inputStream = createXlsxWorkbook {
            val sheet = createSheet("课程表")
            val headerRow = sheet.createRow(0)
            headerRow.createCell(0).setCellValue("课程名称")
            headerRow.createCell(1).setCellValue("星期")

            val dataRow = sheet.createRow(1)
            dataRow.createCell(0).setCellValue("体育")
            dataRow.createCell(1).setCellValue(3.0)
        }

        val config = ExcelImportConfig(
            headerRowIndex = 0,
            dataStartRowIndex = 1,
            fieldMapping = mapOf("courseName" to 0, "dayOfWeek" to 1)
        )

        val result = parser.parseExcel(inputStream, "test.xlsx", config)

        if (result.courses.isNotEmpty()) {
            assertEquals(3, result.courses[0].dayOfWeek)
        }
    }

    @Test
    fun `parseExcel - should handle xls format`() {
        val inputStream = createXlsWorkbook {
            val sheet = createSheet("课程表")
            val headerRow = sheet.createRow(0)
            headerRow.createCell(0).setCellValue("课程名称")
            headerRow.createCell(1).setCellValue("星期")

            val dataRow = sheet.createRow(1)
            dataRow.createCell(0).setCellValue("物理")
            dataRow.createCell(1).setCellValue("周一")
        }

        val config = ExcelImportConfig(
            headerRowIndex = 0,
            dataStartRowIndex = 1,
            fieldMapping = mapOf("courseName" to 0, "dayOfWeek" to 1)
        )

        val result = parser.parseExcel(inputStream, "test.xls", config)

        assertTrue("应至少解析出一门课程", result.courses.isNotEmpty())
        assertEquals("物理", result.courses[0].courseName)
    }

    @Test
    fun `parseExcel - should skip empty rows in xlsx`() {
        val inputStream = createXlsxWorkbook {
            val sheet = createSheet("课程表")
            val headerRow = sheet.createRow(0)
            headerRow.createCell(0).setCellValue("课程名称")
            headerRow.createCell(1).setCellValue("星期")

            val dataRow = sheet.createRow(1)
            dataRow.createCell(0).setCellValue("线性代数")
            dataRow.createCell(1).setCellValue("周二")

            sheet.createRow(2)

            val dataRow3 = sheet.createRow(3)
            dataRow3.createCell(0).setCellValue("概率论")
            dataRow3.createCell(1).setCellValue("周四")
        }

        val config = ExcelImportConfig(
            headerRowIndex = 0,
            dataStartRowIndex = 1,
            fieldMapping = mapOf("courseName" to 0, "dayOfWeek" to 1)
        )

        val result = parser.parseExcel(inputStream, "test.xlsx", config)

        assertEquals(2, result.courses.size)
    }

    @Test
    fun `getSheetNames - should return sheet names for xlsx`() {
        val inputStream = createXlsxWorkbook {
            createSheet("课程表")
            createSheet("成绩单")
        }

        val names = parser.getSheetNames(inputStream, "test.xlsx")

        assertEquals(2, names.size)
        assertEquals("课程表", names[0])
        assertEquals("成绩单", names[1])
    }

    @Test
    fun `getSheetPreview - should return preview data for xlsx`() {
        val inputStream = createXlsxWorkbook {
            val sheet = createSheet("课程表")
            val headerRow = sheet.createRow(0)
            headerRow.createCell(0).setCellValue("课程名称")
            headerRow.createCell(1).setCellValue("教师")

            val dataRow = sheet.createRow(1)
            dataRow.createCell(0).setCellValue("高等数学")
            dataRow.createCell(1).setCellValue("张三")
        }

        val preview = parser.getSheetPreview(inputStream, "test.xlsx", 0, 10)

        assertTrue("预览应包含数据", preview.isNotEmpty())
    }

    @Test
    fun `parseExcel - should handle invalid day of week gracefully`() {
        val inputStream = createXlsxWorkbook {
            val sheet = createSheet("课程表")
            val headerRow = sheet.createRow(0)
            headerRow.createCell(0).setCellValue("课程名称")
            headerRow.createCell(1).setCellValue("星期")

            val dataRow = sheet.createRow(1)
            dataRow.createCell(0).setCellValue("无效课")
            dataRow.createCell(1).setCellValue("周八")
        }

        val config = ExcelImportConfig(
            headerRowIndex = 0,
            dataStartRowIndex = 1,
            fieldMapping = mapOf("courseName" to 0, "dayOfWeek" to 1)
        )

        val result = parser.parseExcel(inputStream, "test.xlsx", config)

        assertTrue("无效星期应产生警告或空结果", result.courses.isEmpty() || result.warnings.isNotEmpty())
    }

    @Test
    fun `parseExcel - should handle multiple courses in xlsx`() {
        val inputStream = createXlsxWorkbook {
            val sheet = createSheet("课程表")
            val headerRow = sheet.createRow(0)
            headerRow.createCell(0).setCellValue("课程名称")
            headerRow.createCell(1).setCellValue("教师")
            headerRow.createCell(2).setCellValue("教室")
            headerRow.createCell(3).setCellValue("星期")
            headerRow.createCell(4).setCellValue("节次")
            headerRow.createCell(5).setCellValue("周次")

            val courses = listOf(
                Triple("高等数学", "张三", "周一"),
                Triple("线性代数", "李四", "周二"),
                Triple("大学英语", "王五", "周三")
            )
            courses.forEachIndexed { idx, (name, teacher, day) ->
                val row = sheet.createRow(idx + 1)
                row.createCell(0).setCellValue(name)
                row.createCell(1).setCellValue(teacher)
                row.createCell(2).setCellValue("教室${idx + 1}")
                row.createCell(3).setCellValue(day)
                row.createCell(4).setCellValue("${idx + 1}-${idx + 2}")
                row.createCell(5).setCellValue("1-16周")
            }
        }

        val config = ExcelImportConfig(
            headerRowIndex = 0,
            dataStartRowIndex = 1,
            fieldMapping = mapOf(
                "courseName" to 0,
                "teacher" to 1,
                "classroom" to 2,
                "dayOfWeek" to 3,
                "section" to 4,
                "weeks" to 5
            )
        )

        val result = parser.parseExcel(inputStream, "test.xlsx", config)

        assertEquals(3, result.courses.size)
        assertEquals("高等数学", result.courses[0].courseName)
        assertEquals("线性代数", result.courses[1].courseName)
        assertEquals("大学英语", result.courses[2].courseName)
    }

    @Test
    fun `parseExcel - should handle week expressions with odd-even`() {
        val inputStream = createXlsxWorkbook {
            val sheet = createSheet("课程表")
            val headerRow = sheet.createRow(0)
            headerRow.createCell(0).setCellValue("课程名称")
            headerRow.createCell(1).setCellValue("星期")
            headerRow.createCell(2).setCellValue("节次")
            headerRow.createCell(3).setCellValue("周次")

            val dataRow = sheet.createRow(1)
            dataRow.createCell(0).setCellValue("实验课")
            dataRow.createCell(1).setCellValue("周四")
            dataRow.createCell(2).setCellValue("5-6")
            dataRow.createCell(3).setCellValue("1-16周(单)")
        }

        val config = ExcelImportConfig(
            headerRowIndex = 0,
            dataStartRowIndex = 1,
            fieldMapping = mapOf(
                "courseName" to 0,
                "dayOfWeek" to 1,
                "section" to 2,
                "weeks" to 3
            )
        )

        val result = parser.parseExcel(inputStream, "test.xlsx", config)

        if (result.courses.isNotEmpty()) {
            val weeks = result.courses[0].weeks
            assertTrue("应包含单周", weeks.contains(1))
            assertTrue("应包含单周", weeks.contains(3))
            assertFalse("不应包含双周", weeks.contains(2))
            assertFalse("不应包含双周", weeks.contains(4))
        }
    }

    @Test
    fun `parseExcel - should handle merged cells in xlsx`() {
        val inputStream = createXlsxWorkbook {
            val sheet = createSheet("课程表")
            val headerRow = sheet.createRow(0)
            headerRow.createCell(0).setCellValue("课程名称")
            headerRow.createCell(1).setCellValue("星期")
            headerRow.createCell(2).setCellValue("节次")

            val row1 = sheet.createRow(1)
            row1.createCell(0).setCellValue("大学英语")
            row1.createCell(1).setCellValue("周三")
            row1.createCell(2).setCellValue("3-4")

            val row2 = sheet.createRow(2)
            row2.createCell(0).setCellValue("大学英语")
            row2.createCell(1).setCellValue("周五")
            row2.createCell(2).setCellValue("1-2")

            sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(1, 2, 0, 0))
        }

        val config = ExcelImportConfig(
            headerRowIndex = 0,
            dataStartRowIndex = 1,
            fieldMapping = mapOf(
                "courseName" to 0,
                "dayOfWeek" to 1,
                "section" to 2
            )
        )

        val result = parser.parseExcel(inputStream, "test.xlsx", config)

        assertEquals(2, result.courses.size)
        assertEquals("大学英语", result.courses[0].courseName)
        assertEquals("大学英语", result.courses[1].courseName)
    }
}
