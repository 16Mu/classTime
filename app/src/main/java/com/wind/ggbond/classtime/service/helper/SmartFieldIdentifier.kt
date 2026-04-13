package com.wind.ggbond.classtime.service.helper

import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartFieldIdentifier @Inject constructor() {

    enum class FieldType {
        COURSE_NAME,
        SECTION,
        WEEKS,
        CLASSROOM,
        CLASS_NAME,
        COURSE_NATURE,
        STUDENT_COUNT,
        SELECTED_COUNT,
        COURSE_HOURS,
        TEACHER,
        CREDIT
    }

    data class FieldAssignment(
        val fieldType: FieldType,
        val value: String,
        val confidence: Float,
        val isAmbiguous: Boolean = false,
        val segmentIndex: Int = -1
    )

    data class IdentificationResult(
        val assignments: Map<FieldType, FieldAssignment>,
        val ambiguousFields: List<FieldType>,
        val overallConfidence: Float
    )

    companion object {
        private const val TAG = "SmartFieldIdentifier"
        private const val SCORE_THRESHOLD = 0.3f
        private const val AMBIGUITY_GAP = 0.2f
        private const val LOW_CONFIDENCE_THRESHOLD = 0.7f
        private const val AMBIGUITY_SECOND_BEST_THRESHOLD = 0.5f

        private val COURSE_NATURE_KEYWORDS = setOf(
            "必修", "选修", "限选", "任选", "考查", "考试",
            "公选", "专选", "专修", "核心", "通识"
        )

        private val COURSE_NATURE_COMPOUND_KEYWORDS = setOf(
            "学科基础", "专业基础", "公共基础", "基础必修",
            "专业选修", "公共选修", "专业必修", "公共必修"
        )

        private val CLASSROOM_KEYWORDS = setOf(
            "楼", "区", "室", "层", "栋", "馆", "堂", "厅",
            "教室", "机房", "实验室", "操场", "场地"
        )

        private val CLASSROOM_PATTERNS = listOf(
            Regex("""^[A-Za-z]?\d+[号栋楼区室]$"""),
            Regex("""^[东西南北]\d+$"""),
            Regex("""^教\d*[-–—]\d+$"""),
            Regex("""^[A-Za-z]\d{2,4}$"""),
            Regex("""^[\u4e00-\u9fa5]{1,3}\d{1,3}[-–—]\d{1,4}$""")
        )

        private val SECTION_PATTERN = Regex("""\d+\s*[-–—~～]\s*\d+\s*节""")
        private val SECTION_STANDALONE = Regex("""^\(?\d+\s*[-–—~～]\s*\d+\s*节\)?$""")
        private val WEEK_PATTERN = Regex("""\d+\s*[-–—]\s*\d+\s*周|(?<=^|[,，\s])\d+\s*周|周[（(][单双][）)]""")
        private val HOURS_PATTERN = Regex("""学时|理论[:：]\d|实践[:：]\d""")
        private val CLASS_PATTERN = Regex("""班""")
        private val PURE_NUMBER = Regex("""^\d+$""")
        private val CHINESE_CHAR = Regex("""[\u4e00-\u9fa5]""")
        private val HOURS_NUMERIC_RANGE = 8..256
    }

    fun identifyFields(segments: List<String>): IdentificationResult {
        if (segments.isEmpty()) {
            return IdentificationResult(emptyMap(), emptyList(), 0f)
        }

        val scoreMatrix = computeScoreMatrix(segments)
        val assignments = optimalAssignment(scoreMatrix, segments)
        val refinedAssignments = resolveNumericAmbiguity(assignments, segments)
        val ambiguousFields = findAmbiguousFields(refinedAssignments, scoreMatrix)
        val overallConfidence = computeOverallConfidence(refinedAssignments)

        AppLogger.d(TAG, "智能识别结果: ${refinedAssignments.map { "${it.key}=${it.value.value}(${it.value.confidence})" }}")
        AppLogger.d(TAG, "歧义字段: ${ambiguousFields.map { it.name }}")
        AppLogger.d(TAG, "整体置信度: $overallConfidence")

        return IdentificationResult(refinedAssignments, ambiguousFields, overallConfidence)
    }

    private fun computeScoreMatrix(segments: List<String>): Map<Int, Map<FieldType, Float>> {
        val matrix = mutableMapOf<Int, Map<FieldType, Float>>()

        for ((index, segment) in segments.withIndex()) {
            val trimmed = segment.trim()
            val scores = mutableMapOf<FieldType, Float>()

            scores[FieldType.COURSE_NAME] = scoreCourseName(trimmed)
            scores[FieldType.SECTION] = scoreSection(trimmed)
            scores[FieldType.WEEKS] = scoreWeeks(trimmed)
            scores[FieldType.CLASSROOM] = scoreClassroom(trimmed)
            scores[FieldType.CLASS_NAME] = scoreClassName(trimmed)
            scores[FieldType.COURSE_NATURE] = scoreCourseNature(trimmed)
            scores[FieldType.STUDENT_COUNT] = scoreStudentCount(trimmed)
            scores[FieldType.SELECTED_COUNT] = scoreSelectedCount(trimmed)
            scores[FieldType.COURSE_HOURS] = scoreCourseHours(trimmed)
            scores[FieldType.TEACHER] = scoreTeacher(trimmed)
            scores[FieldType.CREDIT] = scoreCredit(trimmed)

            matrix[index] = scores
        }

        return matrix
    }

    private fun scoreCourseName(s: String): Float {
        if (s.isBlank()) return 0f
        if (PURE_NUMBER.matches(s)) return 0f
        if (SECTION_STANDALONE.matches(s)) return 0f
        if (WEEK_PATTERN.find(s) != null && CHINESE_CHAR.find(s.replace(Regex("""\d+[-–—]\d+周"""), "")) == null) return 0.1f
        if (COURSE_NATURE_KEYWORDS.contains(s)) return 0f
        if (COURSE_NATURE_COMPOUND_KEYWORDS.contains(s)) return 0f
        if (CLASSROOM_KEYWORDS.any { s.contains(it) }) return 0.1f
        if (s.contains("班") && s.length <= 10) return 0.1f
        if (HOURS_PATTERN.find(s) != null) return 0f

        val hasChinese = CHINESE_CHAR.find(s) != null
        val length = s.length
        return when {
            hasChinese && length in 2..20 -> 0.9f
            hasChinese && length in 21..30 -> 0.7f
            hasChinese -> 0.5f
            length in 2..15 -> 0.4f
            else -> 0.2f
        }
    }

    private fun scoreSection(s: String): Float {
        if (s.isBlank()) return 0f
        val hasSectionPattern = SECTION_PATTERN.find(s) != null
        val isStandalone = SECTION_STANDALONE.matches(s)

        return when {
            isStandalone && hasSectionPattern -> 1.0f
            hasSectionPattern && s.length <= 15 -> 0.9f
            s.contains("节") && hasSectionPattern -> 0.85f
            s.contains("节") -> 0.6f
            else -> 0f
        }
    }

    private fun scoreWeeks(s: String): Float {
        if (s.isBlank()) return 0f
        val hasWeekPattern = WEEK_PATTERN.find(s) != null
        val hasSectionKeyword = s.contains("节")

        return when {
            hasWeekPattern && !hasSectionKeyword -> 0.95f
            hasWeekPattern && hasSectionKeyword -> 0.5f
            s.contains("周") && !hasSectionKeyword -> 0.8f
            s.contains("周") && hasSectionKeyword -> 0.4f
            else -> 0f
        }
    }

    private fun scoreClassroom(s: String): Float {
        if (s.isBlank()) return 0f
        if (PURE_NUMBER.matches(s)) return 0f
        if (COURSE_NATURE_KEYWORDS.contains(s)) return 0f
        if (COURSE_NATURE_COMPOUND_KEYWORDS.contains(s)) return 0f
        if (SECTION_STANDALONE.matches(s)) return 0f

        val hasKeyword = CLASSROOM_KEYWORDS.any { s.contains(it) }
        val matchesPattern = CLASSROOM_PATTERNS.any { it.matches(s) }

        return when {
            hasKeyword && matchesPattern -> 1.0f
            hasKeyword -> 0.9f
            matchesPattern -> 0.85f
            s.matches(Regex("""^[A-Za-z]+\d+$""")) && s.length <= 8 -> 0.7f
            else -> 0f
        }
    }

    private fun scoreClassName(s: String): Float {
        if (s.isBlank()) return 0f
        if (PURE_NUMBER.matches(s)) return 0f
        if (SECTION_STANDALONE.matches(s)) return 0f

        val containsBan = s.contains("班")
        val containsJi = s.endsWith("级") && s.length <= 10
        val hasChinese = CHINESE_CHAR.find(s) != null
        val hasNumber = s.any { it.isDigit() }

        return when {
            containsBan && hasNumber -> 0.95f
            containsBan -> 0.8f
            containsJi && hasNumber -> 0.75f
            hasChinese && hasNumber && s.length in 3..15 -> 0.4f
            else -> 0f
        }
    }

    private fun scoreCourseNature(s: String): Float {
        if (s.isBlank()) return 0f
        val trimmed = s.trim()

        return when {
            COURSE_NATURE_KEYWORDS.contains(trimmed) -> 1.0f
            COURSE_NATURE_COMPOUND_KEYWORDS.contains(trimmed) -> 1.0f
            COURSE_NATURE_KEYWORDS.any { trimmed == it } -> 1.0f
            COURSE_NATURE_COMPOUND_KEYWORDS.any { trimmed.contains(it) } && trimmed.length <= 8 -> 0.85f
            COURSE_NATURE_KEYWORDS.any { trimmed.contains(it) } && trimmed.length <= 4 -> 0.85f
            COURSE_NATURE_KEYWORDS.any { trimmed.contains(it) } && trimmed.length <= 6 -> 0.6f
            else -> 0f
        }
    }

    private fun scoreStudentCount(s: String): Float {
        if (s.isBlank()) return 0f
        if (!PURE_NUMBER.matches(s)) return 0f
        val num = s.toIntOrNull() ?: return 0f
        return when {
            num in 20..500 -> 0.5f
            num in 1..19 -> 0.1f
            num > 500 -> 0.05f
            else -> 0f
        }
    }

    private fun scoreSelectedCount(s: String): Float {
        return scoreStudentCount(s)
    }

    private fun scoreCourseHours(s: String): Float {
        if (s.isBlank()) return 0f
        val hasHoursKeyword = HOURS_PATTERN.find(s) != null
        val containsXueshi = s.contains("学时")

        return when {
            hasHoursKeyword && containsXueshi -> 1.0f
            hasHoursKeyword -> 0.9f
            containsXueshi -> 0.85f
            PURE_NUMBER.matches(s) -> {
                val num = s.toIntOrNull() ?: return 0f
                if (num in HOURS_NUMERIC_RANGE && num % 8 == 0) 0.3f
                else if (num in listOf(16, 24, 32, 48, 64, 96, 128)) 0.3f
                else 0.1f
            }
            else -> 0f
        }
    }

    private fun scoreTeacher(s: String): Float {
        if (s.isBlank()) return 0f
        val hasTeacherKeyword = s.contains("教师") || s.contains("老师")
        return when {
            hasTeacherKeyword -> 0.9f
            CHINESE_CHAR.find(s) != null && s.length in 2..4 && !s.contains("班") && !s.contains("级") -> 0.3f
            else -> 0f
        }
    }

    private fun scoreCredit(s: String): Float {
        if (s.isBlank()) return 0f
        val hasKeyword = s.contains("学分")
        if (hasKeyword) return 0.95f
        if (PURE_NUMBER.matches(s)) {
            val num = s.toFloatOrNull() ?: return 0f
            if (num in 0.5f..12f) return 0.2f
        }
        return 0f
    }

    private fun optimalAssignment(
        scoreMatrix: Map<Int, Map<FieldType, Float>>,
        segments: List<String>
    ): Map<FieldType, FieldAssignment> {
        val assignments = mutableMapOf<FieldType, FieldAssignment>()
        val usedSegments = mutableSetOf<Int>()

        val candidates = mutableListOf<Triple<Int, FieldType, Float>>()
        for ((segIdx, fieldScores) in scoreMatrix) {
            for ((fieldType, score) in fieldScores) {
                if (score > SCORE_THRESHOLD) {
                    candidates.add(Triple(segIdx, fieldType, score))
                }
            }
        }
        candidates.sortByDescending { it.third }

        for ((segIdx, fieldType, score) in candidates) {
            if (segIdx in usedSegments) continue
            if (assignments.containsKey(fieldType)) continue

            assignments[fieldType] = FieldAssignment(
                fieldType = fieldType,
                value = segments[segIdx].trim(),
                confidence = score,
                isAmbiguous = false,
                segmentIndex = segIdx
            )
            usedSegments.add(segIdx)
        }

        return assignments
    }

    private fun resolveNumericAmbiguity(
        assignments: Map<FieldType, FieldAssignment>,
        segments: List<String>
    ): Map<FieldType, FieldAssignment> {
        val result = assignments.toMutableMap()

        val assignedSegmentValues = result.values.map { it.value }.toSet()
        val unassignedNumericSegments = segments
            .mapIndexed { idx, s -> Pair(idx, s.trim()) }
            .filter { (_, s) -> PURE_NUMBER.matches(s) && s !in assignedSegmentValues }

        val unassignedNumericTypes = listOf(
            FieldType.STUDENT_COUNT,
            FieldType.SELECTED_COUNT,
            FieldType.COURSE_HOURS
        ).filter { it !in result }

        if (unassignedNumericSegments.isEmpty() || unassignedNumericTypes.isEmpty()) {
            return result
        }

        val numericValues = unassignedNumericSegments.mapNotNull { (idx, s) ->
            s.toIntOrNull()?.let { Triple(idx, s, it) }
        }.sortedByDescending { it.third }

        val availableTypes = unassignedNumericTypes.toMutableList()

        if (numericValues.size >= availableTypes.size && availableTypes.size >= 3) {
            val sorted = numericValues.sortedByDescending { it.third }
            val largest = sorted[0]
            if (largest.third in HOURS_NUMERIC_RANGE && largest.third % 8 == 0) {
                result[FieldType.COURSE_HOURS] = FieldAssignment(
                    FieldType.COURSE_HOURS, largest.second, 0.6f, true, largest.first
                )
                availableTypes.remove(FieldType.COURSE_HOURS)
                assignRemainingNumeric(sorted.drop(1), availableTypes, result)
            } else {
                assignRemainingNumeric(sorted, availableTypes, result)
            }
        } else if (numericValues.size >= 2 && availableTypes.size >= 2) {
            val sorted = numericValues.sortedByDescending { it.third }
            val bigger = sorted[0]
            val smaller = sorted[1]

            if (FieldType.COURSE_HOURS !in result && bigger.third in HOURS_NUMERIC_RANGE && bigger.third % 8 == 0 && smaller.third < 100) {
                result[FieldType.COURSE_HOURS] = FieldAssignment(
                    FieldType.COURSE_HOURS, bigger.second, 0.6f, true, bigger.first
                )
                availableTypes.remove(FieldType.COURSE_HOURS)
                result[FieldType.STUDENT_COUNT] = FieldAssignment(
                    FieldType.STUDENT_COUNT, smaller.second, 0.55f, true, smaller.first
                )
                availableTypes.remove(FieldType.STUDENT_COUNT)
            } else {
                assignRemainingNumeric(sorted, availableTypes, result)
            }
        } else if (numericValues.isNotEmpty() && availableTypes.isNotEmpty()) {
            val value = numericValues.first()
            val targetType = availableTypes.first()
            result[targetType] = FieldAssignment(
                targetType, value.second, 0.5f, true, value.first
            )
        }

        val studentCount = result[FieldType.STUDENT_COUNT]
        val selectedCount = result[FieldType.SELECTED_COUNT]
        if (studentCount != null && selectedCount != null) {
            val sNum = studentCount.value.toIntOrNull() ?: 0
            val eNum = selectedCount.value.toIntOrNull() ?: 0
            if (sNum < eNum) {
                result[FieldType.STUDENT_COUNT] = selectedCount.copy(
                    fieldType = FieldType.STUDENT_COUNT, isAmbiguous = true
                )
                result[FieldType.SELECTED_COUNT] = studentCount.copy(
                    fieldType = FieldType.SELECTED_COUNT, isAmbiguous = true
                )
            }
        }

        return result
    }

    private fun assignRemainingNumeric(
        sortedValues: List<Triple<Int, String, Int>>,
        availableTypes: MutableList<FieldType>,
        result: MutableMap<FieldType, FieldAssignment>
    ) {
        val typePriority = listOf(FieldType.STUDENT_COUNT, FieldType.SELECTED_COUNT, FieldType.COURSE_HOURS)
        for (type in typePriority) {
            if (type !in availableTypes) continue
            if (sortedValues.isEmpty()) break
            val value = sortedValues.first()
            result[type] = FieldAssignment(type, value.second, 0.55f, true, value.first)
            availableTypes.remove(type)
        }
    }

    private fun findAmbiguousFields(
        assignments: Map<FieldType, FieldAssignment>,
        scoreMatrix: Map<Int, Map<FieldType, Float>>
    ): List<FieldType> {
        val ambiguous = mutableListOf<FieldType>()

        for ((fieldType, assignment) in assignments) {
            if (assignment.isAmbiguous) {
                ambiguous.add(fieldType)
                continue
            }
            if (assignment.confidence < LOW_CONFIDENCE_THRESHOLD) {
                ambiguous.add(fieldType)
                continue
            }
            val segIdx = assignment.segmentIndex
            if (segIdx >= 0) {
                val scores = scoreMatrix[segIdx] ?: continue
                val secondBest = scores.entries
                    .filter { it.key != fieldType && it.value > SCORE_THRESHOLD }
                    .maxOfOrNull { it.value } ?: 0f
                if (secondBest > assignment.confidence - AMBIGUITY_GAP && secondBest > AMBIGUITY_SECOND_BEST_THRESHOLD) {
                    ambiguous.add(fieldType)
                }
            }
        }

        return ambiguous
    }

    private fun computeOverallConfidence(assignments: Map<FieldType, FieldAssignment>): Float {
        if (assignments.isEmpty()) return 0f
        return assignments.values.map { it.confidence }.average().toFloat()
    }
}
