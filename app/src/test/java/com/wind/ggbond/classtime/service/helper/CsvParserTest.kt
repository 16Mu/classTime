package com.wind.ggbond.classtime.service.helper

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CsvParserTest {

    private lateinit var csvParser: CsvParser

    @Before
    fun setup() {
        csvParser = CsvParser()
    }

    @Test
    fun `parseCsvLine simple comma separated`() {
        val result = csvParser.parseCsvLine("a,b,c,d")
        assertEquals(listOf("a", "b", "c", "d"), result)
    }

    @Test
    fun `parseCsvLine with quoted fields`() {
        val result = csvParser.parseCsvLine("\"hello, world\",b,c")
        assertEquals(listOf("hello, world", "b", "c"), result)
    }

    @Test
    fun `parseCsvLine with escaped double quotes`() {
        val result = csvParser.parseCsvLine("\"he said \"\"hello\"\"\",b,c")
        assertEquals(listOf("he said \"hello\"", "b", "c"), result)
    }

    @Test
    fun `parseCsvLine empty line`() {
        val result = csvParser.parseCsvLine("")
        assertEquals(listOf(""), result)
    }

    @Test
    fun `parseCsvLine single field`() {
        val result = csvParser.parseCsvLine("hello")
        assertEquals(listOf("hello"), result)
    }

    @Test
    fun `parseCsvRows filters comments and blank lines`() {
        val content = """
            # This is a comment
            序号,课程名称,教师
            
            1,高等数学,张老师
        """.trimIndent()
        val rows = csvParser.parseCsvRows(content)
        assertEquals(2, rows.size)
        assertEquals("序号", rows[0][0])
        assertEquals("1", rows[1][0])
    }

    @Test
    fun `detectHeaderRow identifies header with course keywords`() {
        val rows = listOf(
            listOf("序号", "课程名称", "教师", "教室", "星期", "节次"),
            listOf("1", "高等数学", "张老师", "A101", "周一", "1-2")
        )
        val headerRow = csvParser.detectHeaderRow(rows)
        assertEquals(0, headerRow)
    }

    @Test
    fun `detectHeaderRow returns 0 when no header found`() {
        val rows = listOf(
            listOf("1", "2", "3", "4"),
            listOf("5", "6", "7", "8")
        )
        val headerRow = csvParser.detectHeaderRow(rows)
        assertEquals(0, headerRow)
    }

    @Test
    fun `autoDetectFieldMapping maps standard headers`() {
        val rows = listOf(
            listOf("课程名称", "教师", "教室", "星期", "节次", "周次", "学分"),
            listOf("高等数学", "张老师", "A101", "周一", "1-2", "1-16周", "4")
        )
        val mapping = csvParser.autoDetectFieldMapping(rows)
        assertTrue(mapping.containsKey("courseName"))
        assertTrue(mapping.containsKey("teacher"))
        assertTrue(mapping.containsKey("classroom"))
        assertTrue(mapping.containsKey("dayOfWeek"))
        assertTrue(mapping.containsKey("section"))
        assertTrue(mapping.containsKey("weeks"))
        assertTrue(mapping.containsKey("credit"))
    }

    @Test
    fun `autoDetectFieldMapping maps English headers`() {
        val rows = listOf(
            listOf("course", "teacher", "room", "day", "section", "weeks", "credit"),
            listOf("Math", "Mr. Smith", "Room 101", "Monday", "1-2", "1-16", "4")
        )
        val mapping = csvParser.autoDetectFieldMapping(rows)
        assertTrue(mapping.containsKey("courseName"))
        assertTrue(mapping.containsKey("teacher"))
    }

    @Test
    fun `autoDetectFieldMapping returns empty when no course name found`() {
        val rows = listOf(
            listOf("A", "B", "C", "D"),
            listOf("1", "2", "3", "4")
        )
        val mapping = csvParser.autoDetectFieldMapping(rows)
        assertFalse(mapping.containsKey("courseName"))
    }

    @Test
    fun `parseCsvContent with standard Chinese headers`() {
        val content = """
            课程名称,教师,教室,星期,节次,周次,学分
            高等数学,张老师,A101,周一,1-2,1-16周,4
            线性代数,李老师,B202,周二,3-4,1-16周,3
        """.trimIndent()
        val result = csvParser.parseCsvContent(content)
        assertEquals(2, result.courses.size)
        assertEquals("高等数学", result.courses[0].courseName)
        assertEquals("张老师", result.courses[0].teacher)
        assertEquals("A101", result.courses[0].classroom)
        assertEquals(1, result.courses[0].dayOfWeek)
        assertEquals(1, result.courses[0].startSection)
        assertEquals(2, result.courses[0].sectionCount)
        assertEquals(4f, result.courses[0].credit)
    }

    @Test
    fun `parseCsvContent with app export format`() {
        val content = """
            序号,课程名称,教师,教室,星期,节次,上课时间,周次,学分,颜色,提醒,备注
            1,高等数学,张老师,A101,1,1,,1-16周,4,,,,
        """.trimIndent()
        val result = csvParser.parseCsvContent(content)
        assertTrue(result.courses.isNotEmpty())
        assertEquals("高等数学", result.courses[0].courseName)
    }

    @Test
    fun `parseCsvContent with empty content returns empty`() {
        val result = csvParser.parseCsvContent("")
        assertTrue(result.courses.isEmpty())
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `parseCsvContent with no course name field returns empty`() {
        val content = """
            A,B,C,D
            1,2,3,4
        """.trimIndent()
        val result = csvParser.parseCsvContent(content)
        assertTrue(result.courses.isEmpty())
    }

    @Test
    fun `parseCsvContent with custom field mapping`() {
        val content = """
            高等数学,张老师,A101,1,1-2,1-16周,4
            线性代数,李老师,B202,2,3-4,1-16周,3
        """.trimIndent()
        val config = TableImportConfig(
            headerRowIndex = -1,
            dataStartRowIndex = 0,
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
        val result = csvParser.parseCsvContent(content, config)
        assertEquals(2, result.courses.size)
        assertEquals("高等数学", result.courses[0].courseName)
    }

    @Test
    fun `parseCsvContent handles week expressions correctly`() {
        val content = """
            课程名称,教师,教室,星期,节次,周次
            高等数学,张老师,A101,周一,1-2,1-16周(单)
        """.trimIndent()
        val result = csvParser.parseCsvContent(content)
        assertTrue(result.courses.isNotEmpty())
        val weeks = result.courses[0].weeks
        assertTrue(weeks.all { it % 2 == 1 })
    }

    @Test
    fun `parseCsvContent handles section ranges`() {
        val content = """
            课程名称,教师,教室,星期,节次,周次
            高等数学,张老师,A101,周一,3-4,1-16周
        """.trimIndent()
        val result = csvParser.parseCsvContent(content)
        assertTrue(result.courses.isNotEmpty())
        assertEquals(3, result.courses[0].startSection)
        assertEquals(2, result.courses[0].sectionCount)
    }

    @Test
    fun `parseCsvContent skips invalid day of week`() {
        val content = """
            课程名称,教师,教室,星期,节次,周次
            高等数学,张老师,A101,无效,1-2,1-16周
        """.trimIndent()
        val result = csvParser.parseCsvContent(content)
        assertTrue(result.courses.isEmpty())
        assertTrue(result.warnings.any { it.contains("星期无效") })
    }

    @Test
    fun `readWithEncodingDetection handles BOM`() {
        val contentWithBom = "\uFEFF课程名称,教师\n高等数学,张老师"
        val bytes = contentWithBom.toByteArray(Charsets.UTF_8)
        val inputStream = bytes.inputStream()
        val (content, encoding) = csvParser.readWithEncodingDetection(inputStream)
        assertEquals("UTF-8", encoding)
        assertFalse(content.startsWith("\uFEFF"))
        assertTrue(content.contains("课程名称"))
    }

    @Test
    fun `parseCsvContent with semicolon delimiter`() {
        val content = """
            课程名称;教师;教室;星期;节次;周次
            高等数学;张老师;A101;周一;1-2;1-16周
        """.trimIndent()
        val config = TableImportConfig(delimiter = ';')
        val result = csvParser.parseCsvContent(content, config)
        assertTrue(result.courses.isNotEmpty())
        assertEquals("高等数学", result.courses[0].courseName)
    }

    @Test
    fun `parseCsvContent with Zhengfang template headers`() {
        val content = """
            课程名称,授课教师,上课地点,星期,节次,上课周次,学分,课程代码
            高等数学A,王教授,教学楼101,周一,1-2,1-16周,4,MATH001
        """.trimIndent()
        val result = csvParser.parseCsvContent(content)
        assertTrue(result.courses.isNotEmpty())
        assertEquals("高等数学A", result.courses[0].courseName)
        assertEquals("王教授", result.courses[0].teacher)
        assertEquals("教学楼101", result.courses[0].classroom)
        assertEquals("MATH001", result.courses[0].courseCode)
    }

    @Test
    fun `parseCsvContent with Qingguo template headers`() {
        val content = """
            课程名,任课老师,教室,星期,上课节次,周次,学分
            大学英语,李老师,外语楼201,周三,5-6,1-18周,2
        """.trimIndent()
        val result = csvParser.parseCsvContent(content)
        assertTrue(result.courses.isNotEmpty())
        assertEquals("大学英语", result.courses[0].courseName)
        assertEquals("李老师", result.courses[0].teacher)
    }

    @Test
    fun `parseCsvContent with URP template headers`() {
        val content = """
            课程名,任课教师,教室,星期,上课时间,上课周次,学分,课序号
            数据结构,赵老师,计算机楼301,周四,7-8,1-16周,3,CS001
        """.trimIndent()
        val result = csvParser.parseCsvContent(content)
        assertTrue(result.courses.isNotEmpty())
        assertEquals("数据结构", result.courses[0].courseName)
        assertEquals("CS001", result.courses[0].courseCode)
    }
}
