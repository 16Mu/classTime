package com.wind.ggbond.classtime.util

import com.wind.ggbond.classtime.data.local.entity.Course
import io.mockk.mockkStatic
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CourseChangeDetectorTest {

    private lateinit var detector: CourseChangeDetector

    @Before
    fun setUp() {
        detector = CourseChangeDetector
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
    }

    @Test
    fun `detectChanges - should detect added courses`() {
        val localCourses = listOf(
            MockDataFactory.createMockCourse(name = "高等数学", dayOfWeek = 1, startSection = 1)
        )

        val remoteCourses = listOf(
            MockDataFactory.createMockCourse(name = "高等数学", dayOfWeek = 1, startSection = 1),
            MockDataFactory.createMockCourse(name = "大学英语", dayOfWeek = 2, startSection = 3)
        )

        val result = detector.detectChanges(localCourses, remoteCourses)

        assertEquals("应检测到1门新增课程", 1, result.addedCourses.size)
        assertEquals("新增课程应为英语", "大学英语", result.addedCourses[0].courseName)
        assertTrue("不应有删除", result.removedCourses.isEmpty())
    }

    @Test
    fun `detectChanges - should detect removed courses`() {
        val localCourses = listOf(
            MockDataFactory.createMockCourse(name = "高等数学", dayOfWeek = 1, startSection = 1),
            MockDataFactory.createMockCourse(name = "物理", dayOfWeek = 3, startSection = 5)
        )

        val remoteCourses = listOf(
            MockDataFactory.createMockCourse(name = "高等数学", dayOfWeek = 1, startSection = 1)
        )

        val result = detector.detectChanges(localCourses, remoteCourses)

        assertEquals("应检测到1门删除课程", 1, result.removedCourses.size)
        assertTrue("不应有新增", result.addedCourses.isEmpty())
    }

    @Test
    fun `detectChanges - should detect no changes when identical`() {
        val courses = listOf(
            MockDataFactory.createMockCourse(name = "高等数学", dayOfWeek = 1, startSection = 1),
            MockDataFactory.createMockCourse(name = "大学英语", dayOfWeek = 2, startSection = 3)
        )

        val result = detector.detectChanges(courses, courses)

        assertFalse("相同列表应无变更", result.hasChanges())
        assertTrue(result.addedCourses.isEmpty())
        assertTrue(result.removedCourses.isEmpty())
        assertTrue(result.adjustedCourses.isEmpty())
    }

    @Test
    fun `detectChanges - should handle empty lists`() {
        val resultEmptyBoth = detector.detectChanges(emptyList(), emptyList())
        assertFalse("两个空列表应无变更", resultEmptyBoth.hasChanges())

        val resultLocalEmpty = detector.detectChanges(emptyList(), listOf(
            MockDataFactory.createMockCourse(name = "新课")
        ))
        assertTrue("本地空远程有应有新增", resultLocalEmpty.hasChanges())
        assertEquals(1, resultLocalEmpty.addedCourses.size)
    }

    @Test
    fun `hasChanges - should return correct status`() {
        val noChange = CourseChangeResult(emptyList(), emptyList(), emptyList())
        assertFalse(noChange.hasChanges())

        val hasAdded = CourseChangeResult(listOf(MockDataFactory.createMockCourse()), emptyList(), emptyList())
        assertTrue(hasAdded.hasChanges())

        val hasRemoved = CourseChangeResult(emptyList(), listOf(MockDataFactory.createMockCourse()), emptyList())
        assertTrue(hasRemoved.hasChanges())

        val hasAdjusted = CourseChangeResult(emptyList(), emptyList(), listOf(
            CourseTimeAdjustment(
                courseName = "测试",
                teacher = "",
                oldTime = CourseTimeInfo(1, 1, 2, listOf(1), ""),
                newTime = CourseTimeInfo(2, 3, 2, listOf(1), ""),
                originalCourse = MockDataFactory.createMockCourse(),
                changeType = CourseChangeType.TIME_ONLY
            )
        ))
        assertTrue(hasAdjusted.hasChanges())
    }

    @Test
    fun `getSummary - should generate correct summary text`() {
        val noChange = CourseChangeResult(emptyList(), emptyList(), emptyList())
        assertEquals("无课程更新", noChange.getSummary())

        val withAdditions = CourseChangeResult(
            listOf(MockDataFactory.createMockCourse(), MockDataFactory.createMockCourse()),
            emptyList(),
            emptyList()
        )
        assertTrue(withAdditions.getSummary().contains("新增"))
        assertTrue(withAdditions.getSummary().contains("2"))
    }
}
