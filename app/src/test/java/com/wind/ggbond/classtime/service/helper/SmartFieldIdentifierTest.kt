package com.wind.ggbond.classtime.service.helper

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SmartFieldIdentifierTest {

    private lateinit var identifier: SmartFieldIdentifier

    @Before
    fun setup() {
        identifier = SmartFieldIdentifier()
    }

    @Test
    fun testStandardFormat_AllFieldsPresent() {
        val segments = listOf("高等数学", "1-2节", "1-16周", "教1-201", "计科2101班", "必修", "45", "42", "理论:32实践:16")
        val result = identifier.identifyFields(segments)

        assertEquals("高等数学", result.assignments[SmartFieldIdentifier.FieldType.COURSE_NAME]?.value)
        assertEquals("1-2节", result.assignments[SmartFieldIdentifier.FieldType.SECTION]?.value)
        assertEquals("1-16周", result.assignments[SmartFieldIdentifier.FieldType.WEEKS]?.value)
        assertEquals("教1-201", result.assignments[SmartFieldIdentifier.FieldType.CLASSROOM]?.value)
        assertEquals("计科2101班", result.assignments[SmartFieldIdentifier.FieldType.CLASS_NAME]?.value)
        assertEquals("必修", result.assignments[SmartFieldIdentifier.FieldType.COURSE_NATURE]?.value)
        assertEquals("理论:32实践:16", result.assignments[SmartFieldIdentifier.FieldType.COURSE_HOURS]?.value)
    }

    @Test
    fun testDifferentFieldOrder() {
        val segments = listOf("大学英语", "选修", "A302", "3-4节", "1-12周", "38", "36", "48")
        val result = identifier.identifyFields(segments)

        assertEquals("大学英语", result.assignments[SmartFieldIdentifier.FieldType.COURSE_NAME]?.value)
        assertEquals("3-4节", result.assignments[SmartFieldIdentifier.FieldType.SECTION]?.value)
        assertEquals("1-12周", result.assignments[SmartFieldIdentifier.FieldType.WEEKS]?.value)
        assertEquals("A302", result.assignments[SmartFieldIdentifier.FieldType.CLASSROOM]?.value)
        assertEquals("选修", result.assignments[SmartFieldIdentifier.FieldType.COURSE_NATURE]?.value)
    }

    @Test
    fun testMissingFields() {
        val segments = listOf("线性代数", "5-6节", "1-8周(单)", "理教201", "必修")
        val result = identifier.identifyFields(segments)

        assertEquals("线性代数", result.assignments[SmartFieldIdentifier.FieldType.COURSE_NAME]?.value)
        assertEquals("5-6节", result.assignments[SmartFieldIdentifier.FieldType.SECTION]?.value)
        assertEquals("理教201", result.assignments[SmartFieldIdentifier.FieldType.CLASSROOM]?.value)
        assertEquals("必修", result.assignments[SmartFieldIdentifier.FieldType.COURSE_NATURE]?.value)
        assertNull(result.assignments[SmartFieldIdentifier.FieldType.CLASS_NAME])
    }

    @Test
    fun testTwoNumericFields_StudentAndSelectedCount() {
        val segments = listOf("体育", "1-16周", "1-2节", "操场", "60", "55")
        val result = identifier.identifyFields(segments)

        assertEquals("体育", result.assignments[SmartFieldIdentifier.FieldType.COURSE_NAME]?.value)
        val studentCount = result.assignments[SmartFieldIdentifier.FieldType.STUDENT_COUNT]?.value?.toIntOrNull() ?: 0
        val selectedCount = result.assignments[SmartFieldIdentifier.FieldType.SELECTED_COUNT]?.value?.toIntOrNull() ?: 0
        assertTrue("人数应>=选课人数", studentCount >= selectedCount)
    }

    @Test
    fun testThreeNumericFields() {
        val segments = listOf("数据结构", "1-12周", "3-4节", "B201", "必修", "50", "47", "64")
        val result = identifier.identifyFields(segments)

        assertEquals("数据结构", result.assignments[SmartFieldIdentifier.FieldType.COURSE_NAME]?.value)
        assertEquals("必修", result.assignments[SmartFieldIdentifier.FieldType.COURSE_NATURE]?.value)
        assertNotNull(result.assignments[SmartFieldIdentifier.FieldType.COURSE_HOURS])
    }

    @Test
    fun testCourseHoursWithLabel() {
        val segments = listOf("操作系统", "1-16周", "5-6节", "A401", "必修", "理论:32实践:16", "40", "38")
        val result = identifier.identifyFields(segments)

        assertEquals("理论:32实践:16", result.assignments[SmartFieldIdentifier.FieldType.COURSE_HOURS]?.value)
        assertEquals("40", result.assignments[SmartFieldIdentifier.FieldType.STUDENT_COUNT]?.value)
        assertEquals("38", result.assignments[SmartFieldIdentifier.FieldType.SELECTED_COUNT]?.value)
    }

    @Test
    fun testMinimalFields_CourseNameAndWeeksOnly() {
        val segments = listOf("思政", "1-2节", "1-16周")
        val result = identifier.identifyFields(segments)

        assertEquals("思政", result.assignments[SmartFieldIdentifier.FieldType.COURSE_NAME]?.value)
        assertEquals("1-2节", result.assignments[SmartFieldIdentifier.FieldType.SECTION]?.value)
        assertEquals("1-16周", result.assignments[SmartFieldIdentifier.FieldType.WEEKS]?.value)
    }

    @Test
    fun testCourseNatureKeywords() {
        val natureKeywords = listOf("必修", "选修", "限选", "任选", "考查", "考试")
        for (keyword in natureKeywords) {
            val segments = listOf("某课程", keyword, "1-16周", "1-2节", "A101")
            val result = identifier.identifyFields(segments)
            assertEquals("课程性质'$keyword'应被识别", keyword, result.assignments[SmartFieldIdentifier.FieldType.COURSE_NATURE]?.value)
        }
    }

    @Test
    fun testClassroomPatterns() {
        val classrooms = listOf("教1-201", "A302", "理教201", "博雅楼301")
        for (classroom in classrooms) {
            val segments = listOf("某课程", "1-2节", "1-16周", classroom)
            val result = identifier.identifyFields(segments)
            assertEquals("教室'$classroom'应被识别", classroom, result.assignments[SmartFieldIdentifier.FieldType.CLASSROOM]?.value)
        }
    }

    @Test
    fun testClassNamePatterns() {
        val classNames = listOf("计科2101班", "数学2101班", "软工2203班")
        for (className in classNames) {
            val segments = listOf("某课程", "1-2节", "1-16周", "A101", className)
            val result = identifier.identifyFields(segments)
            assertEquals("班级'$className'应被识别", className, result.assignments[SmartFieldIdentifier.FieldType.CLASS_NAME]?.value)
        }
    }

    @Test
    fun testEmptyInput() {
        val result = identifier.identifyFields(emptyList())
        assertTrue(result.assignments.isEmpty())
        assertEquals(0f, result.overallConfidence, 0.01f)
    }

    @Test
    fun testSingleCourseNameOnly() {
        val segments = listOf("高等数学")
        val result = identifier.identifyFields(segments)
        assertEquals("高等数学", result.assignments[SmartFieldIdentifier.FieldType.COURSE_NAME]?.value)
    }

    @Test
    fun testOverallConfidenceIsPositive() {
        val segments = listOf("高等数学", "1-2节", "1-16周", "教1-201", "必修")
        val result = identifier.identifyFields(segments)
        assertTrue("整体置信度应>0", result.overallConfidence > 0f)
    }

    @Test
    fun testStudentCountConstraint() {
        val segments = listOf("某课程", "1-2节", "1-16周", "A101", "必修", "42", "45")
        val result = identifier.identifyFields(segments)

        val studentCount = result.assignments[SmartFieldIdentifier.FieldType.STUDENT_COUNT]?.value?.toIntOrNull() ?: 0
        val selectedCount = result.assignments[SmartFieldIdentifier.FieldType.SELECTED_COUNT]?.value?.toIntOrNull() ?: 0
        assertTrue("人数($studentCount)应>=选课人数($selectedCount)", studentCount >= selectedCount)
    }

    @Test
    fun testCourseNameWithLiLun_shouldNotBeMisidentifiedAsNature() {
        val segments = listOf("理论力学", "1-2节", "1-16周", "教1-201", "必修")
        val result = identifier.identifyFields(segments)
        assertEquals("理论力学应被识别为课程名", "理论力学", result.assignments[SmartFieldIdentifier.FieldType.COURSE_NAME]?.value)
        assertNull("理论力学不应被识别为课程性质", result.assignments[SmartFieldIdentifier.FieldType.COURSE_NATURE]?.value?.let {
            if (it == "理论力学") it else null
        })
    }

    @Test
    fun testCourseNameWithShiJian_shouldNotBeMisidentified() {
        val segments = listOf("实践课程导论", "1-2节", "1-8周", "A101", "任选")
        val result = identifier.identifyFields(segments)
        assertEquals("实践课程导论应被识别为课程名", "实践课程导论", result.assignments[SmartFieldIdentifier.FieldType.COURSE_NAME]?.value)
    }

    @Test
    fun testPureNumberNotMisidentifiedAsClassName() {
        val segments = listOf("某课程", "1-2节", "1-16周", "A101", "2024")
        val result = identifier.identifyFields(segments)
        assertNull("纯数字2024不应被识别为班级", result.assignments[SmartFieldIdentifier.FieldType.CLASS_NAME])
    }

    @Test
    fun testCompoundCourseNature() {
        val segments = listOf("某课程", "1-2节", "1-16周", "A101", "学科基础")
        val result = identifier.identifyFields(segments)
        assertEquals("学科基础应被识别为课程性质", "学科基础", result.assignments[SmartFieldIdentifier.FieldType.COURSE_NATURE]?.value)
    }

    @Test
    fun testFourNumericFields() {
        val segments = listOf("某课程", "1-2节", "1-16周", "A101", "必修", "50", "47", "64", "32")
        val result = identifier.identifyFields(segments)
        assertNotNull(result.assignments[SmartFieldIdentifier.FieldType.COURSE_HOURS])
        assertNotNull(result.assignments[SmartFieldIdentifier.FieldType.STUDENT_COUNT])
    }

    @Test
    fun testSegmentIndexTracking() {
        val segments = listOf("高等数学", "1-2节", "1-16周", "教1-201", "必修")
        val result = identifier.identifyFields(segments)

        val courseNameAssignment = result.assignments[SmartFieldIdentifier.FieldType.COURSE_NAME]
        assertNotNull(courseNameAssignment)
        assertTrue("segmentIndex应>=0", courseNameAssignment!!.segmentIndex >= 0)
        assertEquals(0, courseNameAssignment.segmentIndex)
    }

    @Test
    fun testCourseHoursPureNumberInRange() {
        val segments = listOf("某课程", "1-2节", "1-16周", "A101", "必修", "48")
        val result = identifier.identifyFields(segments)
        val hoursAssignment = result.assignments[SmartFieldIdentifier.FieldType.COURSE_HOURS]
        if (hoursAssignment != null) {
            assertEquals("48", hoursAssignment.value)
        }
    }
}
