package com.wind.ggbond.classtime.service.helper

import com.wind.ggbond.classtime.data.local.entity.Course
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ImportValidatorTest {

    private lateinit var validator: ImportValidator

    @Before
    fun setUp() {
        validator = ImportValidator()
    }

    @Test
    fun `validateCourses - should pass valid courses`() {
        val courses = listOf(
            Course(courseName = "高等数学", dayOfWeek = 1, startSection = 1, sectionCount = 2),
            Course(courseName = "大学英语", dayOfWeek = 2, startSection = 3, sectionCount = 2)
        )
        val result = validator.validateCourses(courses)
        assertTrue(result.isValid)
        assertEquals(2, result.validCourses.size)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validateCourses - should fail for empty list`() {
        val result = validator.validateCourses(emptyList())
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("没有找到课程数据"))
    }

    @Test
    fun `validateCourses - should reject course with empty name`() {
        val courses = listOf(
            Course(courseName = "", dayOfWeek = 1, startSection = 1)
        )
        val result = validator.validateCourses(courses)
        assertFalse(result.isValid)
    }

    @Test
    fun `validateCourses - should reject course with invalid dayOfWeek`() {
        val courses = listOf(
            Course(courseName = "测试", dayOfWeek = 0, startSection = 1)
        )
        val result = validator.validateCourses(courses)
        assertFalse(result.isValid)
    }

    @Test
    fun `validateCourses - should reject course with invalid dayOfWeek 8`() {
        val courses = listOf(
            Course(courseName = "测试", dayOfWeek = 8, startSection = 1)
        )
        val result = validator.validateCourses(courses)
        assertFalse(result.isValid)
    }

    @Test
    fun `validateCourses - should reject course with negative startSection`() {
        val courses = listOf(
            Course(courseName = "测试", dayOfWeek = 1, startSection = -1)
        )
        val result = validator.validateCourses(courses)
        assertFalse(result.isValid)
    }

    @Test
    fun `validateCourses - should reject course with zero sectionCount`() {
        val courses = listOf(
            Course(courseName = "测试", dayOfWeek = 1, startSection = 1, sectionCount = 0)
        )
        val result = validator.validateCourses(courses)
        assertFalse(result.isValid)
    }

    @Test
    fun `validateCourses - should warn about empty weeks`() {
        val courses = listOf(
            Course(courseName = "测试", dayOfWeek = 1, startSection = 1, weeks = emptyList(), weekExpression = "")
        )
        val result = validator.validateCourses(courses)
        assertTrue(result.isValid)
        assertTrue(result.warnings.any { it.contains("周次信息为空") })
    }

    @Test
    fun `validateCourses - should reject course with out-of-range weeks`() {
        val courses = listOf(
            Course(courseName = "测试", dayOfWeek = 1, startSection = 1, weeks = listOf(0, 1, 2))
        )
        val result = validator.validateCourses(courses)
        assertFalse(result.isValid)
    }

    @Test
    fun `validateCourses - should warn about high credit`() {
        val courses = listOf(
            Course(courseName = "测试", dayOfWeek = 1, startSection = 1, credit = 25f)
        )
        val result = validator.validateCourses(courses)
        assertTrue(result.isValid)
        assertTrue(result.warnings.any { it.contains("学分超过20") })
    }

    @Test
    fun `validateCourses - should skip invalid courses but keep valid ones`() {
        val courses = listOf(
            Course(courseName = "有效课程", dayOfWeek = 1, startSection = 1),
            Course(courseName = "", dayOfWeek = 1, startSection = 1),
            Course(courseName = "另一有效课程", dayOfWeek = 2, startSection = 3)
        )
        val result = validator.validateCourses(courses)
        assertTrue(result.isValid)
        assertEquals(2, result.validCourses.size)
        assertEquals(1, result.skippedCourses.size)
    }

    @Test
    fun `detectConflicts - should detect time overlap`() {
        val existing = listOf(
            Course(courseName = "数学", dayOfWeek = 1, startSection = 1, sectionCount = 2, weeks = (1..16).toList())
        )
        val new = listOf(
            Course(courseName = "物理", dayOfWeek = 1, startSection = 2, sectionCount = 2, weeks = (1..16).toList())
        )
        val conflicts = validator.detectConflicts(new, existing)
        assertTrue(conflicts.isNotEmpty())
        assertEquals(ImportValidator.ConflictType.TIME_OVERLAP, conflicts.first().conflictType)
    }

    @Test
    fun `detectConflicts - should not detect conflict for different days`() {
        val existing = listOf(
            Course(courseName = "数学", dayOfWeek = 1, startSection = 1, sectionCount = 2, weeks = (1..16).toList())
        )
        val new = listOf(
            Course(courseName = "物理", dayOfWeek = 2, startSection = 1, sectionCount = 2, weeks = (1..16).toList())
        )
        val conflicts = validator.detectConflicts(new, existing)
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `detectConflicts - should not detect conflict for non-overlapping weeks`() {
        val existing = listOf(
            Course(courseName = "数学", dayOfWeek = 1, startSection = 1, sectionCount = 2, weeks = (1..8).toList())
        )
        val new = listOf(
            Course(courseName = "物理", dayOfWeek = 1, startSection = 1, sectionCount = 2, weeks = (9..16).toList())
        )
        val conflicts = validator.detectConflicts(new, existing)
        assertTrue(conflicts.isEmpty())
    }
}
