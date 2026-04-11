package com.wind.ggbond.classtime.service.helper

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VersionMigratorTest {

    private lateinit var migrator: VersionMigrator

    @Before
    fun setUp() {
        migrator = VersionMigrator()
    }

    @Test
    fun `migrateJson - should pass through current version without changes`() {
        val content = """{"meta":{"exportVersion":"3.0","appVersion":"1.2.1","exportTime":"2026-01-01 00:00:00","appName":"课程表","format":"CourseScheduleExport","checksum":""},"courses":[]}"""
        val result = migrator.migrateJson(content, "3.0")
        assertTrue(result.success)
        assertEquals("3.0", result.toVersion)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `migrateJson - should migrate from v2 to v3`() {
        val content = """{"meta":{"version":"2.0","exportTime":"2025-01-01 00:00:00","appName":"课程表","format":"CourseScheduleExport"},"schedule":{"name":"测试课表","schoolName":"测试大学","startDate":"2025-09-01","endDate":"2026-01-15","totalWeeks":20},"classTimes":[],"courses":[{"courseName":"高等数学","teacher":"张三","classroom":"教A101","dayOfWeek":1,"startSection":1,"sectionCount":2,"weeks":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16],"weekExpression":"1-16周","credit":4.0,"color":"#F44336","note":"","reminderEnabled":true,"reminderMinutes":10}]}"""
        val result = migrator.migrateJson(content, "2.0")
        assertTrue(result.success)
        assertEquals("3.0", result.toVersion)
        assertNotNull(result.migratedData)
        assertTrue(result.migratedData!!.contains("exportVersion"))
        assertTrue(result.migratedData!!.contains("3.0"))
    }

    @Test
    fun `migrateJson - should migrate from v1 to v3 directly`() {
        val content = """{"courses":[{"courseName":"高等数学","teacher":"张三","classroom":"教A101","dayOfWeek":1,"startSection":1}]}"""
        val result = migrator.migrateJson(content, "1.0")
        assertTrue(result.success)
        assertEquals("3.0", result.toVersion)
        assertNotNull(result.migratedData)
        assertTrue(result.migratedData!!.contains("meta"))
        assertTrue(result.migratedData!!.contains("exportVersion"))
    }

    @Test
    fun `migrateJson - should handle unknown version with courses field`() {
        val content = """{"courses":[{"courseName":"数学","dayOfWeek":1,"startSection":1}]}"""
        val result = migrator.migrateJson(content, null)
        assertTrue(result.success)
        assertNotNull(result.migratedData)
    }

    @Test
    fun `migrateJson - should fail for completely unrecognized format`() {
        val content = """{"something":"else"}"""
        val result = migrator.migrateJson(content, null)
        assertFalse(result.success)
    }

    @Test
    fun `migrateJson - should preserve course data during migration`() {
        val content = """{"meta":{"version":"2.0","exportTime":"2025-01-01","appName":"课程表","format":"CourseScheduleExport"},"courses":[{"courseName":"线性代数","teacher":"李四","dayOfWeek":2,"startSection":3}]}"""
        val result = migrator.migrateJson(content, "2.0")
        assertTrue(result.success)
        assertTrue(result.migratedData!!.contains("线性代数"))
        assertTrue(result.migratedData!!.contains("李四"))
    }

    @Test
    fun `migrateJson - should add classTimeConfigName during v2 to v3 migration`() {
        val content = """{"meta":{"version":"2.0","exportTime":"2025-01-01","appName":"课程表","format":"CourseScheduleExport"},"schedule":{"name":"测试","schoolName":"测试大学","startDate":"2025-09-01","endDate":"2026-01-15","totalWeeks":20},"courses":[]}"""
        val result = migrator.migrateJson(content, "2.0")
        assertTrue(result.success)
        assertTrue(result.migratedData!!.contains("classTimeConfigName"))
    }
}
