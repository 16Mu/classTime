package com.wind.ggbond.classtime.service.helper

import org.junit.Assert.*
import org.junit.Test

class ExportDataModelTest {

    @Test
    fun `ExportMeta - should have correct current version constants`() {
        assertEquals("1.2.1", ExportMeta.CURRENT_APP_VERSION)
        assertEquals("3.0", ExportMeta.CURRENT_EXPORT_VERSION)
    }

    @Test
    fun `ExportMeta - should include legacy versions in supported set`() {
        assertTrue(ExportMeta.SUPPORTED_IMPORT_VERSIONS.contains("3.0"))
        assertTrue(ExportMeta.SUPPORTED_IMPORT_VERSIONS.contains("2.0"))
        assertTrue(ExportMeta.SUPPORTED_IMPORT_VERSIONS.contains("1.0"))
    }

    @Test
    fun `ExportDataModel - should create with default values`() {
        val meta = ExportMeta(
            exportTime = "2026-01-01 00:00:00",
            appName = "课程表",
            appVersion = "1.2.1",
            exportVersion = "3.0",
            format = "CourseScheduleExport"
        )
        assertEquals("", meta.checksum)
        assertEquals("课程表", meta.appName)
    }

    @Test
    fun `ExportDataModel - should copy with checksum`() {
        val meta = ExportMeta(
            exportTime = "2026-01-01 00:00:00",
            appName = "课程表",
            appVersion = "1.2.1",
            exportVersion = "3.0",
            format = "CourseScheduleExport"
        )
        val withChecksum = meta.copy(checksum = "abc123")
        assertEquals("abc123", withChecksum.checksum)
        assertEquals(meta.exportTime, withChecksum.exportTime)
    }

    @Test
    fun `CourseExportItem - should have correct default values`() {
        val item = CourseExportItem(
            courseName = "测试",
            dayOfWeek = 1,
            startSection = 1
        )
        assertEquals("", item.courseCode)
        assertEquals("", item.teacher)
        assertEquals("", item.classroom)
        assertEquals(1, item.sectionCount)
        assertEquals(0f, item.credit)
        assertEquals(true, item.reminderEnabled)
        assertEquals(10, item.reminderMinutes)
    }

    @Test
    fun `ScheduleExportItem - should have default configName`() {
        val item = ScheduleExportItem(
            name = "测试课表",
            schoolName = "测试大学",
            startDate = "2026-09-01",
            endDate = "2027-01-15",
            totalWeeks = 20
        )
        assertEquals("default", item.classTimeConfigName)
    }

    @Test
    fun `ClassTimeExportItem - should have default configName`() {
        val item = ClassTimeExportItem(
            sectionNumber = 1,
            startTime = "08:00",
            endTime = "08:45"
        )
        assertEquals("default", item.configName)
    }
}
