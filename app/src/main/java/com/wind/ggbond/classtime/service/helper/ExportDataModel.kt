package com.wind.ggbond.classtime.service.helper

data class ExportMeta(
    val exportTime: String,
    val appName: String,
    val appVersion: String,
    val exportVersion: String,
    val format: String,
    val checksum: String = ""
) {
    companion object {
        const val CURRENT_APP_VERSION = "1.2.1"
        const val CURRENT_EXPORT_VERSION = "3.0"
        const val LEGACY_EXPORT_VERSION_2_0 = "2.0"
        const val LEGACY_EXPORT_VERSION_1_0 = "1.0"
        val SUPPORTED_IMPORT_VERSIONS = setOf(CURRENT_EXPORT_VERSION, LEGACY_EXPORT_VERSION_2_0, LEGACY_EXPORT_VERSION_1_0)
    }
}

data class ExportDataModel(
    val meta: ExportMeta,
    val schedule: ScheduleExportItem?,
    val classTimes: List<ClassTimeExportItem>,
    val statistics: StatisticsExportItem?,
    val courses: List<CourseExportItem>
)

data class ScheduleExportItem(
    val name: String,
    val schoolName: String,
    val startDate: String,
    val endDate: String,
    val totalWeeks: Int,
    val classTimeConfigName: String = "default"
)

data class ClassTimeExportItem(
    val sectionNumber: Int,
    val startTime: String,
    val endTime: String,
    val configName: String = "default"
)

data class StatisticsExportItem(
    val totalCourses: Int,
    val totalCredits: Double,
    val coursesByDay: Map<String, Int>
)

data class CourseExportItem(
    val courseName: String,
    val courseCode: String = "",
    val teacher: String = "",
    val classroom: String = "",
    val dayOfWeek: Int,
    val dayName: String = "",
    val startSection: Int,
    val sectionCount: Int = 1,
    val weeks: List<Int> = emptyList(),
    val weekExpression: String = "",
    val credit: Float = 0f,
    val color: String = "#42A5F5",
    val note: String = "",
    val reminderEnabled: Boolean = true,
    val reminderMinutes: Int = 10
)
