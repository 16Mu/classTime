package com.wind.ggbond.classtime.service.helper

import com.wind.ggbond.classtime.data.model.ParsedCourse

data class ExcelImportConfig(
    val sheetIndex: Int = 0,
    val headerRowIndex: Int = 0,
    val dataStartRowIndex: Int = 1,
    val fieldMapping: Map<String, Int> = emptyMap(),
    val skipEmptyRows: Boolean = true,
    val trimWhitespace: Boolean = true
)

data class HeaderDetectionResult(
    val headerRowIndex: Int,
    val fieldMapping: Map<String, Int>,
    val confidence: Float,
    val detectedTemplate: String? = null,
    val unmappedHeaders: List<String> = emptyList()
)

data class TemplateMatchResult(
    val templateName: String,
    val confidence: Float,
    val fieldMapping: Map<String, Int>,
    val headerRowIndex: Int,
    val dataStartRowIndex: Int
)

enum class ImportConfidence {
    HIGH,
    MEDIUM,
    LOW
}

data class ImportRouteDecision(
    val confidence: ImportConfidence,
    val templateName: String?,
    val fieldMapping: Map<String, Int>,
    val headerRowIndex: Int,
    val dataStartRowIndex: Int,
    val needsConfirmation: Boolean,
    val suggestedAction: String = ""
)

data class ExcelParseResult(
    val courses: List<ParsedCourse>,
    val warnings: List<String> = emptyList(),
    val detectedTemplate: String? = null,
    val confidence: Float = 0f,
    val headerRowIndex: Int = 0,
    val fieldMapping: Map<String, Int> = emptyMap()
)

data class MergedCellInfo(
    val firstRow: Int,
    val lastRow: Int,
    val firstCol: Int,
    val lastCol: Int,
    val value: String
)

enum class EducationalSystemTemplate(
    val displayName: String,
    val keywords: List<String>,
    val headerPatterns: Map<String, List<String>>,
    val defaultHeaderRow: Int,
    val defaultDataStartRow: Int
) {
    ZHENGFANG(
        displayName = "正方教务",
        keywords = listOf("正方", "zhengfang", "教务管理系统"),
        headerPatterns = mapOf(
            "courseName" to listOf("课程名称", "课程", "科目"),
            "teacher" to listOf("授课教师", "教师", "任课教师"),
            "classroom" to listOf("上课地点", "教室", "教学班"),
            "dayOfWeek" to listOf("星期", "星 期"),
            "section" to listOf("节次", "节 次", "上课节次"),
            "weeks" to listOf("上课周次", "周次", "起止周"),
            "credit" to listOf("学分"),
            "courseCode" to listOf("课程代码", "课程编号"),
            "className" to listOf("教学班名称", "班级"),
            "courseNature" to listOf("课程性质", "课程属性", "修读类型"),
            "studentCount" to listOf("教学班人数", "人数"),
            "selectedCount" to listOf("选课人数", "已选人数"),
            "courseHours" to listOf("学时组成", "总学时", "学时")
        ),
        defaultHeaderRow = 0,
        defaultDataStartRow = 1
    ),
    QINGGUO(
        displayName = "青果教务",
        keywords = listOf("青果", "qingguo", "Kingosoft"),
        headerPatterns = mapOf(
            "courseName" to listOf("课程名", "课程名称", "科目名称"),
            "teacher" to listOf("教师", "任课老师", "授课老师"),
            "classroom" to listOf("教室", "地点", "上课地点"),
            "dayOfWeek" to listOf("星期", "周次"),
            "section" to listOf("节次", "上课节次"),
            "weeks" to listOf("周次", "上课周次", "起止周"),
            "credit" to listOf("学分"),
            "courseCode" to listOf("课程号", "课程编号"),
            "className" to listOf("班级", "教学班"),
            "courseNature" to listOf("课程性质", "课程类别"),
            "studentCount" to listOf("人数", "班级人数"),
            "selectedCount" to listOf("选课人数"),
            "courseHours" to listOf("学时", "课时")
        ),
        defaultHeaderRow = 0,
        defaultDataStartRow = 1
    ),
    URP(
        displayName = "URP教务",
        keywords = listOf("URP", "urp", "综合教务"),
        headerPatterns = mapOf(
            "courseName" to listOf("课程名", "课程名称"),
            "teacher" to listOf("教师", "任课教师"),
            "classroom" to listOf("教室", "上课地点"),
            "dayOfWeek" to listOf("星期"),
            "section" to listOf("节次", "上课时间"),
            "weeks" to listOf("周次", "上课周次"),
            "credit" to listOf("学分"),
            "courseCode" to listOf("课序号", "课程号"),
            "className" to listOf("班级", "教学班名称"),
            "courseNature" to listOf("课程性质", "课程类型"),
            "studentCount" to listOf("人数", "容量"),
            "selectedCount" to listOf("选课人数", "已选人数"),
            "courseHours" to listOf("学时", "总学时")
        ),
        defaultHeaderRow = 0,
        defaultDataStartRow = 1
    );

    companion object {
        fun fromName(name: String): EducationalSystemTemplate? {
            return entries.find { it.name.equals(name, ignoreCase = true) }
        }
    }
}
