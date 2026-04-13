package com.wind.ggbond.classtime.service.helper

import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartRecognitionEngine @Inject constructor() {

    companion object {
        private const val TAG = "SmartRecognitionEngine"
        private const val MAX_ROWS_TO_SCAN = 20
        private const val HIGH_CONFIDENCE_THRESHOLD = 0.8f
        private const val MEDIUM_CONFIDENCE_THRESHOLD = 0.5f

        private val COURSE_NAME_PATTERNS = listOf(
            "课程名称", "课程名", "课程", "科目", "科目名称", "科目名",
            "course", "subject", "name", "课程名程"
        )
        private val TEACHER_PATTERNS = listOf(
            "授课教师", "任课教师", "教师", "任课老师", "授课老师", "老师",
            "teacher", "instructor"
        )
        private val CLASSROOM_PATTERNS = listOf(
            "上课地点", "教室", "地点", "教学班", "上课教室",
            "room", "location", "classroom"
        )
        private val DAY_PATTERNS = listOf(
            "星期", "星 期", "周次", "上课星期",
            "day", "weekday"
        )
        private val SECTION_PATTERNS = listOf(
            "节次", "节 次", "上课节次", "上课时间", "节",
            "section", "period"
        )
        private val WEEK_PATTERNS = listOf(
            "上课周次", "周次", "起止周", "上课周", "周",
            "week", "weeks"
        )
        private val CREDIT_PATTERNS = listOf(
            "学分", "credit", "credits"
        )
        private val COURSE_CODE_PATTERNS = listOf(
            "课程代码", "课程编号", "课程号", "课序号",
            "course_code", "course_id"
        )
        private val CLASS_NAME_PATTERNS = listOf(
            "教学班名称", "班级", "教学班", "班名",
            "class_name", "classname"
        )
        private val COURSE_NATURE_PATTERNS = listOf(
            "课程性质", "课程类型", "课程类别", "修读类型", "课程属性",
            "course_nature", "course_type", "nature"
        )
        private val STUDENT_COUNT_PATTERNS = listOf(
            "教学班人数", "人数", "班级人数", "容量",
            "student_count", "capacity"
        )
        private val SELECTED_COUNT_PATTERNS = listOf(
            "选课人数", "已选人数", "已选",
            "selected_count", "enrolled"
        )
        private val COURSE_HOURS_PATTERNS = listOf(
            "学时组成", "课程学时", "学时", "总学时", "课时",
            "course_hours", "hours"
        )

        private val FIELD_PATTERNS = mapOf(
            "courseName" to COURSE_NAME_PATTERNS,
            "teacher" to TEACHER_PATTERNS,
            "classroom" to CLASSROOM_PATTERNS,
            "dayOfWeek" to DAY_PATTERNS,
            "section" to SECTION_PATTERNS,
            "weeks" to WEEK_PATTERNS,
            "credit" to CREDIT_PATTERNS,
            "courseCode" to COURSE_CODE_PATTERNS,
            "className" to CLASS_NAME_PATTERNS,
            "courseNature" to COURSE_NATURE_PATTERNS,
            "studentCount" to STUDENT_COUNT_PATTERNS,
            "selectedCount" to SELECTED_COUNT_PATTERNS,
            "courseHours" to COURSE_HOURS_PATTERNS
        )
    }

    fun detectHeader(
        sheetData: List<List<String>>,
        template: EducationalSystemTemplate? = null
    ): HeaderDetectionResult {
        if (sheetData.isEmpty()) {
            return HeaderDetectionResult(
                headerRowIndex = -1,
                fieldMapping = emptyMap(),
                confidence = 0f,
                unmappedHeaders = emptyList()
            )
        }

        val bestResult = findBestHeaderRow(sheetData, template)
        if (bestResult.first < 0) {
            return HeaderDetectionResult(
                headerRowIndex = -1,
                fieldMapping = emptyMap(),
                confidence = 0f,
                unmappedHeaders = emptyList()
            )
        }

        val headerRowIndex = bestResult.first
        val mapping = bestResult.second
        val confidence = bestResult.third

        val headerRow = sheetData[headerRowIndex]
        val mappedIndices = mapping.values.toSet()
        val unmappedHeaders = headerRow.mapIndexedNotNull { index, value ->
            if (index !in mappedIndices && value.isNotBlank()) value else null
        }

        return HeaderDetectionResult(
            headerRowIndex = headerRowIndex,
            fieldMapping = mapping,
            confidence = confidence,
            detectedTemplate = template?.name,
            unmappedHeaders = unmappedHeaders
        )
    }

    fun buildFieldMapping(
        headerRow: List<String>,
        template: EducationalSystemTemplate? = null
    ): Pair<Map<String, Int>, Float> {
        val mapping = mutableMapOf<String, Int>()
        var matchScore = 0f
        var totalFields = 0

        val patterns = if (template != null) {
            template.headerPatterns.mapValues { (_, patterns) -> patterns }
        } else {
            FIELD_PATTERNS
        }

        val requiredFields = listOf("courseName")
        val optionalFields = listOf("teacher", "classroom", "dayOfWeek", "section", "weeks", "credit", "courseCode")

        for ((fieldName, fieldPatterns) in patterns) {
            totalFields++
            var bestMatchIdx = -1
            var bestMatchScore = 0f

            headerRow.forEachIndexed { idx, header ->
                val normalizedHeader = header.trim().lowercase()
                fieldPatterns.forEach { pattern ->
                    val normalizedPattern = pattern.lowercase()
                    val score = when {
                        normalizedHeader == normalizedPattern -> 1.0f
                        normalizedHeader.contains(normalizedPattern) -> 0.8f
                        normalizedPattern.contains(normalizedHeader) -> 0.6f
                        else -> 0f
                    }
                    if (score > bestMatchScore) {
                        bestMatchScore = score
                        bestMatchIdx = idx
                    }
                }
            }

            if (bestMatchIdx >= 0 && bestMatchScore > 0.5f) {
                if (!mapping.containsValue(bestMatchIdx)) {
                    mapping[fieldName] = bestMatchIdx
                    matchScore += bestMatchScore
                } else {
                    val existingKey = mapping.entries.first { it.value == bestMatchIdx }.key
                    if (bestMatchScore > 0.8f && fieldName in requiredFields) {
                        mapping.remove(existingKey)
                        mapping[fieldName] = bestMatchIdx
                        matchScore += bestMatchScore
                    }
                }
            }
        }

        val confidence = if (totalFields > 0) matchScore / totalFields else 0f
        return Pair(mapping, confidence)
    }

    private fun findBestHeaderRow(
        sheetData: List<List<String>>,
        template: EducationalSystemTemplate?
    ): Triple<Int, Map<String, Int>, Float> {
        var bestRow = -1
        var bestMapping = emptyMap<String, Int>()
        var bestConfidence = 0f

        val rowsToScan = minOf(sheetData.size, MAX_ROWS_TO_SCAN)

        for (rowIdx in 0 until rowsToScan) {
            val row = sheetData[rowIdx]
            if (row.isEmpty() || row.all { it.isBlank() }) continue

            val (mapping, confidence) = buildFieldMapping(row, template)

            if (mapping.containsKey("courseName") && confidence > bestConfidence) {
                bestRow = rowIdx
                bestMapping = mapping
                bestConfidence = confidence
            }
        }

        return Triple(bestRow, bestMapping, bestConfidence)
    }

    fun determineConfidence(confidence: Float): ImportConfidence {
        return when {
            confidence >= HIGH_CONFIDENCE_THRESHOLD -> ImportConfidence.HIGH
            confidence >= MEDIUM_CONFIDENCE_THRESHOLD -> ImportConfidence.MEDIUM
            else -> ImportConfidence.LOW
        }
    }

    fun analyzeSheet(
        sheetData: List<List<String>>,
        template: EducationalSystemTemplate? = null
    ): ImportRouteDecision {
        val headerResult = detectHeader(sheetData, template)
        val confidence = determineConfidence(headerResult.confidence)

        val dataStartRow = if (headerResult.headerRowIndex >= 0) {
            headerResult.headerRowIndex + 1
        } else {
            0
        }

        val needsConfirmation = confidence != ImportConfidence.HIGH

        val suggestedAction = when (confidence) {
            ImportConfidence.HIGH -> "自动解析"
            ImportConfidence.MEDIUM -> "请确认字段映射"
            ImportConfidence.LOW -> "请手动选择模板或调整映射"
        }

        return ImportRouteDecision(
            confidence = confidence,
            templateName = headerResult.detectedTemplate,
            fieldMapping = headerResult.fieldMapping,
            headerRowIndex = headerResult.headerRowIndex,
            dataStartRowIndex = dataStartRow,
            needsConfirmation = needsConfirmation,
            suggestedAction = suggestedAction
        )
    }

    fun guessTemplate(sheetData: List<List<String>>): EducationalSystemTemplate? {
        val allText = sheetData.flatten().joinToString(" ").lowercase()

        var bestTemplate: EducationalSystemTemplate? = null
        var bestScore = 0

        for (template in EducationalSystemTemplate.entries) {
            var score = 0
            for (keyword in template.keywords) {
                if (allText.contains(keyword.lowercase())) {
                    score += 2
                }
            }
            for ((_, patterns) in template.headerPatterns) {
                for (pattern in patterns) {
                    if (allText.contains(pattern.lowercase())) {
                        score += 1
                    }
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestTemplate = template
            }
        }

        AppLogger.d(TAG, "模板猜测结果: ${bestTemplate?.name ?: "未知"}, 得分: $bestScore")
        return bestTemplate
    }
}
